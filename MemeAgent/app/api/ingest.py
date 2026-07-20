from fastapi import APIRouter, status

from app.api.deps import InternalAuth
from app.ingest.worker import enqueue_ingest
from app.retrieval.repository import delete_by_meme_ids
from app.schemas.ingest import (
    IngestAcceptedResponse,
    IngestDeleteRequest,
    IngestDeleteResponse,
    IngestRequest,
)

router = APIRouter(prefix="/ingest", tags=["ingest"])


@router.post("", status_code=status.HTTP_202_ACCEPTED, response_model=IngestAcceptedResponse)
async def ingest_documents(
    body: IngestRequest,
    _: InternalAuth,
) -> IngestAcceptedResponse:
    task_count, skipped = await enqueue_ingest(body)
    return IngestAcceptedResponse(accepted=True, task_count=task_count, skipped=skipped)


@router.post("/delete", response_model=IngestDeleteResponse)
async def ingest_delete(
    body: IngestDeleteRequest,
    _: InternalAuth,
) -> IngestDeleteResponse:
    deleted = await delete_by_meme_ids(body.meme_ids)
    return IngestDeleteResponse(deleted_count=deleted)
