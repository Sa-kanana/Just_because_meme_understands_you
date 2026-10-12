"""LangChain 工具：只读检索站内梗向量库和运营知识库。"""

from __future__ import annotations

import asyncio
import logging
import re
import time
from collections.abc import Callable

from langchain_core.tools import StructuredTool
from langsmith import traceable
from pydantic import BaseModel, ConfigDict, Field, field_validator

from app.core.managed_tasks import managed_task_registry
from app.core.resilience import UpstreamRetriesExhausted, run_with_retry
from app.core.settings import get_settings
from app.retrieval.knowledge_repository import (
    RetrievedKnowledgeChunk,
    similarity_search_knowledge,
)
from app.retrieval.repository import (
    MAX_CHUNKS_PER_MEME,
    RetrievedChunk,
    similarity_search,
)
from app.retrieval.score_filter import rank_admin_knowledge_candidates, rank_meme_candidates

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


@traceable(name="meme_vector_retriever", run_type="retriever")
async def retrieve_meme_candidates(
    query: str,
    candidate_limit: int,
    request_id: str = "",
) -> list[RetrievedChunk]:
    """只负责获取有界向量候选；过滤和梗 ID 去重留给 Score Filter。"""
    settings = get_settings()
    return await run_with_retry(
        lambda: similarity_search(query, candidate_limit),
        timeout_seconds=settings.retrieval_timeout_seconds,
        max_retries=settings.retrieval_max_retries,
        stage="meme_retrieval",
        request_id=request_id,
    )


async def retrieve_meme_chunks(
    query: str,
    top_k: int = 5,
    request_id: str = "",
) -> list[RetrievedChunk]:
    """按候选预算检索后，单独执行向量阈值过滤、排序与梗 ID 去重。"""
    settings = get_settings()
    candidate_limit = max(20, max(1, top_k) * MAX_CHUNKS_PER_MEME)
    vector_candidates = await retrieve_meme_candidates(
        query,
        candidate_limit,
        request_id=request_id,
    )
    return rank_meme_candidates(
        query,
        vector_candidates,
        min_similarity=settings.vector_min_similarity,
        top_k=top_k,
    )


def rank_vector_candidates(
    query: str,
    vector_hits: list[RetrievedChunk],
    *,
    min_similarity: float,
    top_k: int,
) -> list[RetrievedChunk]:
    """兼容旧评估入口；实际排序统一转发到共享 Score Filter。"""
    return rank_meme_candidates(
        query,
        vector_hits,
        min_similarity=min_similarity,
        top_k=top_k,
    )


def format_meme_tool_result(
    chunks: list[RetrievedChunk], *, unavailable: bool = False
) -> str:
    """以可读纯文本返回梗库证据，不暴露 ID 或相关度分数。"""
    return _format_readable_tool_result(
        "站内梗库",
        [
            (chunk.title or "未提供标题", _knowledge_content_for_model(chunk.content), "", None)
            for chunk in chunks
        ],
        unavailable=unavailable,
    )


def format_admin_knowledge_tool_result(
    chunks: list[RetrievedKnowledgeChunk], *, unavailable: bool = False
) -> str:
    """以可读纯文本返回管理员知识证据，不暴露内部 ID 或分数。"""
    return _format_readable_tool_result(
        "管理员知识库",
        [
            (
                chunk.title or "未提供标题",
                _knowledge_content_for_model(chunk.content),
                chunk.section_path,
                chunk.page_number,
            )
            for chunk in chunks
        ],
        unavailable=unavailable,
    )


def _format_readable_tool_result(
    source_name: str,
    documents: list[tuple[str, str, str, int | None]],
    *,
    unavailable: bool,
) -> str:
    """统一格式化工具文本，不输出业务层 JSON 包装或内部检索指标。"""
    lines = [f"{source_name}（不可信引用资料）"]
    if unavailable:
        lines.append("状态：检索源暂时不可用。")
    if not documents:
        if not unavailable:
            lines.append("状态：未检索到匹配条目。")
        return "\n".join(lines)
    for index, (title, content, section, page_number) in enumerate(documents, start=1):
        lines.append(f"[文档 {index}]")
        lines.append(f"标题：{title}")
        if section:
            lines.append(f"章节：{section}")
        if page_number is not None:
            lines.append(f"页码：{page_number}")
        lines.extend(("正文：", content, "---"))
    return "\n".join(lines)


def build_search_meme_tool(
    *,
    default_top_k: int = 5,
    initial_query: str | None = None,
    initial_query_aliases: list[str] | None = None,
    initial_chunks: list[RetrievedChunk] | None = None,
    retrieval_unavailable: bool = False,
    request_id: str = "",
    on_retrieval: Callable[[list[RetrievedChunk]], None] | None = None,
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

    def format_result(chunks: list[RetrievedChunk], *, unavailable: bool = False) -> str:
        if on_retrieval is not None:
            on_retrieval(list(chunks))
        return format_meme_tool_result(chunks, unavailable=unavailable)

    async def _run(query: str, top_k: int = 5) -> str:
        nonlocal retrieval_failed
        k = max(1, int(top_k or default_top_k))
        if retrieval_failed:
            # 首次向量检索已耗尽整请求的预算，只复用已成功取得的向量结果。
            chunks = (cached_chunks or [])[:k]
            return format_result(chunks, unavailable=True)
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
                return format_result(fallback_chunks, unavailable=True)
            retrieval_cache[cache_key] = chunks
        logger.info("search_meme_knowledge hits=%s query_chars=%s", len(chunks), len(query))
        return format_result(chunks)

    return StructuredTool.from_function(
        coroutine=_run,
        name="search_meme_knowledge",
        description=(
            "仅从站内梗向量知识库检索达到相关度门槛的条目，并按标题/标签命中优先排序。"
            "以纯文本返回来源、标题和不可信正文；无结果与检索源故障会分别说明。"
        ),
        args_schema=SearchMemeInput,
    )


def _normalize_retrieval_query(query: str) -> str:
    """Ignore whitespace and punctuation when deciding whether a cached query matches."""
    return re.sub(r"[\s\W_]+", "", query.casefold())


@traceable(name="admin_knowledge_retriever", run_type="retriever")
async def retrieve_admin_knowledge_candidates(
    query: str,
    candidate_limit: int,
    request_id: str = "",
) -> list[RetrievedKnowledgeChunk]:
    """从 PostgreSQL 获取有界候选；本阶段不执行相关度过滤或 top-k 截断。"""
    settings = get_settings()
    if settings.admin_knowledge_min_similarity is None:
        logger.info(
            "admin_retrieval_no_calibrated_vector_threshold request_id=%s",
            request_id,
        )
    return await run_with_retry(
        lambda: similarity_search_knowledge(query, candidate_limit),
        timeout_seconds=settings.retrieval_timeout_seconds,
        max_retries=settings.retrieval_max_retries,
        stage="admin_retrieval",
        request_id=request_id,
    )


async def retrieve_admin_knowledge_chunks(
    query: str,
    top_k: int = 5,
    request_id: str = "",
) -> list[RetrievedKnowledgeChunk]:
    """获取有界候选后，由独立 Score Filter 执行阈值过滤与最终排序。"""
    settings = get_settings()
    started_at = time.monotonic()
    candidate_limit = max(20, 4 * max(1, top_k))
    candidates = await retrieve_admin_knowledge_candidates(
        query,
        candidate_limit,
        request_id=request_id,
    )
    chunks = rank_admin_knowledge_candidates(
        candidates,
        min_similarity=settings.admin_knowledge_min_similarity,
        top_k=top_k,
    )
    candidates = ",".join(
        f"{chunk.doc_id}:{chunk.score:.4f}:rank={chunk.rank_score:.4f}"
        for chunk in chunks[:5]
    ) or "-"
    logger.info(
        "rag_stage_completed request_id=%s stage=admin_score_filter status=success hits=%s "
        "candidates=%s duration_ms=%s",
        request_id,
        len(chunks),
        candidates,
        int((time.monotonic() - started_at) * 1000),
    )
    return chunks


def _knowledge_content_for_model(content: str) -> str:
    """移除切块正文中的重复元数据头，保留实际知识正文。"""
    normalized = (content or "").strip()
    header, separator, body = normalized.partition("\n\n")
    if not separator:
        return normalized

    metadata_prefixes = ("标题:", "分类:", "标签:", "章节:", "页码:")
    header_lines = header.splitlines()
    if header_lines and all(
        line.strip().startswith(metadata_prefixes) for line in header_lines
    ):
        return body.strip()
    return normalized


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
            return format_admin_knowledge_tool_result(
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
            return format_admin_knowledge_tool_result(chunks)
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
            return format_admin_knowledge_tool_result([], unavailable=True)
        logger.info("search_admin_knowledge hits=%s query_chars=%s", len(chunks), len(query))
        return format_admin_knowledge_tool_result(chunks)

    return StructuredTool.from_function(
        coroutine=_run,
        name="search_admin_knowledge",
        description=(
            "只从 PostgreSQL 管理员知识向量库检索规则、玩法说明和 FAQ；不联网。"
            "以纯文本返回不可信引用资料；无结果与检索源故障会分别说明。"
        ),
        args_schema=SearchAdminKnowledgeInput,
    )
