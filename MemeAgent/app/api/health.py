from fastapi import APIRouter

from app.api.deps import StreamAuth
from app.core.settings import get_settings
from app.retrieval.db import ping_db
from app.schemas.common import HealthDetailResponse, HealthResponse

router = APIRouter(tags=["health"])


@router.get("/health", response_model=HealthResponse)
async def health() -> HealthResponse:
    """公开探活：不暴露模型名/密钥相关配置。"""
    settings = get_settings()
    vector_ok = bool(settings.vector_database_url) and await ping_db()
    return HealthResponse(
        status="ok" if vector_ok or not settings.vector_database_url else "degraded",
        service=settings.app_name,
        env=settings.app_env,
        vector_db=vector_ok,
    )


@router.get("/health/detail", response_model=HealthDetailResponse)
async def health_detail(_auth: StreamAuth) -> HealthDetailResponse:
    """内网运维详情：需 X-Internal-Api-Key。"""
    settings = get_settings()
    vector_ok = bool(settings.vector_database_url) and await ping_db()
    return HealthDetailResponse(
        status="ok" if vector_ok or not settings.vector_database_url else "degraded",
        service=settings.app_name,
        env=settings.app_env,
        vector_db=vector_ok,
        chat_model=settings.openai_chat_model,
        embedding_model=settings.openai_embedding_model,
        openai_base_host=(settings.openai_base_url or "").split("/")[2]
        if settings.openai_base_url and "://" in settings.openai_base_url
        else None,
        write_key_configured=bool((settings.internal_api_key_write or "").strip()),
    )
