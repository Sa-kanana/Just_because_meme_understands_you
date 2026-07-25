from collections.abc import AsyncIterator

from fastapi import APIRouter
from fastapi.responses import StreamingResponse

from app.agents.search_agent import stream_ai_search
from app.api.deps import InternalAuth
from app.schemas.stream import StreamRequest

router = APIRouter(tags=["stream"])


@router.post("/stream")
async def stream_search(
    payload: StreamRequest,
    _auth: InternalAuth,
) -> StreamingResponse:
    async def event_generator() -> AsyncIterator[str]:
        async for chunk in stream_ai_search(payload):
            yield chunk

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )
