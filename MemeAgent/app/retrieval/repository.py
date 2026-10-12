from dataclasses import dataclass
from datetime import datetime, timezone
import logging

from app.core.limits import (
    MAX_CHUNK_CHARS,
    MAX_CHUNKS_PER_MEME,
    MAX_MEME_ID_PAGE_SIZE,
)
from app.core.settings import get_settings
from app.retrieval.db import get_pool, vector_literal
from app.retrieval.embeddings import embed_text, embed_texts
from app.schemas.ingest import IngestDocument

logger = logging.getLogger(__name__)


@dataclass
class RetrievedChunk:
    meme_id: str
    content: str
    score: float
    title: str | None = None


async def count_same_content_hash_chunks(meme_id: str, content_hash: str) -> int:
    pool = get_pool()
    async with pool.acquire() as conn:
        count = await conn.fetchval(
            """
            SELECT COUNT(*) FROM meme_vector_chunk
            WHERE meme_id = $1 AND content_hash = $2
            """,
            meme_id,
            content_hash,
        )
        return int(count or 0)


async def upsert_document(doc: IngestDocument, chunks: list[str]) -> None:
    if doc.status == "deleted":
        await delete_by_meme_ids([doc.meme_id])
        return

    if len(chunks) > MAX_CHUNKS_PER_MEME or any(
        len(chunk) > MAX_CHUNK_CHARS for chunk in chunks
    ):
        raise ValueError(
            f"梗 {doc.meme_id} 的分块数或单块长度超过入库上限"
        )

    existing_chunk_count = await count_same_content_hash_chunks(
        doc.meme_id, doc.content_hash
    )
    if 0 < existing_chunk_count <= MAX_CHUNKS_PER_MEME:
        logger.info(
            "Skip ingest meme_id=%s unchanged_hash_chunks=%s",
            doc.meme_id,
            existing_chunk_count,
        )
        return
    if existing_chunk_count > MAX_CHUNKS_PER_MEME:
        logger.warning(
            "Repair legacy meme vectors meme_id=%s chunks=%s max_chunks=%s",
            doc.meme_id,
            existing_chunk_count,
            MAX_CHUNKS_PER_MEME,
        )

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
        deleted_count = int(result.split()[-1])
    except (ValueError, IndexError):
        deleted_count = 0
    return deleted_count


async def list_meme_ids_page(
    cursor: str | None, limit: int
) -> tuple[list[str], str | None]:
    if not 1 <= limit <= MAX_MEME_ID_PAGE_SIZE:
        raise ValueError(
            f"梗 ID 分页大小必须在 1 到 {MAX_MEME_ID_PAGE_SIZE} 之间"
        )

    pool = get_pool()
    async with pool.acquire() as conn:
        if cursor is None:
            rows = await conn.fetch(
                """
                SELECT DISTINCT meme_id
                FROM meme_vector_chunk
                ORDER BY meme_id ASC
                LIMIT $1
                """,
                limit + 1,
            )
        else:
            rows = await conn.fetch(
                """
                SELECT DISTINCT meme_id
                FROM meme_vector_chunk
                WHERE meme_id > $1
                ORDER BY meme_id ASC
                LIMIT $2
                """,
                cursor,
                limit + 1,
            )

    meme_ids = [str(row["meme_id"]) for row in rows]
    if len(meme_ids) > limit:
        return meme_ids[:limit], meme_ids[limit - 1]
    return meme_ids, None


async def similarity_search(query: str, candidate_limit: int) -> list[RetrievedChunk]:
    """返回指定上限的原始向量 chunk，不执行业务去重或相似度过滤。"""
    if candidate_limit < 1:
        raise ValueError("梗库检索候选上限必须大于 0")
    query_embedding = await embed_text(query)
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


def _extract_title(content: str) -> str | None:
    for line in content.splitlines():
        if line.startswith("标题:"):
            return line.replace("标题:", "", 1).strip()
    return None
