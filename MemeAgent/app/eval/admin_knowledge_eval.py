"""从人工标注问题集评估并校准管理员知识检索相关度门槛。"""

from __future__ import annotations

import argparse
import asyncio
import json
import math
from pathlib import Path
import time
from typing import Any

from app.core.settings import get_settings
from app.retrieval.db import close_pool, create_pool
from app.retrieval.knowledge_repository import similarity_search_knowledge


def _rank_candidates(
    candidates: list[dict[str, Any]], threshold: float
) -> list[dict[str, Any]]:
    """按线上精确词项优先级与阈值后 RRF 分数恢复候选排序。"""
    eligible = [
        candidate
        for candidate in candidates
        if candidate.get("exact_match")
        or candidate.get("content_match")
        or float(candidate.get("score") or 0) >= threshold
    ]

    def sort_key(candidate: dict[str, Any]) -> tuple[bool, bool, float, float]:
        score = float(candidate.get("score") or 0)
        vector_part = float(candidate.get("vector_rank_score") or 0)
        lexical_part = float(candidate.get("lexical_rank_score") or 0)
        if vector_part or lexical_part:
            fused_score = lexical_part + (vector_part if score >= threshold else 0.0)
        else:
            fused_score = float(candidate.get("rank_score") or score)
        return (
            not bool(candidate.get("exact_match")),
            not bool(candidate.get("content_match")),
            -fused_score,
            -score,
        )

    return sorted(eligible, key=sort_key)


def calibrate_admin_knowledge_threshold(cases: list[dict[str, Any]]) -> dict[str, Any]:
    """以最大 F1 选择门槛；并列时优先精度，再优先较高门槛。"""
    prepared: list[tuple[set[str], list[dict[str, Any]], int]] = []
    observed_scores: set[float] = set()
    positive_count = 0
    for case in cases:
        # 中文注释：expected_doc_ids 是人工确认的相关知识文档 ID。
        expected = {str(value).strip() for value in case.get("expected_doc_ids") or [] if str(value).strip()}
        # 中文注释：expected_empty 标记该查询在当前管理员知识库中应返回空结果。
        expected_empty = case.get("expected_empty") is True
        if expected and expected_empty:
            raise ValueError("问题不能同时标注相关文档和 expected_empty")
        if not expected and not expected_empty:
            continue
        candidates: dict[tuple[str, str], dict[str, Any]] = {}
        for candidate in case.get("candidates") or []:
            doc_id = str(candidate.get("doc_id") or "").strip()
            try:
                score = float(candidate.get("score"))
            except (TypeError, ValueError):
                continue
            if not doc_id or not math.isfinite(score):
                continue
            section_path = str(candidate.get("section_path") or "").strip()
            page_number = candidate.get("page_number")
            section_key = section_path or (f"PDF 第 {page_number} 页" if page_number else "文档")
            candidate_key = (doc_id, section_key)
            exact_match = bool(candidate.get("exact_match"))
            content_match = bool(candidate.get("content_match"))
            vector_rank_score = float(candidate.get("vector_rank_score") or 0)
            lexical_rank_score = float(candidate.get("lexical_rank_score") or 0)
            rank_score = float(candidate.get("rank_score") or score)
            previous = candidates.get(candidate_key)
            if previous is None or (exact_match, content_match, rank_score, score) > (
                bool(previous["exact_match"]),
                bool(previous["content_match"]),
                float(previous["rank_score"]),
                float(previous["score"]),
            ):
                candidates[candidate_key] = {
                    "doc_id": doc_id,
                    "score": score,
                    "exact_match": exact_match,
                    "content_match": content_match,
                    "rank_score": rank_score,
                    "vector_rank_score": vector_rank_score,
                    "lexical_rank_score": lexical_rank_score,
                }
        observed_scores.update(float(candidate["score"]) for candidate in candidates.values())
        positive_count += len(expected)
        # 中文注释：top_k 控制每个查询最终计入的文档数量。
        prepared.append((expected, list(candidates.items()), max(1, int(case.get("top_k") or 5))))

    if positive_count == 0:
        raise ValueError("校准至少需要一个带 expected_doc_ids 的正例")
    if not observed_scores:
        raise ValueError("评估集没有检索候选分数，无法校准门槛")

    thresholds = sorted(score for score in observed_scores if -1 <= score <= 1)
    thresholds.append(1.0)
    best: tuple[float, float, float, int, int, int] | None = None
    for threshold in thresholds:
        true_positive = false_positive = false_negative = predicted = 0
        for expected, candidates, top_k in prepared:
            ordered = _rank_candidates([candidate for _key, candidate in candidates], threshold)
            selected_sections = ordered[:top_k]
            # 中文注释：线上 top_k 按文档/章节合并后的片段数计算；评估指标按唯一文档计算。
            selected = {str(candidate["doc_id"]) for candidate in selected_sections}
            hits = expected.intersection(selected)
            true_positive += len(hits)
            false_positive += len(selected - expected)
            false_negative += len(expected - hits)
            predicted += len(selected)
        precision = true_positive / predicted if predicted else 0.0
        recall = true_positive / positive_count
        f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
        candidate = (f1, precision, threshold, true_positive, false_positive, false_negative)
        if best is None or candidate[:3] > best[:3]:
            best = candidate

    assert best is not None
    f1, precision, threshold, true_positive, false_positive, false_negative = best
    ranking_metrics = _ranking_metrics(prepared, threshold)
    return {
        "selection_metric": "maximum_f1; ties prefer precision then higher threshold",
        "threshold": threshold,
        "precision": round(precision, 4),
        "recall": round(true_positive / positive_count, 4),
        "f1": round(f1, 4),
        "true_positive_count": true_positive,
        "false_positive_count": false_positive,
        "false_negative_count": false_negative,
        "positive_document_count": positive_count,
        "labeled_query_count": len(prepared),
        **ranking_metrics,
    }


def _ranking_metrics(
    prepared: list[tuple[set[str], list[tuple[tuple[str, str], dict[str, Any]]], int]],
    threshold: float,
) -> dict[str, Any]:
    """计算带相关问题的 P@K、R@K、MRR 与无结果问题准确率。"""
    precisions: list[float] = []
    recalls: list[float] = []
    reciprocal_ranks: list[float] = []
    empty_correct = empty_total = 0
    for expected, candidates, top_k in prepared:
        ordered = _rank_candidates([candidate for _key, candidate in candidates], threshold)
        selected = ordered[:top_k]
        ranked_doc_ids = list(dict.fromkeys(str(item.get("doc_id") or "") for item in selected))
        if expected:
            relevant = [doc_id for doc_id in ranked_doc_ids if doc_id in expected]
            precisions.append(len(relevant) / top_k)
            recalls.append(len(set(relevant)) / len(expected))
            reciprocal_ranks.append(1 / (ranked_doc_ids.index(relevant[0]) + 1) if relevant else 0.0)
        else:
            empty_total += 1
            empty_correct += int(not ranked_doc_ids)
    return {
        "precision_at_k": round(sum(precisions) / len(precisions), 4) if precisions else None,
        "recall_at_k": round(sum(recalls) / len(recalls), 4) if recalls else None,
        "mrr": round(sum(reciprocal_ranks) / len(reciprocal_ranks), 4)
        if reciprocal_ranks
        else None,
        "empty_query_accuracy": round(empty_correct / empty_total, 4) if empty_total else None,
        "positive_query_count": len(precisions),
        "empty_query_count": empty_total,
    }


async def evaluate_admin_knowledge_dataset(dataset_path: Path) -> dict[str, Any]:
    """实时检索标注问题，输出原始分数与可配置的校准值。"""
    cases = json.loads(dataset_path.read_text(encoding="utf-8"))
    if not isinstance(cases, list):
        raise ValueError("管理员知识评估集必须是 JSON 数组")
    await create_pool(get_settings())
    evaluated: list[dict[str, Any]] = []
    try:
        for index, case in enumerate(cases):
            if not isinstance(case, dict) or not str(case.get("query") or "").strip():
                raise ValueError(f"第 {index + 1} 个问题缺少 query")
            query = str(case["query"]).strip()
            started_at = time.monotonic()
            # 中文注释：-1 仅用于评估阶段收集未按门槛裁剪的向量候选。
            hits = await similarity_search_knowledge(query, 100, -1.0)
            evaluated.append(
                {
                    "query": query,
                    "expected_doc_ids": case.get("expected_doc_ids") or [],
                    "expected_empty": case.get("expected_empty") is True,
                    "top_k": int(case.get("top_k") or 5),
                    "latency_ms": round((time.monotonic() - started_at) * 1000, 2),
                    "candidates": [
                        {
                            "doc_id": hit.doc_id,
                            "score": hit.score,
                            "rank_score": hit.rank_score,
                            "vector_rank_score": hit.vector_rank_score,
                            "lexical_rank_score": hit.lexical_rank_score,
                            "exact_match": hit.exact_match,
                            "content_match": hit.content_match,
                            "title": hit.title,
                            "section_path": hit.section_path,
                            "page_number": hit.page_number,
                        }
                        for hit in hits
                    ],
                }
            )
    finally:
        await close_pool()
    has_positive_labels = any(
        case.get("expected_doc_ids")
        for case in evaluated
    )
    calibration = (
        calibrate_admin_knowledge_threshold(evaluated) if has_positive_labels else None
    )
    latency_values = sorted(float(case["latency_ms"]) for case in evaluated)

    def percentile(values: list[float], fraction: float) -> float | None:
        if not values:
            return None
        index = min(len(values) - 1, max(0, math.ceil(len(values) * fraction) - 1))
        return round(values[index], 2)

    return {
        "calibration": calibration,
        "latency_p50_ms": percentile(latency_values, 0.50),
        "latency_p95_ms": percentile(latency_values, 0.95),
        "queries": evaluated,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    # 中文注释：人工标注的问题集文件路径。
    parser.add_argument("dataset", type=Path, help="管理员知识评估集 JSON 文件")
    # 中文注释：可选校准报告输出路径；不提供时只打印到标准输出。
    parser.add_argument("--output", type=Path, help="可选 JSON 报告输出路径")
    args = parser.parse_args()
    result = asyncio.run(evaluate_admin_knowledge_dataset(args.dataset))
    rendered = json.dumps(result, ensure_ascii=False, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered + "\n", encoding="utf-8")
    print(rendered)


if __name__ == "__main__":
    main()
