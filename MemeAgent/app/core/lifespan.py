import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.core.auth import configure_logging
from app.core.settings import get_settings
from app.retrieval.db import close_pool, create_pool, ensure_schema

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    # reload / 新 worker 启动时强制重读 .env，避免沿用旧模型配置
    get_settings.cache_clear()
    settings = get_settings()
    configure_logging(settings.log_level)
    logger.info(
        "Starting %s (env=%s) chat_model=%s embed_model=%s base_url=%s",
        settings.app_name,
        settings.app_env,
        settings.openai_chat_model,
        settings.openai_embedding_model,
        settings.openai_base_url,
    )

    if settings.vector_database_url:
        try:
            await create_pool(settings)
            await ensure_schema(settings)
        except Exception:
            logger.exception(
                "Vector DB 连接失败。请检查 .env 中 VECTOR_DATABASE_URL 是否包含用户名密码，"
                "格式示例: postgresql://USER:PASSWORD@HOST:PORT/DB"
            )
            raise
    else:
        logger.warning("VECTOR_DATABASE_URL not set; vector features disabled")

    yield

    await close_pool()
    logger.info("Shutdown complete")
