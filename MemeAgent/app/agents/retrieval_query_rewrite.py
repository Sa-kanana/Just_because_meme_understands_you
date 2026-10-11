"""按需利用当前会话上下文改写向量检索词。"""

from __future__ import annotations

import asyncio
import json
import logging
import re
import time
from importlib.resources import files

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from langchain_openai import ChatOpenAI
from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    StrictBool,
    StrictStr,
    ValidationError,
    model_validator,
)

from app.core.settings import Settings
from app.security.sanitize import sanitize_user_text

logger = logging.getLogger(__name__)

# 中文注释：只在明显依赖前文的追问上提前改写，避免给完整问题增加模型调用。
_CONTEXT_CUE_PATTERN = re.compile(
    r"(?:它|他|她|这(?:个|条|种|样|件)|那(?:个|条|种|样|件)|"
    r"那(?:是|呢)|上述|上面|前面|刚才|刚刚|继续|然后呢|还有(?:呢|吗)?|"
    r"还(?:有|能|可以)|再(?:说|讲|解释|详细))"
)
# 中文注释：单独的短追问也可能没有明显代词，例如“出处呢？”，只匹配完整短句。
_SHORT_FOLLOWUP_PATTERN = re.compile(
    r"^(?:那)?(?:出处|来源|原因|意思|用法|详细|继续)(?:呢|是什么|如何|怎么样)?[!?！？。,.，、~～\s]*$"
)
# 中文注释：改写最多查看最近四条 user/assistant 消息，避免无关旧话题污染查询。
_MAX_HISTORY_MESSAGES = 4
# 中文注释：上下文正文总字符数上限，降低改写调用的输入成本。
_MAX_HISTORY_CONTEXT_CHARS = 2400
# 中文注释：检索词最大长度，防止模型输出长段解释代替查询词。
_MAX_REWRITTEN_QUERY_CHARS = 300
_QUERY_REWRITE_PROMPT = (
    files("app.agents")
    .joinpath("prompt_files")
    .joinpath("retrieval_query_rewrite_zh.md")
    .read_text(encoding="utf-8")
    .strip()
)


class RetrievalQueryRewrite(BaseModel):
    """上下文检索词改写的严格结构化结果。"""

    model_config = ConfigDict(extra="forbid")

    needs_context: StrictBool = Field(description="当前问题是否必须结合近期对话才能检索")
    query: StrictStr | None = Field(
        max_length=_MAX_REWRITTEN_QUERY_CHARS,
        description="补全指代后的独立检索词；不需要上下文时为 null",
    )

    @model_validator(mode="after")
    def validate_query_matches_context_flag(self) -> "RetrievalQueryRewrite":
        if self.needs_context and not self.query:
            raise ValueError("query is required when needs_context is true")
        if not self.needs_context and self.query is not None:
            raise ValueError("query must be null when needs_context is false")
        return self


def should_attempt_contextual_rewrite(query: str) -> bool:
    """判断问题是否含有明显的上下文指代或追问表达。"""
    normalized = (query or "").strip()
    return bool(
        _CONTEXT_CUE_PATTERN.search(normalized)
        or _SHORT_FOLLOWUP_PATTERN.fullmatch(normalized)
    )


def _recent_history_payload(messages: list) -> list[dict[str, str]]:
    """构造有界的近期对话 JSON 数据，不包含 system 或工具消息。"""
    recent: list[dict[str, str]] = []
    remaining_chars = _MAX_HISTORY_CONTEXT_CHARS
    for message in reversed(messages[-_MAX_HISTORY_MESSAGES:]):
        if isinstance(message, AIMessage):
            role = "assistant"
        elif isinstance(message, HumanMessage):
            role = "user"
        else:
            continue

        content = message.content
        if not isinstance(content, str):
            continue
        content = sanitize_user_text(content, max_len=remaining_chars)
        if not content:
            continue
        recent.append({"role": role, "content": content})
        remaining_chars -= len(content)
        if remaining_chars <= 0:
            break
    recent.reverse()
    return recent


def _build_rewriter(settings: Settings) -> ChatOpenAI | None:
    """创建一次性、非流式且不重试的查询改写模型。"""
    if not settings.openai_api_key:
        return None
    kwargs: dict = {
        "model": settings.openai_chat_model,
        "api_key": settings.openai_api_key,
        "max_tokens": 160,
        "timeout": settings.retrieval_query_rewrite_timeout_seconds,
        "max_retries": 0,
        "streaming": False,
        "temperature": 0,
    }
    if settings.openai_base_url:
        kwargs["base_url"] = settings.openai_base_url
    return ChatOpenAI(**kwargs)


async def rewrite_retrieval_query(
    query: str,
    history_messages: list,
    settings: Settings,
    *,
    request_id: str,
) -> str | None:
    """仅在历史能补全当前问题指代时返回受限的独立检索词。"""
    history = _recent_history_payload(history_messages)
    if not history:
        return None

    if not settings.openai_api_key:
        logger.info(
            "retrieval_query_rewrite_finished request_id=%s status=disabled history_messages=%s",
            request_id,
            len(history),
        )
        return None

    started_at = time.monotonic()
    payload = json.dumps(
        {
            "current_query": sanitize_user_text(query, max_len=2000),
            "recent_messages": history,
        },
        ensure_ascii=False,
        separators=(",", ":"),
    )
    try:
        model = _build_rewriter(settings)
        if model is None:
            return None
        structured_model = model.with_structured_output(
            RetrievalQueryRewrite,
            method="function_calling",
            strict=True,
        )
        async with asyncio.timeout(settings.retrieval_query_rewrite_timeout_seconds):
            response = await structured_model.ainvoke(
                [
                    SystemMessage(content=_QUERY_REWRITE_PROMPT),
                    HumanMessage(content=payload),
                ]
            )
        result = (
            response
            if isinstance(response, RetrievalQueryRewrite)
            else RetrievalQueryRewrite.model_validate(response)
        )
    except asyncio.CancelledError:
        raise
    except (TimeoutError, ValidationError, ValueError, TypeError) as error:
        logger.warning(
            "retrieval_query_rewrite_finished request_id=%s status=failed error_type=%s "
            "history_messages=%s duration_ms=%s",
            request_id,
            type(error).__name__,
            len(history),
            int((time.monotonic() - started_at) * 1000),
        )
        return None
    except Exception as error:
        logger.warning(
            "retrieval_query_rewrite_finished request_id=%s status=failed error_type=%s "
            "history_messages=%s duration_ms=%s",
            request_id,
            type(error).__name__,
            len(history),
            int((time.monotonic() - started_at) * 1000),
        )
        return None

    if not result.needs_context or not result.query:
        logger.info(
            "retrieval_query_rewrite_finished request_id=%s status=not_needed "
            "history_messages=%s duration_ms=%s",
            request_id,
            len(history),
            int((time.monotonic() - started_at) * 1000),
        )
        return None
    rewritten = sanitize_user_text(result.query, max_len=_MAX_REWRITTEN_QUERY_CHARS)
    logger.info(
        "retrieval_query_rewrite_finished request_id=%s status=rewritten "
        "query_chars=%s history_messages=%s duration_ms=%s",
        request_id,
        len(rewritten),
        len(history),
        int((time.monotonic() - started_at) * 1000),
    )
    return rewritten or None
