"""应用生命周期内创建的共享异步任务管理。"""

from __future__ import annotations

import asyncio
import logging
from collections.abc import Coroutine
from typing import Any, TypeVar


logger = logging.getLogger(__name__)
T = TypeVar("T")


class ManagedTaskRegistry:
    """登记脱离单个请求但仍需在进程关停时取消的异步任务。"""

    def __init__(self) -> None:
        # 中文注释：保存尚未结束的共享任务，供应用关停时统一取消和等待。
        self._tasks: set[asyncio.Task[Any]] = set()
        # 中文注释：关停开始后禁止创建新的脱离请求生命周期的任务。
        self._accepting = True

    def start(self) -> None:
        """为应用启动或同进程内的下一次 ASGI lifespan 重新开放任务登记。"""
        self._accepting = True

    def create_task(
        self,
        coroutine: Coroutine[Any, Any, T],
        *,
        name: str,
    ) -> asyncio.Task[T]:
        """创建并登记任务；服务已停止接收任务时拒绝创建。"""
        if not self._accepting:
            coroutine.close()
            raise RuntimeError("MemeAgent 正在关闭，不接受新的后台任务")

        task = asyncio.create_task(coroutine, name=name)
        self._tasks.add(task)
        task.add_done_callback(self._tasks.discard)
        return task

    async def cancel_all(self) -> None:
        """停止接收新任务，取消并等待所有共享任务释放其资源。"""
        self._accepting = False
        pending = tuple(task for task in self._tasks if not task.done())
        if not pending:
            return

        task_names = [task.get_name() for task in pending]
        logger.info(
            "Cancelling managed background tasks count=%s names=%s",
            len(pending),
            task_names,
        )
        for task in pending:
            task.cancel("MemeAgent application shutdown")

        results = await asyncio.gather(*pending, return_exceptions=True)
        failure_count = sum(
            isinstance(result, BaseException)
            and not isinstance(result, asyncio.CancelledError)
            for result in results
        )
        logger.info(
            "Managed background tasks stopped count=%s failures=%s",
            len(pending),
            failure_count,
        )


# 中文注释：搜索、Embedding 和应用生命周期共享此任务登记器。
managed_task_registry = ManagedTaskRegistry()
