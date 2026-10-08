"""Bounded retry policy for transient upstream calls."""

from __future__ import annotations

import asyncio
import logging
import time
from collections.abc import Awaitable, Callable
from typing import TypeVar

import httpx
import openai

logger = logging.getLogger(__name__)
_sleep = asyncio.sleep
T = TypeVar("T")


class UpstreamRetriesExhausted(RuntimeError):
    """An upstream operation failed after its configured attempt budget."""

    def __init__(self, stage: str, attempts: int) -> None:
        self.stage = stage
        self.attempts = attempts
        super().__init__(f"{stage} failed after {attempts} attempt(s)")


def _is_transient_error(error: Exception) -> bool:
    if isinstance(
        error,
        (
            TimeoutError,
            asyncio.TimeoutError,
            OSError,
            httpx.TimeoutException,
            httpx.TransportError,
            openai.APIConnectionError,
            openai.APITimeoutError,
        ),
    ):
        return True

    status_code = getattr(error, "status_code", None)
    return status_code == 429 or (isinstance(status_code, int) and status_code >= 500)


async def run_with_retry(
    operation: Callable[[], Awaitable[T]],
    *,
    timeout_seconds: float,
    max_retries: int,
    stage: str,
    request_id: str,
) -> T:
    """Run one async upstream operation with a timeout and bounded retries."""
    if timeout_seconds <= 0:
        raise ValueError("timeout_seconds must be greater than zero")
    if max_retries < 0:
        raise ValueError("max_retries cannot be negative")

    started_at = time.monotonic()
    for attempt in range(1, max_retries + 2):
        try:
            result = await asyncio.wait_for(operation(), timeout=timeout_seconds)
            logger.info(
                "upstream_call_metrics stage=%s outcome=success attempts=%s retry_count=%s "
                "duration_ms=%s request_id=%s",
                stage,
                attempt,
                attempt - 1,
                int((time.monotonic() - started_at) * 1000),
                request_id,
            )
            return result
        except asyncio.CancelledError:
            raise
        except Exception as error:
            if attempt > max_retries or not _is_transient_error(error):
                logger.warning(
                    "upstream_call_failed stage=%s attempts=%s request_id=%s error_type=%s",
                    stage,
                    attempt,
                    request_id,
                    type(error).__name__,
                )
                logger.info(
                    "upstream_call_metrics stage=%s outcome=failed attempts=%s retry_count=%s "
                    "duration_ms=%s request_id=%s",
                    stage,
                    attempt,
                    attempt - 1,
                    int((time.monotonic() - started_at) * 1000),
                    request_id,
                )
                raise UpstreamRetriesExhausted(stage, attempt) from error

            delay_seconds = min(0.5 * (2 ** (attempt - 1)), 2.0)
            logger.warning(
                "upstream_retry stage=%s attempt=%s max_retries=%s delay_seconds=%s "
                "retry_count=%s request_id=%s error_type=%s",
                stage,
                attempt,
                max_retries,
                delay_seconds,
                attempt,
                request_id,
                type(error).__name__,
            )
            await _sleep(delay_seconds)

    raise AssertionError("unreachable retry state")
