import asyncio
import logging

from app.ingest.knowledge_chunker import build_knowledge_chunks
from app.retrieval.knowledge_repository import delete_knowledge_by_ids, upsert_knowledge_document
from app.schemas.knowledge import KnowledgeDocument, KnowledgeIngestRequest

logger = logging.getLogger(__name__)


async def enqueue_knowledge_ingest(request: KnowledgeIngestRequest) -> tuple[int, int]:
    task_count = 0
    skipped = 0
    for doc in request.documents:
        if doc.status == "deleted":
            asyncio.create_task(_safe_delete(doc.doc_id))
            task_count += 1
            continue
        asyncio.create_task(_safe_upsert(doc))
        task_count += 1
    return task_count, skipped


async def _safe_upsert(doc: KnowledgeDocument) -> None:
    try:
        chunks = build_knowledge_chunks(doc)
        await upsert_knowledge_document(doc, chunks)
        logger.info("Ingested knowledge doc_id=%s chunks=%s", doc.doc_id, len(chunks))
    except Exception:
        logger.exception("Knowledge ingest failed doc_id=%s", doc.doc_id)


async def _safe_delete(doc_id: str) -> None:
    try:
        count = await delete_knowledge_by_ids([doc_id])
        logger.info("Deleted knowledge vectors doc_id=%s count=%s", doc_id, count)
    except Exception:
        logger.exception("Delete knowledge vectors failed doc_id=%s", doc_id)
