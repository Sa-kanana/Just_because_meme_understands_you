import logging
import time

from fastapi import APIRouter
from fastapi.responses import JSONResponse

from app.api.deps import WriteAuth
from app.ingest.knowledge_worker import process_knowledge_ingest
from app.retrieval.knowledge_repository import (
    KnowledgeEmbeddingError,
    delete_knowledge_by_ids,
)
from app.schemas.knowledge import (
    KnowledgeDeleteRequest,
    KnowledgeDeleteResponse,
    KnowledgeIngestAcceptedResponse,
    KnowledgeIngestRequest,
)

router = APIRouter(prefix="/ingest/knowledge", tags=["knowledge-ingest"])
logger = logging.getLogger(__name__)


@router.post("", response_model=KnowledgeIngestAcceptedResponse)
async def ingest_knowledge(
    payload: KnowledgeIngestRequest,
    _auth: WriteAuth,
) -> KnowledgeIngestAcceptedResponse:
    started_at = time.perf_counter()
    try:
        task_count, skipped = await process_knowledge_ingest(payload)
    except KnowledgeEmbeddingError as exc:
        logger.exception(
            "Knowledge embedding ingest failed stage=%s completed_chunks=%s total_chunks=%s elapsed_ms=%s",
            exc.stage,
            exc.completed_chunks,
            exc.total_chunks,
            int((time.perf_counter() - started_at) * 1000),
        )
        return JSONResponse(
            status_code=502,
            content={
                "detail": {
                    "code": "KNOWLEDGE_EMBEDDING_FAILED",
                    "completed_chunks": exc.completed_chunks,
                    "total_chunks": exc.total_chunks,
                    "stage": exc.stage,
                    "message": exc.safe_message,
                }
            },
        )
    logger.info(
        "Knowledge ingest request completed documents=%s skipped=%s elapsed_ms=%s",
        task_count,
        skipped,
        int((time.perf_counter() - started_at) * 1000),
    )
    return KnowledgeIngestAcceptedResponse(accepted=True, task_count=task_count, skipped=skipped)


@router.post("/delete", response_model=KnowledgeDeleteResponse)
async def ingest_knowledge_delete(
    payload: KnowledgeDeleteRequest,
    _auth: WriteAuth,
) -> KnowledgeDeleteResponse:
    deleted = await delete_knowledge_by_ids(payload.doc_ids, payload.revision)
    return KnowledgeDeleteResponse(deleted_count=deleted)
