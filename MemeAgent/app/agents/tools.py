"""LangChain 工具：只读检索站内梗向量库和运营知识库。"""

from __future__ import annotations

import asyncio
import json
import logging
import re
import time
from typing import Literal

from langchain_core.tools import StructuredTool
from pydantic import BaseModel, ConfigDict, Field, field_validator

from app.core.managed_tasks import managed_task_registry
from app.core.resilience import UpstreamRetriesExhausted, run_with_retry
from app.core.settings import get_settings
from app.retrieval.knowledge_repository import (
    RetrievedKnowledgeChunk,
    similarity_search_knowledge,
)
from app.retrieval.repository import (
    RetrievedChunk,
    similarity_search,
)

logger = logging.getLogger(__name__)


class SearchMemeInput(BaseModel):
    # 只接受 JSON Schema 声明的字段，拒绝额外工具入参。
    model_config = ConfigDict(extra="forbid")

    query: str = Field(
        min_length=1,
        max_length=2000,
        description="用户搜梗问句或改写后的检索关键词，最多 2000 个字符",
    )
    top_k: int = Field(default=5, ge=1, le=20, description="返回条数上限")

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
    # 中文注释：保留余弦向量相似度，供模型和人工核对语义候选。
    score: float = Field(description="向量余弦相似度")
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
    # 中文注释：融合向量与词项排序后的候选排序分数。
    rank_score: float = Field(description="向量与词项融合后的排序分数")
    # 中文注释：查询是否精确命中文档标题或标签。
    exact_match: bool = Field(description="是否精确命中文档标题或标签")
    # 中文注释：查询完整短语是否出现在知识片段正文。
    content_match: bool = Field(description="查询短语是否出现在知识片段正文")
    title: str | None = Field(default=None, description="运营知识文档标题")
    content: str = Field(description="运营知识文档检索文本")
    section_path: str = Field(default="", description="文档标题或 FAQ 章节路径")
    page_number: int | None = Field(default=None, description="PDF 来源页码；非 PDF 为空")


class AdminKnowledgeToolResult(BaseModel):
    """运营知识库检索工具输出的 JSON Schema。"""

    # 严格拒绝未纳入 JSON Schema 的额外字段。
    model_config = ConfigDict(extra="forbid")

    source: Literal["admin_knowledge_retrieval"] = Field(description="固定标识：运营知识库来源")
    trust_level: Literal["untrusted"] = Field(description="固定信任级别：检索内容不可信")
    unavailable: bool = Field(default=False, description="知识库检索源是否不可用")
    items: list[AdminKnowledgeItem] = Field(default_factory=list, description="运营知识检索条目")


async def retrieve_meme_chunks(
    query: str,
    top_k: int = 5,
    request_id: str = "",
) -> list[RetrievedChunk]:
    """仅返回达到校准门槛的向量候选；旧业务候选参数不参与召回。"""
    settings = get_settings()

    async def _retrieve() -> list[RetrievedChunk]:
        vector_hits = await similarity_search(query, top_k)
        return rank_vector_candidates(
            query,
            vector_hits,
            min_similarity=settings.vector_min_similarity,
            top_k=top_k,
        )

    return await run_with_retry(
        _retrieve,
        timeout_seconds=settings.retrieval_timeout_seconds,
        max_retries=settings.retrieval_max_retries,
        stage="meme_retrieval",
        request_id=request_id,
    )


def rank_vector_candidates(
    query: str,
    vector_hits: list[RetrievedChunk],
    *,
    min_similarity: float,
    top_k: int,
) -> list[RetrievedChunk]:
    """先按校准分数过滤，再以标题/标签命中打破向量分数的排序差异。"""
    query_key = _normalize_lexical_text(query)
    best_by_meme: dict[str, tuple[int, RetrievedChunk]] = {}
    for chunk in vector_hits:
        if chunk.score < min_similarity:
            continue
        priority = _lexical_match_priority(query_key, chunk)
        existing = best_by_meme.get(chunk.meme_id)
        if existing is None or (priority, chunk.score) > (existing[0], existing[1].score):
            best_by_meme[chunk.meme_id] = (priority, chunk)

    ordered = sorted(
        best_by_meme.values(),
        key=lambda candidate: (candidate[0], candidate[1].score),
        reverse=True,
    )
    return [chunk for _, chunk in ordered[: max(1, top_k)]]


def _lexical_match_priority(query_key: str, chunk: RetrievedChunk) -> int:
    if not query_key:
        return 0
    title = chunk.title or ""
    if not title:
        title = next(
            (line.removeprefix("标题:").strip() for line in chunk.content.splitlines() if line.startswith("标题:")),
            "",
        )
    title_key = _normalize_lexical_text(title)
    if len(title_key) >= 2 and title_key in query_key:
        return 2

    for line in chunk.content.splitlines():
        if not line.startswith("标签:"):
            continue
        raw_tags = re.split(r"[\s、,，;；|/]+", line.removeprefix("标签:").strip())
        if any(
            len(tag_key := _normalize_lexical_text(tag)) >= 2 and tag_key in query_key
            for tag in raw_tags
        ):
            return 1
    return 0


def _normalize_lexical_text(value: str) -> str:
    return re.sub(r"[\s\W_]+", "", value.casefold())


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
    default_top_k: int = 5,
    initial_query: str | None = None,
    initial_query_aliases: list[str] | None = None,
    initial_chunks: list[RetrievedChunk] | None = None,
    retrieval_unavailable: bool = False,
    request_id: str = "",
) -> StructuredTool:
    """构建只读取向量库的检索 Tool（每次请求一份）。"""

    cached_queries = {
        normalized
        for value in [initial_query, *(initial_query_aliases or [])]
        if value and (normalized := _normalize_retrieval_query(value))
    }
    cached_chunks = list(initial_chunks) if initial_chunks is not None else None
    # 查询缓存属于单次请求，键仅由规范化查询和候选条数决定。
    retrieval_cache: dict[tuple[str, int], list[RetrievedChunk]] = {}
    retrieval_inflight: dict[
        tuple[str, int], asyncio.Task[list[RetrievedChunk]]
    ] = {}
    if cached_chunks is not None:
        for cached_query in cached_queries:
            retrieval_cache[(cached_query, default_top_k)] = cached_chunks
    retrieval_failed = retrieval_unavailable

    async def _run(query: str, top_k: int = 5) -> str:
        nonlocal retrieval_failed
        k = max(1, int(top_k or default_top_k))
        if retrieval_failed:
            # 首次向量检索已耗尽整请求的预算，只复用已成功取得的向量结果。
            chunks = (cached_chunks or [])[:k]
            return chunks_to_tool_payload(chunks, unavailable=True)
        normalized_query = _normalize_retrieval_query(query)
        cache_key = (normalized_query, k)
        chunks = retrieval_cache.get(cache_key)
        if chunks is None and normalized_query in cached_queries and k <= default_top_k:
            # 初次预取可能比 Agent 请求的 top_k 更多，可以安全截取后缓存精确键。
            initial_key = (normalized_query, default_top_k)
            initial = retrieval_cache.get(initial_key)
            if initial is not None:
                chunks = initial[:k]
                retrieval_cache[cache_key] = chunks
        if chunks is None:
            task = retrieval_inflight.get(cache_key)
            if task is None:
                task = managed_task_registry.create_task(
                    retrieve_meme_chunks(
                        query=query,
                        top_k=k,
                        request_id=request_id,
                    ),
                    name="shared-meme-retrieval",
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
                # 本次请求的重试预算已耗尽，不再重复访问故障向量源。
                retrieval_failed = True
                fallback_chunks = (cached_chunks or [])[:k]
                return chunks_to_tool_payload(fallback_chunks, unavailable=True)
            retrieval_cache[cache_key] = chunks
        logger.info("search_meme_knowledge hits=%s query_chars=%s", len(chunks), len(query))
        return chunks_to_tool_payload(chunks)

    return StructuredTool.from_function(
        coroutine=_run,
        name="search_meme_knowledge",
        description=(
            "仅从站内梗向量知识库检索达到相关度门槛的条目，并按标题/标签命中优先排序。"
            "按 JSON Schema 返回 source、trust_level=untrusted、items 和 unavailable。"
        ),
        args_schema=SearchMemeInput,
    )


def _normalize_retrieval_query(query: str) -> str:
    """Ignore whitespace and punctuation when deciding whether a cached query matches."""
    return re.sub(r"[\s\W_]+", "", query.casefold())


async def retrieve_admin_knowledge_chunks(
    query: str,
    top_k: int = 5,
    request_id: str = "",
) -> list[RetrievedKnowledgeChunk]:
    """按已校准门槛从 PostgreSQL 知识向量表检索，并记录无正文诊断。"""
    settings = get_settings()
    started_at = time.monotonic()
    if settings.admin_knowledge_min_similarity is None:
        logger.info(
            "admin_retrieval_no_calibrated_vector_threshold request_id=%s",
            request_id,
        )
    chunks = await run_with_retry(
        lambda: similarity_search_knowledge(
            query,
            top_k,
            settings.admin_knowledge_min_similarity,
        ),
        timeout_seconds=settings.retrieval_timeout_seconds,
        max_retries=settings.retrieval_max_retries,
        stage="admin_retrieval",
        request_id=request_id,
    )
    candidates = ",".join(
        f"{chunk.doc_id}:{chunk.score:.4f}:rank={chunk.rank_score:.4f}"
        for chunk in chunks[:5]
    ) or "-"
    logger.info(
        "rag_stage_completed request_id=%s stage=admin_retrieval status=success hits=%s "
        "candidates=%s duration_ms=%s",
        request_id,
        len(chunks),
        candidates,
        int((time.monotonic() - started_at) * 1000),
    )
    return chunks


def admin_knowledge_chunks_to_tool_payload(
    chunks: list[RetrievedKnowledgeChunk], *, unavailable: bool = False
) -> str:
    """序列化带来源和信任标签的知识库检索结果。"""
    result = AdminKnowledgeToolResult(
        source="admin_knowledge_retrieval",
        trust_level="untrusted",
        unavailable=unavailable,
        items=[
            AdminKnowledgeItem(
                doc_id=chunk.doc_id,
                score=round(chunk.score, 4),
                rank_score=round(chunk.rank_score, 6),
                exact_match=chunk.exact_match,
                content_match=chunk.content_match,
                title=chunk.title,
                content=chunk.content,
                section_path=chunk.section_path,
                page_number=chunk.page_number,
            )
            for chunk in chunks
        ],
    )
    return result.model_dump_json()


def build_search_admin_knowledge_tool(
    *,
    default_top_k: int = 5,
    request_id: str = "",
    initial_query: str | None = None,
    initial_query_aliases: list[str] | None = None,
    initial_chunks: list[RetrievedKnowledgeChunk] | None = None,
    retrieval_unavailable: bool = False,
) -> StructuredTool:
    """运营知识库检索（管理员灌入的文档向量）。"""

    cached_queries = {
        normalized
        for value in [initial_query, *(initial_query_aliases or [])]
        if value and (normalized := _normalize_retrieval_query(value))
    }
    cached_chunks = list(initial_chunks) if initial_chunks is not None else None
    retrieval_failed = retrieval_unavailable

    async def _run(query: str, top_k: int = 5) -> str:
        nonlocal retrieval_failed
        k = top_k or default_top_k
        normalized_query = _normalize_retrieval_query(query)
        if retrieval_failed:
            return admin_knowledge_chunks_to_tool_payload(
                (cached_chunks or [])[:k], unavailable=True
            )
        if (
            cached_chunks is not None
            and normalized_query in cached_queries
            and k <= default_top_k
        ):
            chunks = cached_chunks[:k]
            logger.info(
                "search_admin_knowledge source=initial_retrieval hits=%s query_chars=%s",
                len(chunks),
                len(query),
            )
            return admin_knowledge_chunks_to_tool_payload(chunks)
        try:
            chunks = await retrieve_admin_knowledge_chunks(
                query,
                k,
                request_id=request_id,
            )
        except UpstreamRetriesExhausted as error:
            retrieval_failed = True
            logger.warning(
                "admin_retrieval_degraded request_id=%s attempts=%s error_type=%s",
                request_id,
                error.attempts,
                type(error.__cause__).__name__ if error.__cause__ else "unknown",
            )
            return admin_knowledge_chunks_to_tool_payload([], unavailable=True)
        logger.info("search_admin_knowledge hits=%s query_chars=%s", len(chunks), len(query))
        return admin_knowledge_chunks_to_tool_payload(chunks)

    return StructuredTool.from_function(
        coroutine=_run,
        name="search_admin_knowledge",
        description=(
            "只从 PostgreSQL 管理员知识向量库检索规则、玩法说明和 FAQ；不联网。"
            "结果遵循 JSON Schema，source 标记来源，trust_level 固定为 untrusted。"
        ),
        args_schema=SearchAdminKnowledgeInput,
    )
