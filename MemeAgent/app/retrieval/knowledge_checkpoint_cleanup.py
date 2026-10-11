"""清理超过保留期的管理员知识 embedding 检查点。"""

import asyncio
import logging

from app.retrieval.db import get_pool

logger = logging.getLogger(__name__)

# 中文注释：与向量表 HNSW 建表锁 ID 分开，保证多个 worker 仅有一个执行清理。
_KNOWLEDGE_CHECKPOINT_CLEANUP_LOCK_ID = 73106003


async def cleanup_expired_knowledge_checkpoints(ttl_days: int) -> int:
    """删除超过 ttl_days 的临时向量，并返回删除数量。"""
    if ttl_days < 1:
        raise ValueError("知识向量检查点保留天数必须大于 0")

    pool = get_pool()
    async with pool.acquire() as conn:
        acquired = await conn.fetchval(
            "SELECT pg_try_advisory_lock($1)",
            _KNOWLEDGE_CHECKPOINT_CLEANUP_LOCK_ID,
        )
        if not acquired:
            return 0
        try:
            result = await conn.execute(
                "DELETE FROM knowledge_embedding_checkpoint "
                "WHERE created_at < NOW() - ($1::int * INTERVAL '1 day')",
                ttl_days,
            )
            try:
                return int(result.rsplit(" ", 1)[-1])
            except (ValueError, IndexError):
                return 0
        finally:
            await conn.fetchval(
                "SELECT pg_advisory_unlock($1)",
                _KNOWLEDGE_CHECKPOINT_CLEANUP_LOCK_ID,
            )


async def run_knowledge_checkpoint_cleanup(
    ttl_days: int,
    interval_seconds: int = 86400,
) -> None:
    """启动后立即清理一次，之后按 interval_seconds 周期执行。"""
    if interval_seconds < 1:
        raise ValueError("知识向量检查点清理间隔必须大于 0 秒")

    while True:
        try:
            deleted_count = await cleanup_expired_knowledge_checkpoints(ttl_days)
            if deleted_count:
                logger.info("Expired knowledge embedding checkpoints deleted rows=%s", deleted_count)
        except asyncio.CancelledError:
            raise
        except Exception as error:
            logger.exception("Knowledge embedding checkpoint cleanup failed")
        await asyncio.sleep(interval_seconds)
