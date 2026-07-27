"""AI 搜梗流式编排：LangChain Agent + SSE。"""

from __future__ import annotations

import json
import logging
from collections.abc import AsyncIterator
from typing import Any

from langchain_core.messages import AIMessage, HumanMessage
from langchain_openai import ChatOpenAI

from app.agents.meme_agent import build_meme_search_agent
from app.agents.prompts import build_context_block
from app.agents.tools import (
    build_search_live_web_tool,
    build_search_meme_tool,
    parse_tool_payload,
    retrieve_meme_chunks,
)
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
    wrap_untrusted_user_payload,
)

logger = logging.getLogger(__name__)


def _build_llm(settings: Settings, max_tokens: int) -> ChatOpenAI | None:
    if not settings.openai_api_key:
        return None
    kwargs: dict = {
        "model": settings.openai_chat_model,
        "api_key": settings.openai_api_key,
        "max_tokens": max_tokens,
        "streaming": True,
        "temperature": 0.2,
    }
    if settings.openai_base_url:
        kwargs["base_url"] = settings.openai_base_url
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


def _chunk_text(text: str, size: int) -> list[str]:
    return [text[i : i + size] for i in range(0, len(text), size)]


async def stream_ai_search(request: StreamRequest) -> AsyncIterator[str]:
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

    try:
        # 产品侧需要稳定的 cite 事件：先确定性检索一次
        retrieved = await retrieve_meme_chunks(
            query=safe_query,
            hint_meme_ids=hint_ids,
            mysql_snippets=snippets,
            top_k=settings.retrieval_top_k,
        )
        meme_ids = list(dict.fromkeys(chunk.meme_id for chunk in retrieved))
        yield _format_sse(
            "meta",
            StreamMetaEvent(request_id=request.request_id, retrieved=meme_ids).model_dump(),
        )
        emitted: set[str] = set()
        for chunk in retrieved:
            if chunk.meme_id in emitted:
                continue
            emitted.add(chunk.meme_id)
            yield _format_sse(
                "cite",
                StreamCiteEvent(
                    meme_id=chunk.meme_id,
                    score=chunk.score,
                    title=chunk.title,
                ).model_dump(),
            )

        llm = _build_llm(settings, max_tokens)
        if llm is None:
            fallback = _fallback_answer(safe_query, retrieved)
            for piece in _chunk_text(fallback, 24):
                yield _format_sse("token", StreamTokenEvent(text=piece).model_dump())
            yield _format_sse(
                "done",
                StreamDoneEvent(
                    finish_reason="fallback",
                    usage={"completion": len(fallback)},
                ).model_dump(),
            )
            return

        tool = build_search_meme_tool(
            default_hint_ids=hint_ids,
            default_snippets=snippets,
            default_top_k=settings.retrieval_top_k,
        )
        live_tool = build_search_live_web_tool()
        executor = build_meme_search_agent(llm, [tool, live_tool], max_iterations=4)

        context_block = build_context_block(
            request.context.locale or "zh-CN",
            hint_ids,
            snippets=snippets,
        )
        agent_input = (
            f"{context_block}\n\n"
            f"{wrap_untrusted_user_payload(safe_query, flagged=injection_flag)}\n\n"
            "请先调用 search_meme_knowledge；若结果不足或问题涉及最新热梗，"
            "再调用 search_live_meme_web。基于工具结果用中文回答。"
            "回答中不要出现 meme_id、score 等内部字段，只写用户可读内容。"
        )

        completion_chars = 0
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
                    },
                    "tags": ["meme-search-agent"],
                },
            ):
                kind = event.get("event")
                if kind == "on_tool_end":
                    # Agent 再次检索时补充 cite（去重）
                    output = event.get("data", {}).get("output")
                    raw = output if isinstance(output, str) else getattr(output, "content", "")
                    for chunk in parse_tool_payload(str(raw or "")):
                        if chunk.meme_id in emitted:
                            continue
                        emitted.add(chunk.meme_id)
                        yield _format_sse(
                            "cite",
                            StreamCiteEvent(
                                meme_id=chunk.meme_id,
                                score=chunk.score,
                                title=chunk.title,
                            ).model_dump(),
                        )
                elif kind == "on_chat_model_stream":
                    chunk = event.get("data", {}).get("chunk")
                    text = _extract_stream_text(chunk)
                    if text:
                        completion_chars += len(text)
                        yield _format_sse("token", StreamTokenEvent(text=text).model_dump())
        except Exception as llm_exc:
            logger.exception(
                "LangChain agent failed, fallback answer request_id=%s",
                request.request_id,
            )
            if completion_chars == 0:
                fallback = _fallback_answer(safe_query, retrieved)
                for piece in _chunk_text(fallback, 24):
                    yield _format_sse("token", StreamTokenEvent(text=piece).model_dump())
                yield _format_sse(
                    "done",
                    StreamDoneEvent(
                        finish_reason="fallback_llm_error",
                        usage={"completion": len(fallback)},
                    ).model_dump(),
                )
                return
            yield _format_sse(
                "error",
                StreamErrorEvent(code="llm_error", message=str(llm_exc)).model_dump(),
            )
            return

        yield _format_sse(
            "done",
            StreamDoneEvent(
                finish_reason="stop",
                usage={"completion": completion_chars},
            ).model_dump(),
        )
    except Exception as exc:
        logger.exception("stream failed request_id=%s", request.request_id)
        yield _format_sse(
            "error",
            StreamErrorEvent(code="agent_error", message=str(exc)).model_dump(),
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
