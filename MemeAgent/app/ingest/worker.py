import asyncio
import logging

from app.ingest.chunker import build_chunks
from app.retrieval.repository import delete_by_meme_ids, upsert_document
from app.schemas.ingest import IngestDocument, IngestRequest

logger = logging.getLogger(__name__)


async def enqueue_ingest(request: IngestRequest) -> tuple[int, int]:
    task_count = 0
    skipped = 0
    for doc in request.documents:
        if doc.status == "deleted":
            asyncio.create_task(_safe_delete(doc.meme_id))
            task_count += 1
            continue
        asyncio.create_task(_safe_upsert(doc))
        task_count += 1
    return task_count, skipped


async def _safe_upsert(doc: IngestDocument) -> None:
    try:
        chunks = build_chunks(doc)
        await upsert_document(doc, chunks)
        logger.info("Ingested meme_id=%s chunks=%s", doc.meme_id, len(chunks))
    except Exception:
        logger.exception("Ingest failed meme_id=%s", doc.meme_id)


async def _safe_delete(meme_id: str) -> None:
    try:
        count = await delete_by_meme_ids([meme_id])
        logger.info("Deleted vectors meme_id=%s count=%s", meme_id, count)
    except Exception:
        logger.exception("Delete vectors failed meme_id=%s", meme_id)
