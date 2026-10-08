"""LangChain tools：站内梗检索 + Firecrawl 实时网页热梗。"""

from __future__ import annotations

import asyncio
import json
import logging
import re
from typing import Any, Literal

from langchain_core.tools import StructuredTool
from pydantic import BaseModel, ConfigDict, Field, field_validator

from app.core.resilience import UpstreamRetriesExhausted, run_with_retry
from app.core.settings import get_settings
from app.crawl.service import crawl_hot_memes
from app.retrieval.knowledge_repository import similarity_search_knowledge
from app.retrieval.repository import (
    RetrievedChunk,
    fetch_chunks_by_meme_ids,
    merge_retrieved,
    similarity_search,
)
from app.schemas.crawl import CrawlHotMemesRequest
from app.security.sanitize import sanitize_hint_meme_ids, sanitize_user_text

logger = logging.getLogger(__name__)


class SearchMemeInput(BaseModel):
    # 只接受 JSON Schema 声明的字段，拒绝额外工具入参。
    model_config = ConfigDict(extra="forbid")

    query: str = Field(
        min_length=1,
        max_length=2000,
        description="用户搜梗问句或改写后的检索关键词，最多 2000 个字符",
    )
    hint_meme_ids: list[str] = Field(
        default_factory=list,
        description="Java 预检索给出的候选 meme_id 列表，可为空",
    )
    top_k: int = Field(default=5, ge=1, le=20, description="返回条数上限")

    @field_validator("query", mode="before")
    @classmethod
    def trim_query(cls, value):
        return value.strip() if isinstance(value, str) else value

    @field_validator("hint_meme_ids", mode="before")
    @classmethod
    def coerce_hint_ids(cls, value):
        if value is None or value == "" or value in ("无", "none", "null", "[]"):
            return []
        if isinstance(value, str):
            text = value.strip()
            if not text or text in ("无", "none", "null"):
                return []
            if text.startswith("["):
                try:
                    parsed = json.loads(text)
                    if isinstance(parsed, list):
                        return [str(x).strip() for x in parsed if str(x).strip()]
                except json.JSONDecodeError:
                    pass
            return [part.strip() for part in text.split(",") if part.strip()]
        if isinstance(value, list):
            return [str(x).strip() for x in value if str(x).strip()]
        return [str(value).strip()]


class SearchLiveWebInput(BaseModel):
    # 只接受 JSON Schema 声明的字段，拒绝额外工具入参。
    model_config = ConfigDict(extra="forbid")

    query: str = Field(
        min_length=1,
        max_length=500,
        description="需要联网补充的热梗 / 流行语关键词，最多 500 个字符",
    )
    limit: int = Field(default=5, ge=1, le=10, description="返回条数")

    @field_validator("query", mode="before")
    @classmethod
    def trim_query(cls, value):
        return value.strip() if isinstance(value, str) else value


class SearchAdminKnowledgeInput(BaseModel):
    # 只接受 JSON Schema 声明的字段，拒绝额外工具入参。
    model_config = ConfigDict(extra="forbid")

    query: str = Field(
        min_length=1,
        max_length=500,
        description="需要从运营知识库检索的问句或关键词，最多 500 个字符",
    )
    top_k: int = Field(default=5, ge=1, le=20, description="返回条数上限")

    @field_validator("query", mode="before")
    @classmethod
    def trim_query(cls, value):
        return value.strip() if isinstance(value, str) else value


class MemeRetrievalItem(BaseModel):
    """站内梗检索结果条目的 JSON Schema。"""

    # 严格拒绝未纳入 JSON Schema 的额外字段。
    model_config = ConfigDict(extra="forbid")

    meme_id: str = Field(description="站内梗条目 ID；仅用于服务端引用关联")
    score: float = Field(description="检索相关度分数")
    title: str | None = Field(default=None, description="站内梗标题")
    content: str = Field(description="站内梗的检索文本内容")


class MemeRetrievalToolResult(BaseModel):
    """站内梗检索工具输出的 JSON Schema。"""

    # 严格拒绝未纳入 JSON Schema 的额外字段。
    model_config = ConfigDict(extra="forbid")

    source: Literal["meme_retrieval"] = Field(description="固定标识：站内梗检索来源")
    trust_level: Literal["untrusted"] = Field(description="固定信任级别：检索内容不可信")
    unavailable: bool = Field(default=False, description="检索源是否不可用")
    items: list[MemeRetrievalItem] = Field(default_factory=list, description="站内梗检索条目")


class AdminKnowledgeItem(BaseModel):
    """运营知识库检索结果条目的 JSON Schema。"""

    # 严格拒绝未纳入 JSON Schema 的额外字段。
    model_config = ConfigDict(extra="forbid")

    doc_id: str = Field(description="运营知识文档 ID")
    score: float = Field(description="检索相关度分数")
    title: str | None = Field(default=None, description="运营知识文档标题")
    content: str = Field(description="运营知识文档检索文本")


class AdminKnowledgeToolResult(BaseModel):
    """运营知识库检索工具输出的 JSON Schema。"""

    # 严格拒绝未纳入 JSON Schema 的额外字段。
    model_config = ConfigDict(extra="forbid")

    source: Literal["admin_knowledge_retrieval"] = Field(description="固定标识：运营知识库来源")
    trust_level: Literal["untrusted"] = Field(description="固定信任级别：检索内容不可信")
    unavailable: bool = Field(default=False, description="知识库检索源是否不可用")
    items: list[AdminKnowledgeItem] = Field(default_factory=list, description="运营知识检索条目")


class LiveWebItem(BaseModel):
    """联网检索结果条目的 JSON Schema。"""

    # 严格拒绝未纳入 JSON Schema 的额外字段。
    model_config = ConfigDict(extra="forbid")

    title: str = Field(description="公开网页热梗标题")
    introduction: str = Field(description="公开网页热梗摘要")
    source_url: str = Field(description="公开网页来源链接")
    tags: list[str] = Field(default_factory=list, description="公开网页提供的标签")


class LiveWebToolResult(BaseModel):
    """联网检索工具输出的 JSON Schema。"""

    # 严格拒绝未纳入 JSON Schema 的额外字段。
    model_config = ConfigDict(extra="forbid")

    source: Literal["live_web_retrieval"] = Field(description="固定标识：公开网页检索来源")
    trust_level: Literal["untrusted"] = Field(description="固定信任级别：网页内容不可信")
    unavailable: bool = Field(default=False, description="联网检索源是否不可用")
    disabled: bool = Field(default=False, description="联网检索功能是否已禁用")
    items: list[LiveWebItem] = Field(default_factory=list, description="公开网页检索条目")


async def retrieve_meme_chunks(
    query: str,
    hint_meme_ids: list[str] | None = None,
    mysql_snippets: list[dict[str, Any]] | None = None,
    top_k: int = 5,
    request_id: str = "",
) -> list[RetrievedChunk]:
    """供 Agent tool 与 SSE 编排共用的检索实现。"""
    settings = get_settings()
    hints = [str(x).strip() for x in (hint_meme_ids or []) if str(x).strip()]

    async def _retrieve() -> list[RetrievedChunk]:
        vector_hits = await similarity_search(query, top_k)
        hint_hits = await fetch_chunks_by_meme_ids(hints) if hints else []
        business_chunks = build_business_candidate_chunks(mysql_snippets or [])
        return merge_retrieved(vector_hits, hint_hits, business_chunks, top_k)

    return await run_with_retry(
        _retrieve,
        timeout_seconds=settings.retrieval_timeout_seconds,
        max_retries=settings.retrieval_max_retries,
        stage="meme_retrieval",
        request_id=request_id,
    )


def build_business_candidate_chunks(snippets: list[dict[str, Any]]) -> list[RetrievedChunk]:
    """Convert only the public Java snippet allowlist into safe fallback candidates."""
    chunks: list[RetrievedChunk] = []
    seen: set[str] = set()
    for item in snippets[:20]:
        if not isinstance(item, dict):
            continue
        meme_id = str(item.get("meme_id") or item.get("memeId") or "").strip()
        if not sanitize_hint_meme_ids([meme_id]) or meme_id in seen:
            continue
        seen.add(meme_id)
        title = sanitize_user_text(str(item.get("title") or ""), max_len=200)
        title = title.replace("\r", " ").replace("\n", " ").strip()
        intro = sanitize_user_text(str(item.get("introduction") or ""), max_len=800)
        raw_tags = item.get("tags")
        tag_text = ""
        if isinstance(raw_tags, list):
            tag_text = " ".join(
                sanitize_user_text(str(tag), max_len=40).replace("\n", " ").strip()
                for tag in raw_tags[:10]
                if str(tag).strip()
            )
        content = f"标题: {title}\n标签: {tag_text}\n\n{intro}".strip()
        if not title and not intro and not tag_text:
            continue
        chunks.append(
            RetrievedChunk(
                meme_id=meme_id,
                content=content,
                score=0.45,
                title=title or None,
            )
        )
    return chunks


def chunks_to_tool_payload(
    chunks: list[RetrievedChunk], *, unavailable: bool = False
) -> str:
    """序列化并校验带来源和信任标记的站内梗检索结果。"""
    result = MemeRetrievalToolResult(
        source="meme_retrieval",
        trust_level="untrusted",
        unavailable=unavailable,
        items=[
            MemeRetrievalItem(
                meme_id=chunk.meme_id,
                score=round(chunk.score, 4),
                title=chunk.title,
                content=chunk.content,
            )
            for chunk in chunks
        ],
    )
    return result.model_dump_json()


def parse_tool_payload(raw: str) -> list[RetrievedChunk]:
    try:
        data = json.loads(raw)
    except json.JSONDecodeError:
        return []
    if isinstance(data, dict):
        data = data.get("items")
    if not isinstance(data, list):
        return []
    out: list[RetrievedChunk] = []
    for item in data:
        if not isinstance(item, dict):
            continue
        meme_id = str(item.get("meme_id") or "").strip()
        if not meme_id:
            continue
        out.append(
            RetrievedChunk(
                meme_id=meme_id,
                content=str(item.get("content") or ""),
                score=float(item.get("score") or 0),
                title=(str(item["title"]) if item.get("title") is not None else None),
            )
        )
    return out


def build_search_meme_tool(
    *,
    default_hint_ids: list[str] | None = None,
    default_snippets: list[dict[str, Any]] | None = None,
    default_top_k: int = 5,
    initial_query: str | None = None,
    initial_chunks: list[RetrievedChunk] | None = None,
    retrieval_unavailable: bool = False,
    request_id: str = "",
) -> StructuredTool:
    """构建带闭包上下文的检索 Tool（每次请求一份）。"""

    hints = list(default_hint_ids or [])
    snippets = list(default_snippets or [])
    cached_query = _normalize_retrieval_query(initial_query or "")
    cached_chunks = list(initial_chunks) if initial_chunks is not None else None
    # 缓存只属于当前 Agent 请求，键包含检索文本、候选梗 ID 和条数。
    retrieval_cache: dict[tuple[str, tuple[str, ...], int], list[RetrievedChunk]] = {}
    retrieval_inflight: dict[
        tuple[str, tuple[str, ...], int], asyncio.Task[list[RetrievedChunk]]
    ] = {}
    if initial_query is not None and cached_chunks is not None:
        retrieval_cache[(cached_query, tuple(hints), default_top_k)] = cached_chunks
    retrieval_failed = retrieval_unavailable

    async def _run(query: str, hint_meme_ids: list[str] | None = None, top_k: int = 5) -> str:
        nonlocal retrieval_failed
        merged_hints = list(hint_meme_ids or []) or hints
        k = max(1, int(top_k or default_top_k))
        if retrieval_failed:
            # 首次检索已耗尽整请求的预算；只返回 Java 候选，不再次请求同一故障源。
            chunks = (cached_chunks or build_business_candidate_chunks(snippets))[:k]
            return chunks_to_tool_payload(chunks, unavailable=True)
        normalized_query = _normalize_retrieval_query(query)
        cache_key = (normalized_query, tuple(merged_hints), k)
        chunks = retrieval_cache.get(cache_key)
        if chunks is None and normalized_query == cached_query and k <= default_top_k:
            # 初次预取可能比 Agent 请求的 top_k 更多，可以安全截取后缓存精确键。
            initial_key = (cached_query, tuple(hints), default_top_k)
            if tuple(merged_hints) == tuple(hints):
                initial = retrieval_cache.get(initial_key)
                if initial is not None:
                    chunks = initial[:k]
                    retrieval_cache[cache_key] = chunks
        if chunks is None:
            task = retrieval_inflight.get(cache_key)
            if task is None:
                task = asyncio.create_task(
                    retrieve_meme_chunks(
                        query=query,
                        hint_meme_ids=merged_hints,
                        mysql_snippets=snippets,
                        top_k=k,
                        request_id=request_id,
                    )
                )
                retrieval_inflight[cache_key] = task

                def remember_result(completed: asyncio.Task[list[RetrievedChunk]]) -> None:
                    try:
                        # 只缓存本请求内成功完成的结果；查询异常不会污染缓存。
                        retrieval_cache[cache_key] = list(completed.result())
                    except (asyncio.CancelledError, Exception):
                        pass
                    finally:
                        if retrieval_inflight.get(cache_key) is completed:
                            retrieval_inflight.pop(cache_key, None)

                task.add_done_callback(remember_result)
            # 同请求的并发相同检索共用一次请求；客户端取消不会取消其他调用者的检索。
            try:
                chunks = list(await asyncio.shield(task))
            except UpstreamRetriesExhausted:
                # 本次请求的重试预算已耗尽，后续工具调用回退到已知候选，不再重复打故障源。
                retrieval_failed = True
                fallback_chunks = (cached_chunks or build_business_candidate_chunks(snippets))[:k]
                return chunks_to_tool_payload(fallback_chunks, unavailable=True)
            retrieval_cache[cache_key] = chunks
        logger.info("search_meme_knowledge hits=%s query_chars=%s", len(chunks), len(query))
        return chunks_to_tool_payload(chunks)

    return StructuredTool.from_function(
        coroutine=_run,
        name="search_meme_knowledge",
        description=(
            "检索站内梗知识库，融合向量检索、候选 meme_id 与 Java 提供的公开梗摘要。"
            "按 JSON Schema 返回 source、trust_level=untrusted、items 和 unavailable。"
        ),
        args_schema=SearchMemeInput,
    )


def _normalize_retrieval_query(query: str) -> str:
    """Ignore whitespace and punctuation when deciding whether a cached query matches."""
    return re.sub(r"[\s\W_]+", "", query.casefold())


def build_search_live_web_tool(*, request_id: str = "") -> StructuredTool:
    """Firecrawl 联网补强：站内命中不足或用户问「最新/今天」热梗时使用。"""

    async def _run(query: str, limit: int = 5) -> str:
        settings = get_settings()
        if not settings.firecrawl_enabled:
            return LiveWebToolResult(
                source="live_web_retrieval",
                trust_level="untrusted",
                disabled=True,
            ).model_dump_json()
        request = CrawlHotMemesRequest(query=query, limit=limit, include_markdown=True)
        try:
            resp = await run_with_retry(
                lambda: crawl_hot_memes(request, settings=settings),
                timeout_seconds=settings.live_web_timeout_seconds,
                max_retries=0,
                stage="live_web",
                request_id=request_id,
            )
        except UpstreamRetriesExhausted as error:
            logger.warning(
                "live_web_degraded request_id=%s attempts=%s error_type=%s",
                request_id,
                error.attempts,
                type(error.__cause__).__name__ if error.__cause__ else "unknown",
            )
            return LiveWebToolResult(
                source="live_web_retrieval",
                trust_level="untrusted",
                unavailable=True,
            ).model_dump_json()
        result = LiveWebToolResult(
            source="live_web_retrieval",
            trust_level="untrusted",
            items=[
                LiveWebItem(
                    title=c.title,
                    introduction=c.introduction[:800],
                    source_url=c.source_url,
                    tags=c.tags,
                )
                for c in resp.candidates
            ],
        )
        logger.info("search_live_meme_web hits=%s query_chars=%s", len(result.items), len(query))
        return result.model_dump_json()

    return StructuredTool.from_function(
        coroutine=_run,
        name="search_live_meme_web",
        description=(
            "使用 Firecrawl 检索公开网页并返回热梗摘要与来源链接；不返回站内 meme_id。"
            "结果遵循 JSON Schema，source 标记来源，trust_level 固定为 untrusted。"
        ),
        args_schema=SearchLiveWebInput,
    )


def build_search_admin_knowledge_tool(
    *, default_top_k: int = 5, request_id: str = ""
) -> StructuredTool:
    """运营知识库检索（管理员灌入的文档向量）。"""

    async def _run(query: str, top_k: int = 5) -> str:
        settings = get_settings()
        k = top_k or default_top_k
        try:
            chunks = await run_with_retry(
                lambda: similarity_search_knowledge(query, k),
                timeout_seconds=settings.retrieval_timeout_seconds,
                max_retries=settings.retrieval_max_retries,
                stage="admin_retrieval",
                request_id=request_id,
            )
        except UpstreamRetriesExhausted as error:
            logger.warning(
                "admin_retrieval_degraded request_id=%s attempts=%s error_type=%s",
                request_id,
                error.attempts,
                type(error.__cause__).__name__ if error.__cause__ else "unknown",
            )
            return AdminKnowledgeToolResult(
                source="admin_knowledge_retrieval",
                trust_level="untrusted",
                unavailable=True,
            ).model_dump_json()
        result = AdminKnowledgeToolResult(
            source="admin_knowledge_retrieval",
            trust_level="untrusted",
            items=[
                AdminKnowledgeItem(
                    doc_id=chunk.doc_id,
                    score=round(chunk.score, 4),
                    title=chunk.title,
                    content=chunk.content,
                )
                for chunk in chunks
            ],
        )
        logger.info("search_admin_knowledge hits=%s query_chars=%s", len(result.items), len(query))
        return result.model_dump_json()

    return StructuredTool.from_function(
        coroutine=_run,
        name="search_admin_knowledge",
        description=(
            "检索管理员维护的站内运营知识文档，包括规则、玩法说明和 FAQ；不返回梗条目 meme_id。"
            "结果遵循 JSON Schema，source 标记来源，trust_level 固定为 untrusted。"
        ),
        args_schema=SearchAdminKnowledgeInput,
    )
