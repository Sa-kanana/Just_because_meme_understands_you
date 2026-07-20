from dataclasses import dataclass
from datetime import datetime, timezone
import logging

from app.retrieval.db import get_pool, vector_literal
from app.retrieval.embeddings import embed_text
from app.schemas.ingest import IngestDocument

logger = logging.getLogger(__name__)


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

    pool = get_pool()
    async with pool.acquire() as conn:
        async with conn.transaction():
            await conn.execute("DELETE FROM meme_vector_chunk WHERE meme_id = $1", doc.meme_id)
            for idx, chunk in enumerate(chunks):
                embedding = await embed_text(chunk)
                await conn.execute(
                    """
                    INSERT INTO meme_vector_chunk
                        (meme_id, chunk_index, content, content_hash, embedding, updated_at)
                    VALUES ($1, $2, $3, $4, $5::vector, $6)
                    """,
                    doc.meme_id,
                    idx,
                    chunk,
                    doc.content_hash,
                    vector_literal(embedding),
                    doc.updated_at.astimezone(timezone.utc),
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
    pool = get_pool()
    async with pool.acquire() as conn:
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
            top_k,
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
