from dataclasses import dataclass
from datetime import timezone
import logging

from app.core.settings import get_settings
from app.retrieval.db import get_pool, vector_literal
from app.retrieval.embeddings import embed_text, embed_texts
from app.schemas.knowledge import KnowledgeDocument

logger = logging.getLogger(__name__)


@dataclass
class RetrievedKnowledgeChunk:
    doc_id: str
    content: str
    score: float
    title: str | None = None


async def has_same_knowledge_hash(doc_id: str, content_hash: str) -> bool:
    pool = get_pool()
    async with pool.acquire() as conn:
        row = await conn.fetchrow(
            """
            SELECT 1 FROM knowledge_vector_chunk
            WHERE doc_id = $1 AND content_hash = $2
            LIMIT 1
            """,
            doc_id,
            content_hash,
        )
        return row is not None


async def upsert_knowledge_document(doc: KnowledgeDocument, chunks: list[str]) -> None:
    if doc.status == "deleted":
        await delete_knowledge_by_ids([doc.doc_id])
        return

    if await has_same_knowledge_hash(doc.doc_id, doc.content_hash):
        logger.info("Skip knowledge ingest doc_id=%s unchanged hash=%s", doc.doc_id, doc.content_hash)
        return

    # Keep external embedding calls outside the DB connection and transaction.
    embeddings = await embed_texts(chunks)
    updated_at = doc.updated_at.astimezone(timezone.utc)
    title = doc.title.strip()
    category = (doc.category or "").strip() or None
    pool = get_pool()
    async with pool.acquire() as conn:
        async with conn.transaction():
            await conn.execute("DELETE FROM knowledge_vector_chunk WHERE doc_id = $1", doc.doc_id)
            await conn.executemany(
                """
                INSERT INTO knowledge_vector_chunk
                    (doc_id, chunk_index, content, content_hash, title, category, embedding, updated_at)
                VALUES ($1, $2, $3, $4, $5, $6, $7::vector, $8)
                """,
                [
                    (
                        doc.doc_id,
                        idx,
                        chunk,
                        doc.content_hash,
                        title,
                        category,
                        vector_literal(embeddings[idx]),
                        updated_at,
                    )
                    for idx, chunk in enumerate(chunks)
                ],
            )


async def delete_knowledge_by_ids(doc_ids: list[str]) -> int:
    if not doc_ids:
        return 0
    pool = get_pool()
    async with pool.acquire() as conn:
        result = await conn.execute(
            "DELETE FROM knowledge_vector_chunk WHERE doc_id = ANY($1::varchar[])",
            doc_ids,
        )
    try:
        return int(result.split()[-1])
    except (ValueError, IndexError):
        return 0


async def similarity_search_knowledge(query: str, top_k: int) -> list[RetrievedKnowledgeChunk]:
    query_embedding = await embed_text(query)
    pool = get_pool()
    acquire_timeout = get_settings().vector_pool_acquire_timeout_seconds
    async with pool.acquire(timeout=acquire_timeout) as conn:
        rows = await conn.fetch(
            """
            SELECT doc_id, content, title,
                   1 - (embedding <=> $1::vector) AS score
            FROM knowledge_vector_chunk
            WHERE embedding IS NOT NULL
            ORDER BY embedding <=> $1::vector
            LIMIT $2
            """,
            vector_literal(query_embedding),
            top_k,
        )

    results: list[RetrievedKnowledgeChunk] = []
    for row in rows:
        results.append(
            RetrievedKnowledgeChunk(
                doc_id=row["doc_id"],
                content=row["content"],
                score=float(row["score"] or 0),
                title=row["title"],
            )
        )
    return results
