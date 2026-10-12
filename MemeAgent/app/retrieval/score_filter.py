"""RAG 候选过滤、排序与去重阶段。"""

from __future__ import annotations

from dataclasses import dataclass, replace
import re
from typing import Any

from langsmith import traceable

from app.retrieval.knowledge_repository import RetrievedKnowledgeChunk
from app.retrieval.repository import RetrievedChunk


@dataclass
class _AdminFilterResult:
    """过滤结果与不含正文的诊断统计。"""

    # 中文注释：通过相关度过滤、章节去重和 top-k 截断后的知识片段。
    documents: list[RetrievedKnowledgeChunk]
    # 中文注释：通过相似度或词项命中资格判定的候选数。
    eligible_count: int
    # 中文注释：候选中因相似度不足或缺少词项命中而被过滤的数量。
    filtered_count: int
    # 中文注释：按章节去重时移除的候选数。
    deduplicated_count: int
    # 中文注释：章节去重后因 top-k 限制移除的候选数。
    truncated_count: int


def _filter_trace_inputs(inputs: dict[str, Any]) -> dict[str, Any]:
    """只记录候选数量与配置，避免 score-filter span 重复保存正文。"""
    candidates = inputs.get("candidates") or []
    return {
        "candidate_count": len(candidates),
        "min_similarity": inputs.get("min_similarity"),
        "top_k": inputs.get("top_k"),
    }


def _filter_trace_outputs(result: _AdminFilterResult) -> dict[str, Any]:
    """把过滤输出压缩为计数和不含文本的排序分数摘要。"""
    return {
        "candidate_count": result.eligible_count + result.filtered_count,
        "eligible_count": result.eligible_count,
        "filtered_count": result.filtered_count,
        "deduplicated_count": result.deduplicated_count,
        "truncated_count": result.truncated_count,
        "returned_count": len(result.documents),
        "ranking": [
            {
                "score": item.score,
                "rank_score": item.rank_score,
                "exact_match": item.exact_match,
                "content_match": item.content_match,
            }
            for item in result.documents
        ],
    }


def _candidate_sources(candidate: RetrievedKnowledgeChunk) -> set[str]:
    """读取候选来源，并兼容没有新增来源字段的旧评估样本。"""
    sources = set(candidate.candidate_sources or ())
    if sources:
        return sources
    if candidate.vector_rank is not None or candidate.vector_rank_score > 0:
        sources.add("vector")
    if (
        candidate.lexical_rank is not None
        or candidate.lexical_rank_score > 0
        or candidate.exact_match
        or candidate.content_match
    ):
        sources.add("lexical")
    if not sources:
        sources.add("vector")
    return sources


@traceable(
    name="admin_knowledge_score_filter",
    run_type="chain",
    process_inputs=_filter_trace_inputs,
    process_outputs=_filter_trace_outputs,
)
def _rank_admin_knowledge_stage(
    candidates: list[RetrievedKnowledgeChunk],
    min_similarity: float | None,
    top_k: int | None,
) -> _AdminFilterResult:
    """先过滤，再对保留候选重算 RRF，章节去重后应用 top-k。"""
    eligible = [
        candidate
        for candidate in candidates
        if candidate.exact_match
        or candidate.content_match
        or (
            min_similarity is not None
            and candidate.score >= min_similarity
        )
    ]

    vector_candidates = [
        candidate
        for candidate in eligible
        if candidate.score >= (min_similarity if min_similarity is not None else float("inf"))
        and "vector" in _candidate_sources(candidate)
    ]
    lexical_candidates = [
        candidate
        for candidate in eligible
        if "lexical" in _candidate_sources(candidate)
    ]

    # 中文注释：先移除不合格向量项，再按保留候选分数重算向量 RRF 排名。
    vector_candidates.sort(key=lambda item: (-item.score, item.chunk_index, item.doc_id))
    # 中文注释：词项候选按标题/标签命中、正文短语命中和相似度稳定排序。
    lexical_candidates.sort(
        key=lambda item: (
            not item.exact_match,
            not item.content_match,
            -item.score,
            item.chunk_index,
            item.doc_id,
        )
    )
    vector_ranks = {id(item): rank for rank, item in enumerate(vector_candidates, start=1)}
    lexical_ranks = {id(item): rank for rank, item in enumerate(lexical_candidates, start=1)}
    # 中文注释：旧评估样本可能只有 RRF 分量而没有分支原始排名，需兼容读取。
    has_vector_ranks = any(item.vector_rank is not None for item in eligible)
    has_lexical_ranks = any(item.lexical_rank is not None for item in eligible)

    rescored: list[RetrievedKnowledgeChunk] = []
    for candidate in eligible:
        vector_rank = vector_ranks.get(id(candidate))
        lexical_rank = lexical_ranks.get(id(candidate))
        vector_score = (
            1.0 / (60 + vector_rank)
            if vector_rank is not None
            else (
                candidate.vector_rank_score
                if not has_vector_ranks
                and candidate.score >= (min_similarity if min_similarity is not None else float("inf"))
                and "vector" in _candidate_sources(candidate)
                else 0.0
            )
        )
        lexical_score = (
            1.0 / (60 + lexical_rank)
            if lexical_rank is not None
            else (
                candidate.lexical_rank_score
                if not has_lexical_ranks and "lexical" in _candidate_sources(candidate)
                else 0.0
            )
        )
        rescored.append(
            replace(
                candidate,
                rank_score=vector_score + lexical_score,
                vector_rank_score=vector_score,
                lexical_rank_score=lexical_score,
                vector_rank=vector_rank,
                lexical_rank=lexical_rank,
            )
        )

    rescored.sort(
        key=lambda item: (
            not item.exact_match,
            not item.content_match,
            -item.rank_score,
            -item.score,
            item.chunk_index,
            item.doc_id,
        )
    )
    best_by_section: dict[tuple[str, str], RetrievedKnowledgeChunk] = {}
    for candidate in rescored:
        section_key = (
            candidate.section_path
            or (f"PDF 第 {candidate.page_number} 页" if candidate.page_number else "文档")
        )
        best_by_section.setdefault((candidate.doc_id, section_key), candidate)

    deduplicated = list(best_by_section.values())
    selected = deduplicated if top_k is None else deduplicated[: max(0, top_k)]
    return _AdminFilterResult(
        documents=selected,
        eligible_count=len(eligible),
        filtered_count=len(candidates) - len(eligible),
        deduplicated_count=len(eligible) - len(deduplicated),
        truncated_count=len(deduplicated) - len(selected),
    )


def rank_admin_knowledge_candidates(
    candidates: list[RetrievedKnowledgeChunk],
    min_similarity: float | None,
    top_k: int | None = None,
) -> list[RetrievedKnowledgeChunk]:
    """线上与离线评估共用的管理员知识过滤、RRF 重排与章节去重。"""
    return _rank_admin_knowledge_stage(candidates, min_similarity, top_k).documents


@dataclass
class _MemeFilterResult:
    """梗库过滤结果与不含正文的诊断统计。"""

    # 中文注释：通过相似度门槛、按梗 ID 去重并截断后的梗知识片段。
    documents: list[RetrievedChunk]
    # 中文注释：通过向量相似度门槛的原始候选数。
    eligible_count: int
    # 中文注释：因低于向量相似度门槛而过滤的候选数。
    filtered_count: int
    # 中文注释：按 meme ID 去重时移除的候选数。
    deduplicated_count: int
    # 中文注释：去重后因 top-k 限制移除的候选数。
    truncated_count: int
    # 中文注释：只含向量分数和标题/标签优先级的排序摘要。
    ranking_summary: list[tuple[int, float]]


def _meme_filter_trace_inputs(inputs: dict[str, Any]) -> dict[str, Any]:
    """仅记录候选数量和排序配置，不重复保存梗正文。"""
    return {
        "query_chars": len(str(inputs.get("query") or "")),
        "candidate_count": len(inputs.get("candidates") or []),
        "min_similarity": inputs.get("min_similarity"),
        "top_k": inputs.get("top_k"),
    }


def _meme_filter_trace_outputs(result: _MemeFilterResult) -> dict[str, Any]:
    """把梗库过滤结果压缩成计数和分数摘要。"""
    return {
        "candidate_count": result.eligible_count + result.filtered_count,
        "eligible_count": result.eligible_count,
        "filtered_count": result.filtered_count,
        "deduplicated_count": result.deduplicated_count,
        "truncated_count": result.truncated_count,
        "returned_count": len(result.documents),
        "ranking": [
            {"score": score, "title_match_priority": priority}
            for priority, score in result.ranking_summary
        ],
    }


@traceable(
    name="meme_score_filter",
    run_type="chain",
    process_inputs=_meme_filter_trace_inputs,
    process_outputs=_meme_filter_trace_outputs,
)
def _rank_meme_candidates_stage(
    query: str,
    candidates: list[RetrievedChunk],
    min_similarity: float,
    top_k: int,
) -> _MemeFilterResult:
    """仅对达标候选按标题/标签优先级排序、去重并截取 top-k。"""
    query_key = _normalize_meme_text(query)
    eligible = [
        (index, chunk)
        for index, chunk in enumerate(candidates)
        if chunk.score >= min_similarity
    ]
    best_by_meme: dict[str, tuple[int, RetrievedChunk, int]] = {}
    for index, chunk in eligible:
        priority = _meme_lexical_match_priority(query_key, chunk)
        existing = best_by_meme.get(chunk.meme_id)
        if existing is None or (priority, chunk.score) > (existing[0], existing[1].score):
            best_by_meme[chunk.meme_id] = (priority, chunk, index)

    ordered = sorted(
        best_by_meme.values(),
        key=lambda candidate: (-candidate[0], -candidate[1].score, candidate[2]),
    )
    selected = ordered[: max(1, top_k)]
    return _MemeFilterResult(
        documents=[chunk for _priority, chunk, _index in selected],
        eligible_count=len(eligible),
        filtered_count=len(candidates) - len(eligible),
        deduplicated_count=len(eligible) - len(best_by_meme),
        truncated_count=len(best_by_meme) - len(selected),
        ranking_summary=[(priority, chunk.score) for priority, chunk, _index in selected],
    )


def rank_meme_candidates(
    query: str,
    candidates: list[RetrievedChunk],
    *,
    min_similarity: float,
    top_k: int,
) -> list[RetrievedChunk]:
    """线上与离线评估共用的梗库向量阈值过滤、排序和 meme ID 去重。"""
    return _rank_meme_candidates_stage(
        query,
        candidates,
        min_similarity,
        top_k,
    ).documents


def _normalize_meme_text(value: str) -> str:
    """与线上旧逻辑一致地忽略空白和标点。"""
    return re.sub(r"[\s\W_]+", "", value.casefold())


def _meme_lexical_match_priority(query_key: str, chunk: RetrievedChunk) -> int:
    """标题匹配优先于标签匹配；普通正文不参与词项加权。"""
    if not query_key:
        return 0
    title = chunk.title or ""
    if not title:
        title = next(
            (
                line.removeprefix("标题:").strip()
                for line in chunk.content.splitlines()
                if line.startswith("标题:")
            ),
            "",
        )
    title_key = _normalize_meme_text(title)
    if len(title_key) >= 2 and title_key in query_key:
        return 2

    for line in chunk.content.splitlines():
        if not line.startswith("标签:"):
            continue
        raw_tags = re.split(r"[\s、,，;；|/]+", line.removeprefix("标签:").strip())
        if any(
            len(tag_key := _normalize_meme_text(tag)) >= 2 and tag_key in query_key
            for tag in raw_tags
        ):
            return 1
    return 0
