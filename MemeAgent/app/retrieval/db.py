import logging
from typing import Any

import asyncpg

from app.core.settings import Settings

logger = logging.getLogger(__name__)

_pool: asyncpg.Pool | None = None


async def create_pool(settings: Settings) -> asyncpg.Pool:
    global _pool
    if _pool is not None:
        return _pool
    _pool = await asyncpg.create_pool(
        dsn=settings.vector_database_url,
        min_size=1,
        max_size=10,
        command_timeout=30,
    )
    logger.info("Vector DB pool created")
    return _pool


async def close_pool() -> None:
    global _pool
    if _pool is not None:
        await _pool.close()
        _pool = None
        logger.info("Vector DB pool closed")


def get_pool() -> asyncpg.Pool:
    if _pool is None:
        raise RuntimeError("Vector DB pool is not initialized")
    return _pool


async def ensure_schema(settings: Settings) -> None:
    pool = get_pool()
    dim = settings.vector_embedding_dim
    async with pool.acquire() as conn:
        await conn.execute("CREATE EXTENSION IF NOT EXISTS vector")
        await conn.execute(
            f"""
            CREATE TABLE IF NOT EXISTS meme_vector_chunk (
                id              BIGSERIAL PRIMARY KEY,
                meme_id         VARCHAR(64) NOT NULL,
                chunk_index     INT NOT NULL DEFAULT 0,
                content         TEXT NOT NULL,
                content_hash    VARCHAR(64) NOT NULL,
                embedding       vector({dim}),
                updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                CONSTRAINT uq_meme_vector_chunk UNIQUE (meme_id, chunk_index)
            )
            """
        )
        await conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_meme_vector_chunk_meme_id "
            "ON meme_vector_chunk (meme_id)"
        )
        await conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_meme_vector_chunk_hash "
            "ON meme_vector_chunk (meme_id, content_hash)"
        )


async def ping_db() -> bool:
    try:
        pool = get_pool()
        async with pool.acquire() as conn:
            await conn.fetchval("SELECT 1")
        return True
    except Exception:
        return False


def vector_literal(values: list[float]) -> str:
    return "[" + ",".join(f"{v:.8f}" for v in values) + "]"
