"""AI 搜梗流式编排：LangChain Agent + SSE。"""

from __future__ import annotations

import asyncio
import json
import logging
import time
from collections.abc import AsyncIterator
from dataclasses import dataclass, field
from typing import Any

from langchain_core.messages import AIMessage, HumanMessage
from langchain_openai import ChatOpenAI

from app.agents.meme_agent import build_meme_search_agent
from app.agents.prompts import PROMPT_VERSION, build_user_payload
from app.agents.tools import (
    build_business_candidate_chunks,
    build_search_admin_knowledge_tool,
    build_search_live_web_tool,
    build_search_meme_tool,
    parse_tool_payload,
    retrieve_meme_chunks,
)
from app.core.resilience import UpstreamRetriesExhausted
from app.core.settings import Settings, apply_langsmith_env, get_settings
from app.retrieval.repository import RetrievedChunk
from app.schemas.stream import (
    StreamCiteEvent,
    StreamDoneEvent,
    StreamErrorEvent,
    StreamMetaEvent,
    StreamRequest,
    StreamTokenEvent,
)
from app.security.sanitize import (
    looks_like_prompt_injection,
    sanitize_business_extra,
    sanitize_chat_messages,
    sanitize_hint_meme_ids,
    sanitize_user_text,
)

logger = logging.getLogger(__name__)


def _build_llm(settings: Settings, max_tokens: int) -> ChatOpenAI | None:
    if not settings.openai_api_key:
        return None
    kwargs: dict = {
        "model": settings.openai_chat_model,
        "api_key": settings.openai_api_key,
        "max_tokens": max_tokens,
        "timeout": settings.llm_request_timeout_seconds,
        "max_retries": settings.llm_max_retries,
        "streaming": True,
        "temperature": 0.2,
    }
    if settings.openai_base_url:
        kwargs["base_url"] = settings.openai_base_url
    if settings.llm_stream_usage:
        kwargs["stream_usage"] = True
    return ChatOpenAI(**kwargs)


def _format_sse(event: str, payload: dict) -> str:
    return f"event: {event}\ndata: {json.dumps(payload, ensure_ascii=False)}\n\n"


def _snippets_from_context(extra: dict[str, Any]) -> list[dict]:
    raw = extra.get("snippets") if isinstance(extra, dict) else None
    if not isinstance(raw, list):
        return []
    return [item for item in raw if isinstance(item, dict)]


def _history_messages(request: StreamRequest) -> list:
    history = []
    for msg in sanitize_chat_messages(request.messages):
        if msg.role == "user":
            history.append(HumanMessage(content=msg.content))
        elif msg.role == "assistant":
            history.append(AIMessage(content=msg.content))
    return history


def _fallback_answer(query: str, retrieved: list[RetrievedChunk]) -> str:
    if not retrieved:
        return f"暂未检索到与「{query}」相关的梗。请尝试换关键词，或到首页浏览热门内容。"
    lines = [f"找到 {len(retrieved)} 条相关梗："]
    for chunk in retrieved[:5]:
        title = (chunk.title or chunk.content.splitlines()[0][:40]).strip()
        lines.append(f"- {title}")
    lines.append("可点击下方「相关梗」查看详情。")
    return "\n".join(lines)


@dataclass
class _StreamState:
    # 当前请求可用的站内梗候选。
    retrieved: list[RetrievedChunk]
    # 首次检索状态：pending、success 或 degraded。
    retrieval_status: str = "pending"
    # 已进入用户可见 token 流的字符数，用于决定错误后的降级方式。
    visible_chars: int = 0
    # 是否已发出 meta 事件，避免重复发送。
    meta_emitted: bool = False
    # 已发出的梗引用 ID，用于去重。
    emitted_citations: set[str] = field(default_factory=set)
    # 模型供应商返回的实际 token 用量。
    usage: dict[str, int] = field(default_factory=dict)
    # 站内检索及其重试、退避的总耗时（毫秒）；未执行时为 None。
    retrieval_duration_ms: int | None = None
    # Agent 模型流执行耗时（毫秒）；未启动模型时为 None。
    agent_duration_ms: int | None = None


def _fallback_text(
    query: str,
    state: _StreamState,
) -> str:
    if state.retrieved:
        return _fallback_answer(query, state.retrieved)
    if state.retrieval_status == "success":
        return _fallback_answer(query, [])
    return "暂时无法连接梗库，当前无法确认是否存在相关内容。请稍后重试。"


def _retrieval_events(request: StreamRequest, state: _StreamState) -> list[str]:
    events: list[str] = []
    if not state.meta_emitted:
        meme_ids = list(dict.fromkeys(chunk.meme_id for chunk in state.retrieved))
        events.append(
            _format_sse(
                "meta",
                StreamMetaEvent(request_id=request.request_id, retrieved=meme_ids).model_dump(),
            )
        )
        state.meta_emitted = True
    for chunk in state.retrieved:
        if chunk.meme_id in state.emitted_citations:
            continue
        state.emitted_citations.add(chunk.meme_id)
        events.append(
            _format_sse(
                "cite",
                StreamCiteEvent(
                    meme_id=chunk.meme_id,
                    score=chunk.score,
                    title=chunk.title,
                ).model_dump(),
            )
        )
    return events


def _fallback_events(
    query: str,
    state: _StreamState,
    finish_reason: str,
) -> list[str]:
    if state.retrieval_status == "degraded":
        finish_reason = "fallback_retrieval_unavailable"
    answer = _fallback_text(query, state)
    events = []
    for piece in _chunk_text(answer, 24):
        state.visible_chars += len(piece)
        events.append(_format_sse("token", StreamTokenEvent(text=piece).model_dump()))
    events.append(
        _format_sse(
            "done",
            StreamDoneEvent(
                finish_reason=finish_reason,
                usage={"completion": len(answer), **state.usage},
            ).model_dump(),
        )
    )
    return events


def _contains_tool_call_data(chunk: Any) -> bool:
    """Tool-call chunks are control data even if a provider attaches visible text."""
    if isinstance(chunk, dict):
        if chunk.get("tool_calls") or chunk.get("tool_call_chunks"):
            return True
        return any(
            _contains_tool_call_data(chunk.get(key))
            for key in ("message", "output", "generations", "messages")
            if chunk.get(key) is not None
        )
    if isinstance(chunk, (list, tuple)):
        return any(_contains_tool_call_data(item) for item in chunk)
    if bool(
        getattr(chunk, "tool_calls", None)
        or getattr(chunk, "tool_call_chunks", None)
    ):
        return True
    return any(
        _contains_tool_call_data(getattr(chunk, key, None))
        for key in ("message", "output", "generations", "messages")
        if getattr(chunk, key, None) is not None
    )


def _chunk_text(text: str, size: int) -> list[str]:
    return [text[i : i + size] for i in range(0, len(text), size)]


def _decode_sse_event(event: str) -> tuple[str, dict[str, Any]]:
    """解析本服务发出的单条 SSE，用于只记录低基数遥测字段。"""
    event_name = "message"
    payload: dict[str, Any] = {}
    for line in event.splitlines():
        if line.startswith("event: "):
            event_name = line[7:].strip()
        elif line.startswith("data: "):
            try:
                decoded = json.loads(line[6:])
                if isinstance(decoded, dict):
                    payload = decoded
            except json.JSONDecodeError:
                pass
    return event_name, payload


async def stream_ai_search(request: StreamRequest) -> AsyncIterator[str]:
    started_at = time.monotonic()
    first_token_at: float | None = None
    streamed_chars = 0
    outcome = "incomplete"
    finish_reason = "unknown"
    settings = get_settings()
    apply_langsmith_env(settings)
    max_tokens = min(request.max_tokens, settings.max_output_tokens)
    safe_query = sanitize_user_text(request.query, max_len=2000)
    injection_flag = looks_like_prompt_injection(safe_query)
    if injection_flag:
        logger.warning(
            "prompt_injection_suspected request_id=%s",
            request.request_id,
        )
    extra = sanitize_business_extra(request.context.extra or {})
    snippets = _snippets_from_context(extra)
    hint_ids = sanitize_hint_meme_ids(list(request.context.hint_meme_ids or []))
    state = _StreamState(retrieved=build_business_candidate_chunks(snippets))

    try:
        async with asyncio.timeout(settings.agent_total_timeout_seconds):
            async for event in _stream_ai_search_body(
                request,
                settings,
                max_tokens,
                safe_query,
                snippets,
                hint_ids,
                state,
            ):
                event_name, payload = _decode_sse_event(event)
                if event_name == "token":
                    if first_token_at is None:
                        first_token_at = time.monotonic()
                    streamed_chars += len(str(payload.get("text") or ""))
                elif event_name == "done":
                    finish_reason = str(payload.get("finish_reason") or "unknown")
                    outcome = "fallback" if finish_reason.startswith("fallback") else "done"
                elif event_name == "error":
                    outcome = "error_after_token" if first_token_at is not None else "error_before_token"
                yield event
    except asyncio.CancelledError:
        outcome = "cancelled"
        raise
    except TimeoutError:
        logger.warning("agent_total_timeout request_id=%s", request.request_id)
        if state.visible_chars:
            yield _format_sse(
                "error",
                StreamErrorEvent(
                    code="agent_timeout",
                    message="回答生成超时，请重新发起搜索。",
                ).model_dump(),
            )
            return
        for event in _retrieval_events(request, state):
            yield event
        for event in _fallback_events(safe_query, state, "fallback_total_timeout"):
            yield event
    except Exception as error:
        logger.error(
            "stream failed request_id=%s error_type=%s",
            request.request_id,
            type(error).__name__,
        )
        if state.visible_chars:
            yield _format_sse(
                "error",
                StreamErrorEvent(
                    code="agent_error",
                    message="搜索服务暂时不可用，请稍后重试。",
                ).model_dump(),
            )
            return
        for event in _retrieval_events(request, state):
            yield event
        for event in _fallback_events(safe_query, state, "fallback_agent_error"):
            yield event
    finally:
        if outcome == "incomplete":
            outcome = "cancelled"
        total_ms = int((time.monotonic() - started_at) * 1000)
        ttft_ms = (
            int((first_token_at - started_at) * 1000)
            if first_token_at is not None
            else -1
        )
        logger.info(
            "ai_stream_metrics request_id=%s prompt_version=%s outcome=%s finish_reason=%s "
            "retrieval_status=%s retrieval_ms=%s agent_ms=%s ttft_ms=%s total_ms=%s token_chars=%s",
            request.request_id,
            PROMPT_VERSION,
            outcome,
            finish_reason,
            state.retrieval_status,
            state.retrieval_duration_ms if state.retrieval_duration_ms is not None else -1,
            state.agent_duration_ms if state.agent_duration_ms is not None else -1,
            ttft_ms,
            total_ms,
            streamed_chars,
        )


async def _stream_ai_search_body(
    request: StreamRequest,
    settings: Settings,
    max_tokens: int,
    safe_query: str,
    snippets: list[dict],
    hint_ids: list[str],
    state: _StreamState,
) -> AsyncIterator[str]:
    retrieval_started_at = time.monotonic()
    try:
        state.retrieved = await retrieve_meme_chunks(
            query=safe_query,
            hint_meme_ids=hint_ids,
            mysql_snippets=snippets,
            top_k=settings.retrieval_top_k,
            request_id=request.request_id,
        )
        state.retrieval_status = "success"
    except UpstreamRetriesExhausted as error:
        state.retrieval_status = "degraded"
        logger.warning(
            "meme_retrieval_degraded request_id=%s attempts=%s error_type=%s",
            request.request_id,
            error.attempts,
            type(error.__cause__).__name__ if error.__cause__ else "unknown",
        )
    finally:
        state.retrieval_duration_ms = int((time.monotonic() - retrieval_started_at) * 1000)

    for event in _retrieval_events(request, state):
        yield event

    llm = _build_llm(settings, max_tokens)
    if llm is None:
        for event in _fallback_events(safe_query, state, "fallback_no_model"):
            yield event
        return

    tool = build_search_meme_tool(
        default_hint_ids=hint_ids,
        default_snippets=snippets,
        default_top_k=settings.retrieval_top_k,
        initial_query=safe_query,
        initial_chunks=state.retrieved,
        retrieval_unavailable=state.retrieval_status == "degraded",
        request_id=request.request_id,
    )
    live_tool = build_search_live_web_tool(request_id=request.request_id)
    knowledge_tool = build_search_admin_knowledge_tool(
        default_top_k=settings.retrieval_top_k,
        request_id=request.request_id,
    )
    executor = build_meme_search_agent(
        llm, [tool, knowledge_tool, live_tool], max_iterations=4
    )
    # 暂存每次模型调用的文本，等结束事件确认没有工具调用后再向用户发送。
    pending_model_text: dict[str, list[str]] = {}
    tool_call_runs: set[str] = set()

    def flush_model_run(run_id: str, output: Any = None) -> list[str]:
        pieces = pending_model_text.pop(run_id, [])
        has_tool_call = run_id in tool_call_runs or _contains_tool_call_data(output)
        tool_call_runs.discard(run_id)
        if has_tool_call:
            return []
        events: list[str] = []
        for piece in pieces:
            state.visible_chars += len(piece)
            events.append(_format_sse("token", StreamTokenEvent(text=piece).model_dump()))
        return events

    agent_input = build_user_payload(
        safe_query,
        {
            "locale": sanitize_user_text(request.context.locale or "zh-CN", max_len=32),
            "hint_meme_ids": hint_ids,
            "snippets": snippets,
        },
    )

    agent_started_at = time.monotonic()
    try:
        async for event in executor.astream_events(
            {
                "input": agent_input,
                "chat_history": _history_messages(request),
            },
            version="v2",
            config={
                "metadata": {
                    "request_id": request.request_id,
                    "session_id": request.session_id,
                    "prompt_version": PROMPT_VERSION,
                },
                "tags": ["meme-search-agent", f"prompt:{PROMPT_VERSION}"],
            },
        ):
            kind = event.get("event")
            if kind == "on_tool_end":
                output = event.get("data", {}).get("output")
                raw = output if isinstance(output, str) else getattr(output, "content", "")
                for chunk in parse_tool_payload(str(raw or "")):
                    if chunk.meme_id in state.emitted_citations:
                        continue
                    state.emitted_citations.add(chunk.meme_id)
                    yield _format_sse(
                        "cite",
                        StreamCiteEvent(
                            meme_id=chunk.meme_id,
                            score=chunk.score,
                            title=chunk.title,
                        ).model_dump(),
                    )
            elif kind == "on_chat_model_end":
                run_id = str(event.get("run_id") or "__untracked__")
                output = event.get("data", {}).get("output")
                for token_event in flush_model_run(run_id, output):
                    yield token_event
            elif kind == "on_chat_model_stream":
                chunk = event.get("data", {}).get("chunk")
                state.usage.update(_extract_usage(chunk))
                run_id = str(event.get("run_id") or "__untracked__")
                if _contains_tool_call_data(chunk):
                    tool_call_runs.add(run_id)
                    pending_model_text.pop(run_id, None)
                    continue
                text = _extract_stream_text(chunk)
                if text and run_id not in tool_call_runs:
                    pending_model_text.setdefault(run_id, []).append(text)
        # 缺少模型结束确认时丢弃缓冲文本，并进入统一降级路径，避免泄漏工具控制内容。
        if pending_model_text or tool_call_runs:
            pending_model_text.clear()
            tool_call_runs.clear()
            raise RuntimeError("Chat model completion event missing")
    except asyncio.CancelledError:
        raise
    except Exception as error:
        logger.error(
            "LangChain agent failed request_id=%s error_type=%s",
            request.request_id,
            type(error).__name__,
        )
        if state.visible_chars:
            yield _format_sse(
                "error",
                StreamErrorEvent(
                    code="llm_error",
                    message="暂时无法生成回答，请稍后重试。",
                ).model_dump(),
            )
            return
        for event in _fallback_events(safe_query, state, "fallback_llm_error"):
            yield event
        return
    finally:
        state.agent_duration_ms = int((time.monotonic() - agent_started_at) * 1000)

    yield _format_sse(
        "done",
        StreamDoneEvent(
            finish_reason="stop",
            usage={"completion": state.visible_chars, **state.usage},
        ).model_dump(),
    )


def _extract_stream_text(chunk: Any) -> str:
    """兼容 str / content blocks 的流式 token 文本。"""
    if chunk is None:
        return ""
    text = getattr(chunk, "content", None)
    if isinstance(text, str):
        return text
    if isinstance(text, list):
        parts: list[str] = []
        for block in text:
            if isinstance(block, str):
                parts.append(block)
            elif isinstance(block, dict):
                if block.get("type") == "text" or "text" in block:
                    parts.append(str(block.get("text") or ""))
            else:
                piece = getattr(block, "text", None)
                if piece:
                    parts.append(str(piece))
        return "".join(parts)
    # langchain_core 新版 AIMessageChunk.text
    fallback = getattr(chunk, "text", None)
    return str(fallback) if fallback else ""


def _extract_usage(chunk: Any) -> dict[str, int]:
    """读取 LangChain 流式消息附带的 token 用量；不把字符数伪装成 token。"""
    metadata = getattr(chunk, "usage_metadata", None)
    if not isinstance(metadata, dict):
        return {}
    aliases = {
        "input_tokens": "prompt_tokens",
        "output_tokens": "completion_tokens",
        "total_tokens": "total_tokens",
    }
    usage: dict[str, int] = {}
    for source, target in aliases.items():
        value = metadata.get(source)
        if isinstance(value, (int, float)) and not isinstance(value, bool):
            usage[target] = max(0, int(value))
    return usage
