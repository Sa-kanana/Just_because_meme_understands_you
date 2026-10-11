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
from langsmith.run_helpers import trace

from app.agents.meme_agent import build_meme_search_agent
from app.agents.prompts import (
    AGENT_SYSTEM_PROMPT,
    PROMPT_VERSION,
    build_agent_prompt_inputs,
)
from app.agents.query_intent import greeting_reply
from app.agents.retrieval_query_rewrite import (
    rewrite_retrieval_query,
    should_attempt_contextual_rewrite,
)
from app.agents.tools import (
    build_search_admin_knowledge_tool,
    build_search_meme_tool,
    retrieve_admin_knowledge_chunks,
    retrieve_meme_chunks,
)
from app.core.settings import Settings, apply_langsmith_env, get_settings
from app.retrieval.knowledge_repository import RetrievedKnowledgeChunk
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
    sanitize_chat_messages,
    sanitize_user_text,
)

logger = logging.getLogger(__name__)

_NO_RETRIEVAL_RESULT_REPLY = (
    "我这边暂时还没找到和你这个问题特别相关的内容，你可以换个说法，"
    "或者再补充一点信息，我再帮你找找。"
)


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


def _history_messages(request: StreamRequest) -> list:
    history = []
    for msg in sanitize_chat_messages(request.messages):
        if msg.role == "user":
            history.append(HumanMessage(content=msg.content))
        elif msg.role == "assistant":
            history.append(AIMessage(content=msg.content))
    return history


def _fallback_answer(retrieved: list[RetrievedChunk]) -> str:
    if not retrieved:
        return _NO_RETRIEVAL_RESULT_REPLY
    lines = []
    for chunk in retrieved[:5]:
        title = (chunk.title or chunk.content.splitlines()[0][:40]).strip()
        lines.append(f"- {title}")
    return "\n".join(lines)


@dataclass
class _StreamState:
    # 当前请求可用的站内梗候选。
    retrieved: list[RetrievedChunk]
    # 当前请求预检索到的运营知识库片段。
    knowledge_retrieved: list[RetrievedKnowledgeChunk] = field(default_factory=list)
    # 首次检索状态：pending、success 或 degraded。
    retrieval_status: str = "pending"
    # 运营知识库首次检索状态：pending、success 或 degraded。
    knowledge_retrieval_status: str = "pending"
    # 已进入用户可见 token 流的字符数，用于决定错误后的降级方式。
    visible_chars: int = 0
    # 是否已发出 meta 事件，避免重复发送。
    meta_emitted: bool = False
    # 已发出的梗引用 ID，用于去重。
    emitted_citations: set[str] = field(default_factory=set)
    # 模型供应商返回的实际 token 用量。
    usage: dict[str, int] = field(default_factory=dict)
    # 中文注释：工具二次检索取得、尚未发送 SSE 引用事件的梗候选。
    pending_citations: list[RetrievedChunk] = field(default_factory=list)


def _fallback_text(state: _StreamState) -> str:
    if state.retrieved:
        return _fallback_answer(state.retrieved)
    if state.knowledge_retrieved:
        titles = [
            (chunk.title or chunk.section_path or "").strip()
            for chunk in state.knowledge_retrieved[:5]
        ]
        return (
            "\n".join(f"- {title}" for title in titles if title)
            or _NO_RETRIEVAL_RESULT_REPLY
        )
    return _fallback_answer([])


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
    state: _StreamState,
    finish_reason: str,
) -> list[str]:
    if (
        state.retrieval_status == "degraded"
        or state.knowledge_retrieval_status == "degraded"
    ):
        finish_reason = "fallback_retrieval_unavailable"
    answer = _fallback_text(state)
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


async def stream_ai_search(request: StreamRequest) -> AsyncIterator[str]:
    """以请求级 LangSmith 父 trace 包裹整个 RAG 流程并转发 SSE。"""
    settings = get_settings()
    apply_langsmith_env(settings)
    safe_query = sanitize_user_text(request.query, max_len=2000)
    locale = sanitize_user_text(request.context.locale or "zh-CN", max_len=32)
    event_counts: dict[str, int] = {}
    with trace(
        name="meme_rag_request",
        run_type="chain",
        inputs={
            "question": {"query": safe_query},
            "locale": {"locale": locale},
        },
        project_name=settings.langchain_project,
        tags=["meme-rag", f"prompt:{PROMPT_VERSION}"],
        metadata={
            "request_id": request.request_id,
            "session_id": request.session_id,
            "prompt_version": PROMPT_VERSION,
        },
    ) as request_trace:
        async for event in _stream_ai_search(request):
            event_name = event.partition("\n")[0].removeprefix("event: ").strip()
            event_counts[event_name] = event_counts.get(event_name, 0) + 1
            yield event
        request_trace.end(outputs={"event_counts": event_counts})


async def _trace_query_rewrite(
    query: str,
    history_messages: list,
    settings: Settings,
    *,
    request_id: str,
) -> str | None:
    """将按需查询改写标记成命名 trace 子节点。"""
    with trace(
        name="query_rewrite",
        run_type="chain",
        inputs={"query": query, "history_message_count": len(history_messages)},
        metadata={"request_id": request_id, "stage": "query_rewrite"},
    ) as rewrite_trace:
        rewritten_query = await rewrite_retrieval_query(
            query,
            history_messages,
            settings,
            request_id=request_id,
        )
        rewrite_trace.end(
            outputs={
                "rewritten": rewritten_query is not None,
                "query": rewritten_query,
            }
        )
    return rewritten_query


async def _stream_ai_search(request: StreamRequest) -> AsyncIterator[str]:
    request_started_at = time.monotonic()
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
    state = _StreamState(retrieved=[])
    history_messages = _history_messages(request)
    outcome = "incomplete"
    logger.info(
        "rag_request_started request_id=%s prompt_version=%s query_chars=%s history_messages=%s",
        request.request_id,
        PROMPT_VERSION,
        len(safe_query),
        len(history_messages),
    )

    try:
        greeting = greeting_reply(safe_query)
        if greeting is not None:
            logger.info("greeting_short_circuit request_id=%s", request.request_id)
            logger.info(
                "rag_stage_skipped request_id=%s stage=retrieval reason=greeting",
                request.request_id,
            )
            for event in _retrieval_events(request, state):
                yield event
            for piece in _chunk_text(greeting, 24):
                state.visible_chars += len(piece)
                yield _format_sse("token", StreamTokenEvent(text=piece).model_dump())
            yield _format_sse(
                "done",
                StreamDoneEvent(
                    finish_reason="greeting_no_retrieval",
                    usage={"completion": state.visible_chars},
                ).model_dump(),
            )
            outcome = "completed"
            return

        async with asyncio.timeout(settings.agent_total_timeout_seconds):
            async for event in _stream_ai_search_body(
                request,
                settings,
                max_tokens,
                safe_query,
                state,
                history_messages,
            ):
                yield event
        outcome = "completed"
    except asyncio.CancelledError:
        outcome = "cancelled"
        logger.warning("rag_request_cancelled request_id=%s", request.request_id)
        raise
    except TimeoutError:
        outcome = "timeout_after_partial_answer" if state.visible_chars else "fallback_timeout"
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
        for event in _fallback_events(state, "fallback_total_timeout"):
            yield event
    except Exception as error:
        outcome = "error_after_partial_answer" if state.visible_chars else "fallback_error"
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
        for event in _fallback_events(state, "fallback_agent_error"):
            yield event
    finally:
        logger.info(
            "rag_request_finished request_id=%s outcome=%s retrieval_status=%s retrieved=%s "
            "admin_retrieval_status=%s admin_retrieved=%s "
            "answer_chars=%s duration_ms=%s",
            request.request_id,
            outcome,
            state.retrieval_status,
            len(state.retrieved),
            state.knowledge_retrieval_status,
            len(state.knowledge_retrieved),
            state.visible_chars,
            int((time.monotonic() - request_started_at) * 1000),
        )


async def _stream_ai_search_body(
    request: StreamRequest,
    settings: Settings,
    max_tokens: int,
    safe_query: str,
    state: _StreamState,
    history_messages: list,
) -> AsyncIterator[str]:
    retrieval_query = safe_query
    rewrite_attempted = False
    if history_messages and should_attempt_contextual_rewrite(safe_query):
        rewrite_attempted = True
        rewritten_query = await _trace_query_rewrite(
            safe_query,
            history_messages,
            settings,
            request_id=request.request_id,
        )
        if rewritten_query:
            retrieval_query = rewritten_query
            logger.info(
                "retrieval_query_rewrite_applied request_id=%s reason=context_reference "
                "query_chars=%s history_messages=%s",
                request.request_id,
                len(retrieval_query),
                min(4, len(history_messages)),
            )

    meme_retrieval_started_at = time.monotonic()
    logger.info(
        "rag_stage_started request_id=%s stage=meme_retrieval top_k=%s",
        request.request_id,
        settings.retrieval_top_k,
    )
    admin_retrieval_started_at = time.monotonic()
    logger.info(
        "rag_stage_started request_id=%s stage=admin_retrieval top_k=%s",
        request.request_id,
        settings.retrieval_top_k,
    )
    meme_result, admin_result = await asyncio.gather(
        retrieve_meme_chunks(
            query=retrieval_query,
            top_k=settings.retrieval_top_k,
            request_id=request.request_id,
        ),
        retrieve_admin_knowledge_chunks(
            query=retrieval_query,
            top_k=settings.retrieval_top_k,
            request_id=request.request_id,
        ),
        return_exceptions=True,
    )
    if isinstance(meme_result, BaseException):
        if isinstance(meme_result, asyncio.CancelledError):
            raise meme_result
        if not isinstance(meme_result, Exception):
            raise meme_result
        state.retrieval_status = "degraded"
        logger.warning(
            "rag_stage_completed request_id=%s stage=meme_retrieval status=degraded hits=0 "
            "error_type=%s duration_ms=%s",
            request.request_id,
            type(meme_result).__name__,
            int((time.monotonic() - meme_retrieval_started_at) * 1000),
        )
    else:
        state.retrieved = meme_result
        state.retrieval_status = "success"
        # 中文注释：仅记录前五个候选 ID 和相关度分数，不输出标题或检索正文。
        candidate_summary = ",".join(
            f"{chunk.meme_id}:{chunk.score:.4f}" for chunk in state.retrieved[:5]
        ) or "-"
        logger.info(
            "rag_stage_completed request_id=%s stage=meme_retrieval status=success hits=%s "
            "candidates=%s duration_ms=%s",
            request.request_id,
            len(state.retrieved),
            candidate_summary,
            int((time.monotonic() - meme_retrieval_started_at) * 1000),
        )
    if isinstance(admin_result, BaseException):
        if isinstance(admin_result, asyncio.CancelledError):
            raise admin_result
        if not isinstance(admin_result, Exception):
            raise admin_result
        state.knowledge_retrieval_status = "degraded"
        logger.warning(
            "rag_stage_completed request_id=%s stage=admin_retrieval status=degraded hits=0 "
            "attempts=%s error_type=%s duration_ms=%s",
            request.request_id,
            getattr(admin_result, "attempts", 0),
            type(admin_result.__cause__).__name__ if admin_result.__cause__ else type(admin_result).__name__,
            int((time.monotonic() - admin_retrieval_started_at) * 1000),
        )
    else:
        state.knowledge_retrieved = admin_result
        state.knowledge_retrieval_status = "success"

    if (
        history_messages
        and not rewrite_attempted
        and state.retrieval_status == "success"
        and state.knowledge_retrieval_status == "success"
        and not state.retrieved
        and not state.knowledge_retrieved
    ):
        rewrite_attempted = True
        rewritten_query = await _trace_query_rewrite(
            safe_query,
            history_messages,
            settings,
            request_id=request.request_id,
        )
        if rewritten_query:
            retrieval_query = rewritten_query
            logger.info(
                "retrieval_query_rewrite_applied request_id=%s reason=empty_initial_retrieval "
                "query_chars=%s history_messages=%s",
                request.request_id,
                len(retrieval_query),
                min(4, len(history_messages)),
            )
            meme_retry_started_at = time.monotonic()
            admin_retry_started_at = time.monotonic()
            logger.info(
                "rag_stage_started request_id=%s stage=meme_retrieval_context_retry top_k=%s",
                request.request_id,
                settings.retrieval_top_k,
            )
            logger.info(
                "rag_stage_started request_id=%s stage=admin_retrieval_context_retry top_k=%s",
                request.request_id,
                settings.retrieval_top_k,
            )
            meme_retry_result, admin_retry_result = await asyncio.gather(
                retrieve_meme_chunks(
                    query=retrieval_query,
                    top_k=settings.retrieval_top_k,
                    request_id=request.request_id,
                ),
                retrieve_admin_knowledge_chunks(
                    query=retrieval_query,
                    top_k=settings.retrieval_top_k,
                    request_id=request.request_id,
                ),
                return_exceptions=True,
            )
            if isinstance(meme_retry_result, BaseException):
                if isinstance(meme_retry_result, asyncio.CancelledError):
                    raise meme_retry_result
                if not isinstance(meme_retry_result, Exception):
                    raise meme_retry_result
                state.retrieval_status = "degraded"
                logger.warning(
                    "rag_stage_completed request_id=%s stage=meme_retrieval_context_retry "
                    "status=degraded hits=0 error_type=%s duration_ms=%s",
                    request.request_id,
                    type(meme_retry_result).__name__,
                    int((time.monotonic() - meme_retry_started_at) * 1000),
                )
            else:
                state.retrieved = meme_retry_result
                state.retrieval_status = "success"
                logger.info(
                    "rag_stage_completed request_id=%s stage=meme_retrieval_context_retry "
                    "status=success hits=%s duration_ms=%s",
                    request.request_id,
                    len(state.retrieved),
                    int((time.monotonic() - meme_retry_started_at) * 1000),
                )
            if isinstance(admin_retry_result, BaseException):
                if isinstance(admin_retry_result, asyncio.CancelledError):
                    raise admin_retry_result
                if not isinstance(admin_retry_result, Exception):
                    raise admin_retry_result
                state.knowledge_retrieval_status = "degraded"
                logger.warning(
                    "rag_stage_completed request_id=%s stage=admin_retrieval_context_retry "
                    "status=degraded hits=0 error_type=%s duration_ms=%s",
                    request.request_id,
                    type(admin_retry_result).__name__,
                    int((time.monotonic() - admin_retry_started_at) * 1000),
                )
            else:
                state.knowledge_retrieved = admin_retry_result
                state.knowledge_retrieval_status = "success"
                logger.info(
                    "rag_stage_completed request_id=%s stage=admin_retrieval_context_retry "
                    "status=success hits=%s duration_ms=%s",
                    request.request_id,
                    len(state.knowledge_retrieved),
                    int((time.monotonic() - admin_retry_started_at) * 1000),
                )

    for event in _retrieval_events(request, state):
        yield event

    llm = _build_llm(settings, max_tokens)
    if llm is None:
        logger.info(
            "rag_generation_skipped request_id=%s reason=model_not_configured "
            "meme_retrieved=%s admin_retrieved=%s",
            request.request_id,
            len(state.retrieved),
            len(state.knowledge_retrieved),
        )
        for event in _fallback_events(state, "fallback_no_model"):
            yield event
        return

    tool = build_search_meme_tool(
        default_top_k=settings.retrieval_top_k,
        initial_query=retrieval_query,
        initial_query_aliases=[safe_query],
        initial_chunks=state.retrieved,
        retrieval_unavailable=state.retrieval_status == "degraded",
        request_id=request.request_id,
        on_retrieval=state.pending_citations.extend,
    )
    knowledge_tool = build_search_admin_knowledge_tool(
        default_top_k=settings.retrieval_top_k,
        request_id=request.request_id,
        initial_query=retrieval_query,
        initial_query_aliases=[safe_query],
        initial_chunks=state.knowledge_retrieved,
        retrieval_unavailable=state.knowledge_retrieval_status == "degraded",
    )
    executor = build_meme_search_agent(
        llm, [tool, knowledge_tool], max_iterations=4
    )
    # 跟踪尚未结束的模型调用，并屏蔽携带工具控制字段的调用。
    active_model_runs: set[str] = set()
    tool_call_runs: set[str] = set()

    locale = sanitize_user_text(request.context.locale or "zh-CN", max_len=32)
    agent_input = build_agent_prompt_inputs(
        safe_query,
        locale,
        state.retrieved,
        state.knowledge_retrieved,
        meme_unavailable=state.retrieval_status == "degraded",
        admin_unavailable=state.knowledge_retrieval_status == "degraded",
    )

    generation_started_at = time.monotonic()
    # 保存 LangChain 子调用的起始时刻，用于输出阶段耗时而不记录工具输入或输出。
    active_tool_started_at: dict[str, tuple[str, float]] = {}
    active_model_started_at: dict[str, float] = {}
    # 中文注释：累计本轮 Agent 发起的模型调用次数，便于定位多轮工具编排。
    model_call_count = 0
    # 中文注释：累计本轮 Agent 发起的工具调用次数，不记录工具参数。
    tool_call_count = 0
    logger.info(
        "rag_stage_started request_id=%s stage=agent_generation model=%s max_tokens=%s",
        request.request_id,
        settings.openai_chat_model,
        max_tokens,
    )
    try:
        async for event in executor.astream_events(
            {
                **agent_input,
                "chat_history": history_messages,
            },
            version="v2",
            config={
                "run_name": "agent_generation",
                "metadata": {
                    "request_id": request.request_id,
                    "session_id": request.session_id,
                    "prompt_version": PROMPT_VERSION,
                },
                "tags": [
                    "meme-search-agent",
                    "agent-generation",
                    f"prompt:{PROMPT_VERSION}",
                ],
            },
        ):
            kind = event.get("event")
            if kind == "on_tool_start":
                run_id = str(event.get("run_id") or "__untracked__")
                tool_name = str(event.get("name") or "unknown")
                active_tool_started_at[run_id] = (tool_name, time.monotonic())
                tool_call_count += 1
                logger.info(
                    "rag_tool_started request_id=%s tool=%s",
                    request.request_id,
                    tool_name,
                )
            elif kind == "on_tool_end":
                run_id = str(event.get("run_id") or "__untracked__")
                tool_name, tool_started_at = active_tool_started_at.pop(
                    run_id,
                    (str(event.get("name") or "unknown"), time.monotonic()),
                )
                logger.info(
                    "rag_tool_completed request_id=%s tool=%s duration_ms=%s",
                    request.request_id,
                    tool_name,
                    int((time.monotonic() - tool_started_at) * 1000),
                )
                pending_citations = list(state.pending_citations)
                state.pending_citations.clear()
                for chunk in pending_citations:
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
            elif kind == "on_tool_error":
                run_id = str(event.get("run_id") or "__untracked__")
                tool_name, tool_started_at = active_tool_started_at.pop(
                    run_id,
                    (str(event.get("name") or "unknown"), time.monotonic()),
                )
                error = event.get("data", {}).get("error")
                logger.warning(
                    "rag_tool_failed request_id=%s tool=%s error_type=%s duration_ms=%s",
                    request.request_id,
                    tool_name,
                    type(error).__name__ if error is not None else "unknown",
                    int((time.monotonic() - tool_started_at) * 1000),
                )
            elif kind == "on_chat_model_start":
                run_id = str(event.get("run_id") or "__untracked__")
                active_model_started_at[run_id] = time.monotonic()
                model_call_count += 1
                logger.info(
                    "rag_model_call_started request_id=%s call=%s model=%s",
                    request.request_id,
                    model_call_count,
                    settings.openai_chat_model,
                )
            elif kind == "on_chat_model_end":
                run_id = str(event.get("run_id") or "__untracked__")
                model_started_at = active_model_started_at.pop(run_id, None)
                if model_started_at is not None:
                    logger.info(
                        "rag_model_call_completed request_id=%s call=%s duration_ms=%s",
                        request.request_id,
                        model_call_count,
                        int((time.monotonic() - model_started_at) * 1000),
                    )
                active_model_runs.discard(run_id)
                tool_call_runs.discard(run_id)
            elif kind == "on_chat_model_stream":
                chunk = event.get("data", {}).get("chunk")
                state.usage.update(_extract_usage(chunk))
                run_id = str(event.get("run_id") or "__untracked__")
                active_model_runs.add(run_id)
                if _contains_tool_call_data(chunk):
                    tool_call_runs.add(run_id)
                    continue
                text = _extract_stream_text(chunk)
                if text and run_id not in tool_call_runs:
                    state.visible_chars += len(text)
                    yield _format_sse("token", StreamTokenEvent(text=text).model_dump())
        # 缺少模型结束事件时保留已经流出的部分回答，并由统一错误事件收尾。
        if active_model_runs or tool_call_runs:
            raise RuntimeError("Chat model completion event missing")
    except asyncio.CancelledError:
        logger.warning(
            "rag_stage_cancelled request_id=%s stage=agent_generation duration_ms=%s",
            request.request_id,
            int((time.monotonic() - generation_started_at) * 1000),
        )
        raise
    except Exception as error:
        logger.error(
            "rag_stage_failed request_id=%s stage=agent_generation error_type=%s duration_ms=%s",
            request.request_id,
            type(error).__name__,
            int((time.monotonic() - generation_started_at) * 1000),
        )
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
        for event in _fallback_events(state, "fallback_llm_error"):
            yield event
        return
    logger.info(
        "rag_stage_completed request_id=%s stage=agent_generation status=success duration_ms=%s "
        "model_calls=%s tool_calls=%s answer_chars=%s",
        request.request_id,
        int((time.monotonic() - generation_started_at) * 1000),
        model_call_count,
        tool_call_count,
        state.visible_chars,
    )
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
