from dataclasses import dataclass
from datetime import datetime, timezone
import logging

from app.core.settings import get_settings
from app.retrieval.db import get_pool, vector_literal
from app.retrieval.embeddings import embed_text, embed_texts
from app.schemas.ingest import IngestDocument

logger = logging.getLogger(__name__)
MAX_CHUNKS_PER_MEME = 6


@dataclass
class RetrievedChunk:
    meme_id: str
    content: str
    score: float
    title: str | None = None


async def has_same_content_hash(meme_id: str, content_hash: str) -> bool:
    pool = get_pool()
    async with pool.acquire() as conn:
        row = await conn.fetchrow(
            """
            SELECT 1 FROM meme_vector_chunk
            WHERE meme_id = $1 AND content_hash = $2
            LIMIT 1
            """,
            meme_id,
            content_hash,
        )
        return row is not None


async def upsert_document(doc: IngestDocument, chunks: list[str]) -> None:
    if doc.status == "deleted":
        await delete_by_meme_ids([doc.meme_id])
        return

    if await has_same_content_hash(doc.meme_id, doc.content_hash):
        logger.info("Skip ingest meme_id=%s unchanged hash=%s", doc.meme_id, doc.content_hash)
        return

    # Keep external embedding calls outside the DB connection and transaction.
    embeddings = await embed_texts(chunks)
    updated_at = doc.updated_at.astimezone(timezone.utc)
    pool = get_pool()
    async with pool.acquire() as conn:
        async with conn.transaction():
            await conn.execute("DELETE FROM meme_vector_chunk WHERE meme_id = $1", doc.meme_id)
            await conn.executemany(
                """
                INSERT INTO meme_vector_chunk
                    (meme_id, chunk_index, content, content_hash, embedding, updated_at)
                VALUES ($1, $2, $3, $4, $5::vector, $6)
                """,
                [
                    (
                        doc.meme_id,
                        idx,
                        chunk,
                        doc.content_hash,
                        vector_literal(embeddings[idx]),
                        updated_at,
                    )
                    for idx, chunk in enumerate(chunks)
                ],
            )


async def delete_by_meme_ids(meme_ids: list[str]) -> int:
    if not meme_ids:
        return 0
    pool = get_pool()
    async with pool.acquire() as conn:
        result = await conn.execute(
            "DELETE FROM meme_vector_chunk WHERE meme_id = ANY($1::varchar[])",
            meme_ids,
        )
    # asyncpg returns 'DELETE N'
    try:
        return int(result.split()[-1])
    except (ValueError, IndexError):
        return 0


async def similarity_search(query: str, top_k: int) -> list[RetrievedChunk]:
    query_embedding = await embed_text(query)
    # Meme introductions are capped at 5,000 chars and chunked into at most six
    # chunks. Overfetch before merge_retrieved deduplicates by meme_id.
    candidate_limit = max(1, top_k) * MAX_CHUNKS_PER_MEME
    pool = get_pool()
    acquire_timeout = get_settings().vector_pool_acquire_timeout_seconds
    async with pool.acquire(timeout=acquire_timeout) as conn:
        rows = await conn.fetch(
            """
            SELECT meme_id, content,
                   1 - (embedding <=> $1::vector) AS score
            FROM meme_vector_chunk
            WHERE embedding IS NOT NULL
            ORDER BY embedding <=> $1::vector
            LIMIT $2
            """,
            vector_literal(query_embedding),
            candidate_limit,
        )

    results: list[RetrievedChunk] = []
    for row in rows:
        title = _extract_title(row["content"])
        results.append(
            RetrievedChunk(
                meme_id=row["meme_id"],
                content=row["content"],
                score=float(row["score"] or 0),
                title=title,
            )
        )
    return results


async def fetch_chunks_by_meme_ids(meme_ids: list[str], per_meme: int = 1) -> list[RetrievedChunk]:
    """按业务 meme_id 拉取向量片段（供 hint 融合）。"""
    ids = [str(m).strip() for m in meme_ids if str(m).strip()]
    if not ids:
        return []
    pool = get_pool()
    async with pool.acquire() as conn:
        rows = await conn.fetch(
            """
            SELECT meme_id, content, chunk_index
            FROM (
                SELECT meme_id, content, chunk_index,
                       ROW_NUMBER() OVER (PARTITION BY meme_id ORDER BY chunk_index ASC) AS row_num
                FROM meme_vector_chunk
                WHERE meme_id = ANY($1::varchar[])
            ) ranked
            WHERE row_num <= $2
            ORDER BY array_position($1::varchar[], meme_id), chunk_index ASC
            """,
            ids,
            max(1, per_meme),
        )
    # 保留调用方传入顺序及每个 meme 内的块顺序。
    results: list[RetrievedChunk] = []
    for row in rows:
        results.append(
            RetrievedChunk(
                meme_id=row["meme_id"],
                content=row["content"],
                score=0.55,
                title=_extract_title(row["content"]),
            )
        )
    return results


def merge_retrieved(
    vector_hits: list[RetrievedChunk],
    hint_hits: list[RetrievedChunk],
    mysql_snippets: list[RetrievedChunk],
    top_k: int,
    rrf_k: int = 60,
) -> list[RetrievedChunk]:
    """RRF 融合语义结果与 Java 业务候选；hint 和 snippet 合并为一个来源。"""
    merged: dict[str, RetrievedChunk] = {}
    fused_scores: dict[str, float] = {}
    rank_constant = max(1, rrf_k)

    business_candidates: dict[str, RetrievedChunk] = {}
    for chunk in (*hint_hits, *mysql_snippets):
        business_candidates.setdefault(chunk.meme_id, chunk)

    for source in (vector_hits, list(business_candidates.values())):
        seen_in_source: set[str] = set()
        rank = 0
        for chunk in source:
            if chunk.meme_id in seen_in_source:
                continue
            seen_in_source.add(chunk.meme_id)
            rank += 1
            fused_scores[chunk.meme_id] = fused_scores.get(chunk.meme_id, 0.0) + (
                1.0 / (rank_constant + rank)
            )

            # Keep the best original result payload and its source score for citations.
            existing = merged.get(chunk.meme_id)
            if existing is None or chunk.score > existing.score:
                merged[chunk.meme_id] = chunk

    ordered = sorted(merged.values(), key=lambda c: fused_scores[c.meme_id], reverse=True)
    return ordered[: max(1, top_k)]


def _extract_title(content: str) -> str | None:
    for line in content.splitlines():
        if line.startswith("标题:"):
            return line.replace("标题:", "", 1).strip()
    return None
