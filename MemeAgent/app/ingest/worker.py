import asyncio
import logging

from app.ingest.chunker import build_chunks
from app.retrieval.repository import delete_by_meme_ids, upsert_document
from app.schemas.ingest import IngestDocument, IngestRequest

logger = logging.getLogger(__name__)


async def enqueue_ingest(request: IngestRequest) -> tuple[int, int]:
    """在请求生命周期内并发处理向量任务，完成后再确认 Java 发件箱事件。"""
    async with asyncio.TaskGroup() as task_group:
        for index, doc in enumerate(request.documents):
            if doc.status == "deleted":
                task_group.create_task(
                    _safe_delete(doc.meme_id), name=f"meme-delete-{index}"
                )
            else:
                task_group.create_task(
                    _safe_upsert(doc), name=f"meme-upsert-{index}"
                )
    return len(request.documents), 0


async def _safe_upsert(doc: IngestDocument) -> None:
    try:
        chunks = build_chunks(doc)
        await upsert_document(doc, chunks)
        logger.info("Ingested meme_id=%s chunks=%s", doc.meme_id, len(chunks))
    except asyncio.CancelledError:
        logger.warning("Ingest cancelled meme_id=%s", doc.meme_id)
        raise
    except Exception:
        logger.exception("Ingest failed meme_id=%s", doc.meme_id)
        raise


async def _safe_delete(meme_id: str) -> None:
    try:
        count = await delete_by_meme_ids([meme_id])
        logger.info("Deleted vectors meme_id=%s count=%s", meme_id, count)
    except asyncio.CancelledError:
        logger.warning("Vector delete cancelled meme_id=%s", meme_id)
        raise
    except Exception:
        logger.exception("Delete vectors failed meme_id=%s", meme_id)
        raise
