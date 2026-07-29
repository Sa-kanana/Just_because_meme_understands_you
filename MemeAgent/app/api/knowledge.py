from fastapi import APIRouter, status

from app.api.deps import WriteAuth
from app.ingest.knowledge_worker import enqueue_knowledge_ingest
from app.retrieval.knowledge_repository import delete_knowledge_by_ids
from app.schemas.knowledge import (
    KnowledgeDeleteRequest,
    KnowledgeDeleteResponse,
    KnowledgeIngestAcceptedResponse,
    KnowledgeIngestRequest,
)

router = APIRouter(prefix="/ingest/knowledge", tags=["knowledge-ingest"])


@router.post("", status_code=status.HTTP_202_ACCEPTED, response_model=KnowledgeIngestAcceptedResponse)
async def ingest_knowledge(
    payload: KnowledgeIngestRequest,
    _auth: WriteAuth,
) -> KnowledgeIngestAcceptedResponse:
    task_count, skipped = await enqueue_knowledge_ingest(payload)
    return KnowledgeIngestAcceptedResponse(accepted=True, task_count=task_count, skipped=skipped)


@router.post("/delete", response_model=KnowledgeDeleteResponse)
async def ingest_knowledge_delete(
    payload: KnowledgeDeleteRequest,
    _auth: WriteAuth,
) -> KnowledgeDeleteResponse:
    deleted = await delete_knowledge_by_ids(payload.doc_ids)
    return KnowledgeDeleteResponse(deleted_count=deleted)
