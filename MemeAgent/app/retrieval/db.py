import logging
from typing import Any

import asyncpg

from app.core.settings import Settings

logger = logging.getLogger(__name__)

_pool: asyncpg.Pool | None = None
_VECTOR_TABLES = frozenset({"meme_vector_chunk", "knowledge_vector_chunk"})
_HNSW_INDEXES = (
    ("idx_meme_vector_chunk_embedding_hnsw", "meme_vector_chunk", 73106001),
    ("idx_knowledge_vector_chunk_embedding_hnsw", "knowledge_vector_chunk", 73106002),
)


async def create_pool(settings: Settings) -> asyncpg.Pool:
    global _pool
    if _pool is not None:
        return _pool
    _pool = await asyncpg.create_pool(
        dsn=settings.vector_database_url,
        min_size=settings.vector_pool_min_size,
        max_size=settings.vector_pool_max_size,
        command_timeout=settings.vector_db_command_timeout_seconds,
    )
    logger.info("Vector DB pool created")
    return _pool


async def close_pool() -> None:
    global _pool
    if _pool is not None:
        await _pool.close()
        _pool = None
        logger.info("Vector DB pool closed")


async def _ensure_embedding_dimension(conn: Any, table_name: str, expected_dim: int) -> None:
    """Align an empty vector table to the configured model dimension safely."""
    if table_name not in _VECTOR_TABLES:
        raise ValueError(f"Unsupported vector table: {table_name}")

    type_query = """
        SELECT format_type(a.atttypid, a.atttypmod)
        FROM pg_catalog.pg_attribute a
        WHERE a.attrelid = to_regclass($1)
          AND a.attname = 'embedding'
          AND NOT a.attisdropped
    """
    expected_type = f"vector({expected_dim})"
    current_type = await conn.fetchval(type_query, table_name)
    if current_type == expected_type:
        return

    # Lock and re-check before changing the type so a concurrent ingest cannot race
    # the empty-table check. Existing vectors require an explicit re-embedding job.
    async with conn.transaction():
        await conn.execute(f"LOCK TABLE {table_name} IN ACCESS EXCLUSIVE MODE")
        current_type = await conn.fetchval(type_query, table_name)
        if current_type == expected_type:
            return
        has_rows = await conn.fetchval(f"SELECT EXISTS (SELECT 1 FROM {table_name} LIMIT 1)")
        if has_rows:
            raise RuntimeError(
                f"{table_name}.embedding is {current_type}; expected {expected_type}. "
                "The table contains vectors; re-embed its documents before changing dimensions."
            )
        await conn.execute(
            f"ALTER TABLE {table_name} ALTER COLUMN embedding TYPE vector({expected_dim})"
        )


async def _ensure_hnsw_indexes(conn: Any) -> None:
    """串行创建 cosine HNSW 索引，并使用 CONCURRENTLY 避免阻塞向量表写入。"""
    # m=16 控制图中每层的连接数；ef_construction=64 控制建图候选范围与构建成本。
    for index_name, table_name, lock_id in _HNSW_INDEXES:
        # 多个 Uvicorn worker 可能同时启动；会话级 advisory lock 不进入事务，允许 CONCURRENTLY。
        await conn.fetchval("SELECT pg_advisory_lock($1)", lock_id)
        try:
            await conn.execute(
                f"CREATE INDEX CONCURRENTLY IF NOT EXISTS {index_name} "
                f"ON {table_name} USING hnsw (embedding vector_cosine_ops) "
                "WITH (m = 16, ef_construction = 64)"
            )
        finally:
            await conn.fetchval("SELECT pg_advisory_unlock($1)", lock_id)


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
        await conn.execute("COMMENT ON COLUMN meme_vector_chunk.id IS '向量片段主键'")
        await conn.execute("COMMENT ON COLUMN meme_vector_chunk.meme_id IS '对应业务梗 ID'")
        await conn.execute("COMMENT ON COLUMN meme_vector_chunk.chunk_index IS '同一梗内片段序号'")
        await conn.execute("COMMENT ON COLUMN meme_vector_chunk.content IS '用于检索和引用的文本片段'")
        await conn.execute("COMMENT ON COLUMN meme_vector_chunk.content_hash IS '梗文档内容哈希，用于跳过未变更内容'")
        await conn.execute("COMMENT ON COLUMN meme_vector_chunk.embedding IS '文本片段的向量表示'")
        await conn.execute("COMMENT ON COLUMN meme_vector_chunk.updated_at IS '向量片段最后更新时间'")
        await _ensure_embedding_dimension(conn, "meme_vector_chunk", dim)
        await conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_meme_vector_chunk_meme_id "
            "ON meme_vector_chunk (meme_id)"
        )
        await conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_meme_vector_chunk_hash "
            "ON meme_vector_chunk (meme_id, content_hash)"
        )
        await conn.execute(
            f"""
            CREATE TABLE IF NOT EXISTS knowledge_vector_chunk (
                id              BIGSERIAL PRIMARY KEY,
                doc_id          VARCHAR(64) NOT NULL,
                chunk_index     INT NOT NULL DEFAULT 0,
                content         TEXT NOT NULL,
                content_hash    VARCHAR(64) NOT NULL,
                title           VARCHAR(200),
                category        VARCHAR(64),
                embedding       vector({dim}),
                updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                CONSTRAINT uq_knowledge_vector_chunk UNIQUE (doc_id, chunk_index)
            )
            """
        )
        await conn.execute("COMMENT ON COLUMN knowledge_vector_chunk.id IS '知识向量片段主键'")
        await conn.execute("COMMENT ON COLUMN knowledge_vector_chunk.doc_id IS '运营知识文档 ID'")
        await conn.execute("COMMENT ON COLUMN knowledge_vector_chunk.chunk_index IS '同一文档内片段序号'")
        await conn.execute("COMMENT ON COLUMN knowledge_vector_chunk.content IS '用于检索和引用的知识片段'")
        await conn.execute("COMMENT ON COLUMN knowledge_vector_chunk.content_hash IS '知识文档内容哈希，用于跳过未变更内容'")
        await conn.execute("COMMENT ON COLUMN knowledge_vector_chunk.title IS '知识文档标题'")
        await conn.execute("COMMENT ON COLUMN knowledge_vector_chunk.category IS '知识文档分类'")
        await conn.execute("COMMENT ON COLUMN knowledge_vector_chunk.embedding IS '知识片段的向量表示'")
        await conn.execute("COMMENT ON COLUMN knowledge_vector_chunk.updated_at IS '知识向量片段最后更新时间'")
        await _ensure_embedding_dimension(conn, "knowledge_vector_chunk", dim)
        await conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_knowledge_vector_chunk_doc_id "
            "ON knowledge_vector_chunk (doc_id)"
        )
        await conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_knowledge_vector_chunk_hash "
            "ON knowledge_vector_chunk (doc_id, content_hash)"
        )
        await _ensure_hnsw_indexes(conn)


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
