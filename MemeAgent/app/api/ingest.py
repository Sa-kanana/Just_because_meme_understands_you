from fastapi import APIRouter, Query

from app.api.deps import WriteAuth
from app.core.limits import MAX_MEME_ID_PAGE_SIZE
from app.ingest.worker import enqueue_ingest
from app.retrieval.repository import delete_by_meme_ids, list_meme_ids_page
from app.schemas.ingest import (
    IngestAcceptedResponse,
    IngestDeleteRequest,
    IngestDeleteResponse,
    IngestRequest,
    MemeIdPageResponse,
)

router = APIRouter(prefix="/ingest", tags=["ingest"])


@router.post("", response_model=IngestAcceptedResponse)
async def ingest_documents(
    payload: IngestRequest,
    _auth: WriteAuth,
) -> IngestAcceptedResponse:
    task_count, skipped = await enqueue_ingest(payload)
    return IngestAcceptedResponse(accepted=True, task_count=task_count, skipped=skipped)


@router.post("/delete", response_model=IngestDeleteResponse)
async def ingest_delete(
    payload: IngestDeleteRequest,
    _auth: WriteAuth,
) -> IngestDeleteResponse:
    deleted = await delete_by_meme_ids(payload.meme_ids)
    return IngestDeleteResponse(deleted_count=deleted)


@router.get("/meme-ids", response_model=MemeIdPageResponse)
async def ingest_meme_ids_page(
    _auth: WriteAuth,
    cursor: str | None = Query(default=None, description="上一页末尾的梗 ID 游标"),
    limit: int = Query(
        default=MAX_MEME_ID_PAGE_SIZE,
        ge=1,
        le=MAX_MEME_ID_PAGE_SIZE,
        description="本页最多返回的梗 ID 数量",
    ),
) -> MemeIdPageResponse:
    meme_ids, next_cursor = await list_meme_ids_page(cursor, limit)
    return MemeIdPageResponse(meme_ids=meme_ids, next_cursor=next_cursor)
