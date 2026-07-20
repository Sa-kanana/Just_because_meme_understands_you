import json
import logging
from collections.abc import AsyncIterator

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from langchain_openai import ChatOpenAI

from app.agents.prompts import (
    SYSTEM_PROMPT,
    build_context_block,
    build_retrieval_block,
)
from app.core.settings import Settings, get_settings
from app.retrieval.repository import RetrievedChunk, similarity_search
from app.schemas.stream import (
    StreamCiteEvent,
    StreamDoneEvent,
    StreamErrorEvent,
    StreamMetaEvent,
    StreamRequest,
    StreamTokenEvent,
)

logger = logging.getLogger(__name__)


def _configure_langsmith(settings: Settings) -> None:
    if not settings.langchain_tracing_v2:
        return
    import os

    os.environ["LANGCHAIN_TRACING_V2"] = "true"
    if settings.langchain_api_key:
        os.environ["LANGCHAIN_API_KEY"] = settings.langchain_api_key
    os.environ["LANGCHAIN_PROJECT"] = settings.langchain_project


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


async def stream_ai_search(request: StreamRequest) -> AsyncIterator[str]:
    settings = get_settings()
    _configure_langsmith(settings)
    max_tokens = min(request.max_tokens, settings.max_output_tokens)

    try:
        retrieved = await similarity_search(request.query, settings.retrieval_top_k)
        meme_ids = list(dict.fromkeys(chunk.meme_id for chunk in retrieved))
        yield _format_sse("meta", StreamMetaEvent(request_id=request.request_id, retrieved=meme_ids).model_dump())

        for chunk in retrieved:
            yield _format_sse(
                "cite",
                StreamCiteEvent(
                    meme_id=chunk.meme_id,
                    score=chunk.score,
                    title=chunk.title,
                ).model_dump(),
            )

        async for line in _generate_answer(request, retrieved, settings, max_tokens):
            yield line
    except Exception as exc:
        logger.exception("stream failed request_id=%s", request.request_id)
        yield _format_sse(
            "error",
            StreamErrorEvent(code="agent_error", message=str(exc)).model_dump(),
        )


async def _generate_answer(
    request: StreamRequest,
    retrieved: list[RetrievedChunk],
    settings: Settings,
    max_tokens: int,
) -> AsyncIterator[str]:
    retrieval_tuple = [(c.meme_id, c.content, c.score) for c in retrieved]
    context_block = build_context_block(
        request.context.locale,
        request.context.hint_meme_ids,
    )
    retrieval_block = build_retrieval_block(retrieval_tuple)
    user_prompt = (
        f"{context_block}\n\n{retrieval_block}\n\n"
        f"<user_query>\n{request.query}\n</user_query>"
    )

    llm = _build_llm(settings, max_tokens)
    if llm is None:
        fallback = _fallback_answer(request.query, retrieved)
        for piece in _chunk_text(fallback, 24):
            yield _format_sse("token", StreamTokenEvent(text=piece).model_dump())
        yield _format_sse(
            "done",
            StreamDoneEvent(finish_reason="fallback", usage={"completion": len(fallback)}).model_dump(),
        )
        return

    messages = [SystemMessage(content=SYSTEM_PROMPT)]
    for msg in request.messages:
        if msg.role == "user":
            messages.append(HumanMessage(content=msg.content))
        elif msg.role == "assistant":
            messages.append(AIMessage(content=msg.content))
    messages.append(HumanMessage(content=user_prompt))

    completion_chars = 0
    async for chunk in llm.astream(messages):
        text = chunk.content if isinstance(chunk.content, str) else ""
        if not text:
            continue
        completion_chars += len(text)
        yield _format_sse("token", StreamTokenEvent(text=text).model_dump())

    yield _format_sse(
        "done",
        StreamDoneEvent(
            finish_reason="stop",
            usage={"completion": completion_chars},
        ).model_dump(),
    )


def _fallback_answer(query: str, retrieved: list[RetrievedChunk]) -> str:
    if not retrieved:
        return f"暂未检索到与「{query}」相关的梗。请尝试换关键词，或到首页浏览热门内容。"
    top = retrieved[0]
    return (
        f"（离线模式）找到 {len(retrieved)} 条相关内容。"
        f"最相关的是 meme_id={top.meme_id}：{top.content[:200]}…"
    )


def _chunk_text(text: str, size: int) -> list[str]:
    return [text[i : i + size] for i in range(0, len(text), size)]
