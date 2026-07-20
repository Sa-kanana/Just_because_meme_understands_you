from fastapi import APIRouter

from app.core.settings import get_settings
from app.retrieval.db import ping_db
from app.schemas.common import HealthResponse

router = APIRouter(tags=["health"])


@router.get("/health", response_model=HealthResponse)
async def health() -> HealthResponse:
    settings = get_settings()
    vector_ok = bool(settings.vector_database_url) and await ping_db()
    return HealthResponse(
        status="ok" if vector_ok or not settings.vector_database_url else "degraded",
        service=settings.app_name,
        env=settings.app_env,
        vector_db=vector_ok,
    )
