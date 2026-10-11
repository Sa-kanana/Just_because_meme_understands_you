"""运行标注过的梗检索与回答评估，并输出 JSON 指标。"""

from __future__ import annotations

import argparse
import asyncio
import json
import math
import time
from pathlib import Path
from typing import Any

from app.agents.search_agent import stream_ai_search
from app.agents.query_intent import greeting_reply
from app.agents.tools import rank_vector_candidates, retrieve_meme_chunks
from app.core.settings import get_settings
from app.retrieval.db import close_pool, create_pool
from app.retrieval.repository import similarity_search
from app.schemas.stream import StreamRequest

DEFAULT_DATASET = Path(__file__).with_name("golden_queries.json")
TOP_KS = (5, 10)


def calibrate_vector_similarity_threshold(cases: list[dict[str, Any]]) -> dict[str, Any]:
    """在人工标注的正负例上选择 F1 最优且更偏向高精度的相似度门槛。"""
    candidates_by_query: list[tuple[set[str], dict[str, tuple[float, int]], int]] = []
    observed_scores: set[float] = set()
    total_positive_labels = 0
    for case in cases:
        expected = {
            str(name).strip().casefold()
            for name in case.get("expected_meme_names") or []
            if str(name).strip()
        }
        if not expected and case.get("expected_empty") is not True:
            continue
        if expected and case.get("expected_empty") is True:
            raise ValueError("评估问题不能同时标记 expected_meme_names 和 expected_empty")
        total_positive_labels += len(expected)
        scores_by_title: dict[str, tuple[float, int]] = {}
        for rank, candidate in enumerate(case.get("candidates") or []):
            title = str(candidate.get("title") or "").strip().casefold()
            try:
                score = float(candidate.get("score"))
            except (TypeError, ValueError):
                continue
            if not title or not math.isfinite(score):
                continue
            existing_score = scores_by_title.get(title, (float("-inf"), rank))[0]
            if score > existing_score:
                scores_by_title[title] = (score, rank)
            observed_scores.add(score)
        candidates_by_query.append(
            (expected, scores_by_title, max(1, int(case.get("top_k") or 5)))
        )

    if total_positive_labels == 0:
        raise ValueError("相似度门槛校准至少需要一条带 expected_meme_names 的正例")
    if not observed_scores:
        raise ValueError("评估集中没有可用于校准的向量分数")
    observed_scores.add(1.0)

    best: tuple[float, float, float, int, int, int] | None = None
    for threshold in observed_scores:
        true_positive = 0
        false_positive = 0
        false_negative = 0
        predicted = 0
        for expected, scores_by_title, top_k in candidates_by_query:
            selected_ordered = [
                title
                for title, (score, _rank) in sorted(
                    scores_by_title.items(), key=lambda item: item[1][1]
                )
                if score >= threshold
            ][:top_k]
            selected = set(selected_ordered)
            hits = expected.intersection(selected)
            true_positive += len(hits)
            false_positive += len(selected - expected)
            false_negative += len(expected - hits)
            predicted += len(selected)
        precision = true_positive / predicted if predicted else 0.0
        recall = true_positive / total_positive_labels
        f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
        candidate = (f1, precision, threshold, true_positive, false_positive, false_negative)
        if best is None or candidate[:3] > best[:3]:
            best = candidate

    assert best is not None
    f1, precision, threshold, true_positive, false_positive, false_negative = best
    return {
        "selection_metric": "maximum_f1; ties prefer precision then higher threshold",
        # 保留原始浮点门槛，避免序列化时向上舍入排除边界正例。
        "threshold": threshold,
        "precision": round(precision, 4),
        "recall": round(true_positive / total_positive_labels, 4),
        "f1": round(f1, 4),
        "true_positive_count": true_positive,
        "false_positive_count": false_positive,
        "false_negative_count": false_negative,
        "positive_label_count": total_positive_labels,
        "labeled_query_count": len(candidates_by_query),
    }


def score_retrieval(
    expected_ids: list[str],
    retrieved_ids: list[str],
    top_ks: tuple[int, ...] = TOP_KS,
) -> dict[str, float] | None:
    """计算 Recall@K 与 MRR；空标签表示未标注，不参与评分。"""
    return _score_retrieval_keys(expected_ids, retrieved_ids, top_ks)


def score_retrieval_names(
    expected_names: list[str],
    retrieved_names: list[str],
    top_ks: tuple[int, ...] = TOP_KS,
) -> dict[str, float] | None:
    """按梗标题计算检索排序，避免跨数据库环境依赖自增 ID。"""
    return _score_retrieval_keys(expected_names, retrieved_names, top_ks)


def _score_retrieval_keys(
    expected_values: list[str],
    retrieved_values: list[str],
    top_ks: tuple[int, ...],
) -> dict[str, float] | None:
    expected = {str(value).strip().casefold() for value in expected_values if str(value).strip()}
    if not expected:
        return None

    unique_retrieved = list(
        dict.fromkeys(str(value).strip().casefold() for value in retrieved_values if str(value).strip())
    )
    first_relevant_rank = next(
        (rank for rank, value in enumerate(unique_retrieved, start=1) if value in expected),
        None,
    )
    metrics = {
        f"recall_at_{k}": len(expected.intersection(unique_retrieved[:k])) / len(expected)
        for k in top_ks
    }
    metrics["mrr"] = 1.0 / first_relevant_rank if first_relevant_rank else 0.0
    return metrics


def score_answer_grounding(
    expected_meme_names: list[str],
    expected_answer_fact_groups: list[list[str]],
    evidence_titles: list[str],
    answer: str,
) -> dict[str, float | bool] | None:
    """用固定词面标签近似衡量回答是否命中预期证据和事实。"""
    if not expected_meme_names and not expected_answer_fact_groups:
        return None

    normalized_titles = [str(title).strip().casefold() for title in evidence_titles if str(title).strip()]
    normalized_answer = str(answer or "").casefold()
    name_coverage = (
        sum(any(name.strip().casefold() in title for title in normalized_titles) for name in expected_meme_names)
        / len(expected_meme_names)
        if expected_meme_names
        else 1.0
    )
    fact_coverage = (
        sum(
            any(str(alternative).strip().casefold() in normalized_answer for alternative in group)
            for group in expected_answer_fact_groups
        )
        / len(expected_answer_fact_groups)
        if expected_answer_fact_groups
        else 1.0
    )
    return {
        "expected_meme_name_coverage": round(name_coverage, 4),
        "expected_answer_fact_coverage": round(fact_coverage, 4),
        "grounded": name_coverage == 1.0 and fact_coverage == 1.0,
    }


def estimate_cost_usd(
    usage: dict[str, Any] | None,
    input_price_per_million: float | None,
    output_price_per_million: float | None,
) -> float | None:
    """按供应商给出的 token usage 与用户配置的价格估算单次费用。"""
    if usage is None or input_price_per_million is None or output_price_per_million is None:
        return None
    input_tokens = usage.get("prompt_tokens", usage.get("input_tokens"))
    output_tokens = usage.get("completion_tokens", usage.get("output_tokens"))
    if input_tokens is None or output_tokens is None:
        return None
    try:
        input_tokens = max(0, int(input_tokens))
        output_tokens = max(0, int(output_tokens))
        input_price = float(input_price_per_million)
        output_price = float(output_price_per_million)
    except (TypeError, ValueError):
        return None
    if input_price < 0 or output_price < 0:
        raise ValueError("Token 单价不能为负数")
    return round(
        (input_tokens * input_price + output_tokens * output_price) / 1_000_000,
        10,
    )


def _percentile(values: list[float], percentile: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, round((len(ordered) - 1) * percentile)))
    return round(ordered[index], 2)


def _parse_sse_event(raw_event: str) -> tuple[str, dict[str, Any]] | None:
    """解析 Agent 输出的单个 SSE 事件，忽略不完整或无效事件。"""
    event_name = "message"
    data_lines: list[str] = []
    for line in raw_event.splitlines():
        if line.startswith("event:"):
            event_name = line.removeprefix("event:").strip()
        elif line.startswith("data:"):
            data_lines.append(line.removeprefix("data:").strip())
    if not data_lines:
        return None
    try:
        payload = json.loads("\n".join(data_lines))
    except json.JSONDecodeError:
        return None
    return (event_name, payload) if isinstance(payload, dict) else None


async def _evaluate_answer(
    query: str,
    case: dict[str, Any],
    index: int,
) -> dict[str, Any]:
    """调用实际 SSE 搜索入口，收集答案、引用、usage 与首 token 延迟。"""
    request = StreamRequest(
        request_id=f"eval-{index + 1}",
        session_id="golden-eval",
        query=query,
        context={
            "hint_meme_ids": case.get("hint_meme_ids") or [],
            "extra": {"snippets": case.get("snippets") or []},
        },
    )
    started = time.perf_counter()
    first_token_ms: float | None = None
    answer_parts: list[str] = []
    evidence: list[dict[str, Any]] = []
    retrieved_ids: list[str] = []
    usage: dict[str, int] = {}
    errors: list[dict[str, Any]] = []

    async for raw_event in stream_ai_search(request):
        parsed = _parse_sse_event(raw_event)
        if parsed is None:
            continue
        event_name, payload = parsed
        if event_name == "meta":
            retrieved_ids.extend(str(value) for value in payload.get("retrieved") or [])
        elif event_name == "cite":
            evidence.append(payload)
            meme_id = str(payload.get("meme_id") or "")
            if meme_id and meme_id not in retrieved_ids:
                retrieved_ids.append(meme_id)
        elif event_name == "token":
            text = str(payload.get("text") or "")
            if text and first_token_ms is None:
                first_token_ms = (time.perf_counter() - started) * 1000
            answer_parts.append(text)
        elif event_name == "done":
            raw_usage = payload.get("usage")
            if isinstance(raw_usage, dict):
                usage = {
                    str(key): int(value)
                    for key, value in raw_usage.items()
                    if isinstance(value, (int, float)) and not isinstance(value, bool)
                }
        elif event_name == "error":
            errors.append(payload)

    answer = "".join(answer_parts)
    evidence_titles = [str(item.get("title") or "") for item in evidence]
    return {
        "answer": answer,
        "evidence": evidence,
        "evidence_titles": evidence_titles,
        "retrieved_meme_ids": list(dict.fromkeys(retrieved_ids)),
        "usage": usage or None,
        "latency_ms": round((time.perf_counter() - started) * 1000, 2),
        "time_to_first_token_ms": round(first_token_ms, 2) if first_token_ms is not None else None,
        "errors": errors,
    }


async def evaluate_dataset(
    dataset_path: Path = DEFAULT_DATASET,
    *,
    include_answers: bool = False,
    calibrate_threshold: bool = False,
    stream_usage: bool = False,
    input_price_per_million: float | None = None,
    output_price_per_million: float | None = None,
) -> dict[str, Any]:
    cases = json.loads(dataset_path.read_text(encoding="utf-8"))
    if not isinstance(cases, list):
        raise ValueError("Golden query dataset must be a JSON array")
    if stream_usage and not include_answers:
        raise ValueError("--stream-usage 需要同时启用 --include-answers")
    if calibrate_threshold and include_answers:
        raise ValueError("--calibrate-threshold 只能用于检索评估，不能同时启用 --include-answers")

    settings = get_settings()
    settings.llm_stream_usage = stream_usage
    await create_pool(settings)
    details: list[dict[str, Any]] = []
    latencies: list[float] = []
    first_token_latencies: list[float] = []
    calibration_cases: list[dict[str, Any]] = []
    try:
        for index, case in enumerate(cases):
            if not isinstance(case, dict) or not str(case.get("query") or "").strip():
                raise ValueError(f"Golden query at index {index} has no query")
            query = str(case["query"]).strip()
            expected_ids = [str(item) for item in case.get("expected_meme_ids") or []]
            expected_names = [str(item) for item in case.get("expected_meme_names") or []]
            expected_empty = case.get("expected_empty") is True

            if include_answers:
                result = await _evaluate_answer(query, case, index)
                retrieved_ids = result["retrieved_meme_ids"]
                retrieved_names = result["evidence_titles"]
                latency_ms = result["latency_ms"]
                answer = result["answer"]
                evidence_titles = result["evidence_titles"]
                usage = result["usage"]
                if result["time_to_first_token_ms"] is not None:
                    first_token_latencies.append(result["time_to_first_token_ms"])
                details_extra = {
                    "answer": answer,
                    "evidence": result["evidence"],
                    "errors": result["errors"],
                    "time_to_first_token_ms": result["time_to_first_token_ms"],
                    "usage": usage,
                    "estimated_cost_usd": estimate_cost_usd(
                        usage,
                        input_price_per_million,
                        output_price_per_million,
                    ),
                    "answer_grounding": score_answer_grounding(
                        expected_names,
                        case.get("expected_answer_fact_groups") or [],
                        evidence_titles,
                        answer,
                    ),
                }
            else:
                started = time.perf_counter()
                if greeting_reply(query) is not None:
                    # 线上纯问候会在向量检索前短路；评估也跳过检索以复现真实链路。
                    chunks = []
                    if calibrate_threshold:
                        calibration_cases.append(
                            {
                                # 中文注释：query 是与线上 Agent 相同的问题文本。
                                "query": query,
                                # 中文注释：正例梗标题标签；问候不产生向量候选。
                                "expected_meme_names": expected_names,
                                # 中文注释：标记该问题在梗库中应当没有相关结果。
                                "expected_empty": expected_empty,
                                # 中文注释：记录线上检索使用的候选数，供门槛评估截断。
                                "top_k": settings.retrieval_top_k,
                                # 中文注释：问候短路后不调用向量服务，因此候选为空。
                                "candidates": [],
                            }
                        )
                elif calibrate_threshold:
                    raw_chunks = await similarity_search(query, max(TOP_KS))
                    ranked_chunks = rank_vector_candidates(
                        query,
                        raw_chunks,
                        min_similarity=-1.0,
                        top_k=len(raw_chunks) or settings.retrieval_top_k,
                    )
                    calibration_cases.append(
                        {
                            # 中文注释：query 是与线上 Agent 相同的问题文本。
                            "query": query,
                            # 中文注释：由人工维护的正例梗标题标签。
                            "expected_meme_names": expected_names,
                            # 中文注释：标记该问题在梗库中应当没有相关结果。
                            "expected_empty": expected_empty,
                            # 中文注释：记录线上检索使用的候选数，供门槛评估截断。
                            "top_k": settings.retrieval_top_k,
                            # 中文注释：未过滤的向量候选及其原始余弦相似度。
                            "candidates": [
                                {
                                    # 中文注释：候选梗的业务 ID。
                                    "meme_id": chunk.meme_id,
                                    # 中文注释：候选梗标题，用于人工标签匹配。
                                    "title": chunk.title or "",
                                    # 中文注释：候选向量与查询向量的 cosine 相似度。
                                    "score": chunk.score,
                                }
                                for chunk in ranked_chunks
                            ],
                        }
                    )
                    chunks = ranked_chunks[: max(TOP_KS)]
                else:
                    chunks = await retrieve_meme_chunks(
                        query=query,
                        top_k=max(TOP_KS),
                    )
                latency_ms = (time.perf_counter() - started) * 1000
                retrieved_ids = [chunk.meme_id for chunk in chunks]
                retrieved_names = [chunk.title or "" for chunk in chunks]
                answer = ""
                evidence_titles = retrieved_names
                details_extra = {
                    "answer": None,
                    "evidence": [],
                    "errors": [],
                    "time_to_first_token_ms": None,
                    "usage": None,
                    "estimated_cost_usd": None,
                    "answer_grounding": None,
                }

            latencies.append(latency_ms)
            metrics = (
                score_retrieval_names(expected_names, retrieved_names)
                if expected_names
                else score_retrieval(expected_ids, retrieved_ids)
            )
            expected_facts = case.get("expected_answer_fact_groups") or []
            answer_grounding = details_extra["answer_grounding"]
            details.append(
                {
                    "query": query,
                    "expected_meme_ids": expected_ids,
                    "expected_meme_names": expected_names,
                    "expected_answer_fact_groups": expected_facts,
                    "expected_empty": expected_empty,
                    "retrieved_meme_ids": retrieved_ids,
                    "retrieved_meme_names": retrieved_names,
                    "latency_ms": round(latency_ms, 2),
                    "metrics": metrics,
                    "empty_query_correct": (not retrieved_ids) if expected_empty else None,
                    **details_extra,
                }
            )
    finally:
        await close_pool()

    labeled = [item for item in details if item["metrics"] is not None]
    empty_cases = [item for item in details if item["expected_empty"]]
    grounded_cases = [item["answer_grounding"] for item in details if item["answer_grounding"] is not None]
    answered = [item for item in details if item["answer"] is not None]
    available_costs = [item["estimated_cost_usd"] for item in answered if item["estimated_cost_usd"] is not None]
    summary: dict[str, Any] = {
        "query_count": len(details),
        "labeled_relevance_queries": len(labeled),
        "unlabeled_queries": len(details) - len(labeled) - len(empty_cases),
        "empty_query_count": len(empty_cases),
        "empty_query_accuracy": (
            sum(item["empty_query_correct"] for item in empty_cases) / len(empty_cases)
            if empty_cases
            else None
        ),
        "recall_at_5": (
            sum(item["metrics"]["recall_at_5"] for item in labeled) / len(labeled)
            if labeled
            else None
        ),
        "recall_at_10": (
            sum(item["metrics"]["recall_at_10"] for item in labeled) / len(labeled)
            if labeled
            else None
        ),
        "mrr": (
            sum(item["metrics"]["mrr"] for item in labeled) / len(labeled)
            if labeled
            else None
        ),
        "latency_p50_ms": _percentile(latencies, 0.50),
        "latency_p95_ms": _percentile(latencies, 0.95),
        "answer_evidence_alignment_rate": (
            sum(bool(item["grounded"]) for item in grounded_cases) / len(grounded_cases)
            if grounded_cases
            else None
        ),
        "answer_fact_coverage": (
            sum(float(item["expected_answer_fact_coverage"]) for item in grounded_cases)
            / len(grounded_cases)
            if grounded_cases
            else None
        ),
        "time_to_first_token_p50_ms": _percentile(first_token_latencies, 0.50),
        "time_to_first_token_p95_ms": _percentile(first_token_latencies, 0.95),
        "estimated_cost_usd_total": round(sum(available_costs), 10) if available_costs else None,
        "estimated_cost_usd_average": (
            round(sum(available_costs) / len(available_costs), 10) if available_costs else None
        ),
        "cost_estimate_available_queries": len(available_costs),
    }
    if calibrate_threshold:
        calibration = calibrate_vector_similarity_threshold(calibration_cases)
        threshold = float(calibration["threshold"])
        # 让校准报告中的检索结果和空结果指标都反映最终门槛，而不是未过滤的原始近邻。
        for detail, case in zip(details, calibration_cases):
            selected = [
                candidate
                for candidate in case["candidates"]
                if float(candidate["score"]) >= threshold
            ][: max(TOP_KS)]
            detail["retrieved_meme_ids"] = [str(candidate["meme_id"]) for candidate in selected]
            detail["retrieved_meme_names"] = [str(candidate["title"] or "") for candidate in selected]
            detail["metrics"] = (
                score_retrieval_names(
                    detail["expected_meme_names"], detail["retrieved_meme_names"]
                )
                if detail["expected_meme_names"]
                else score_retrieval(detail["expected_meme_ids"], detail["retrieved_meme_ids"])
            )
            detail["empty_query_correct"] = (
                not detail["retrieved_meme_ids"] if detail["expected_empty"] else None
            )

        labeled = [item for item in details if item["metrics"] is not None]
        empty_cases = [item for item in details if item["expected_empty"]]
        summary["empty_query_accuracy"] = (
            sum(item["empty_query_correct"] for item in empty_cases) / len(empty_cases)
            if empty_cases
            else None
        )
        summary["recall_at_5"] = (
            sum(item["metrics"]["recall_at_5"] for item in labeled) / len(labeled)
            if labeled
            else None
        )
        summary["recall_at_10"] = (
            sum(item["metrics"]["recall_at_10"] for item in labeled) / len(labeled)
            if labeled
            else None
        )
        summary["mrr"] = (
            sum(item["metrics"]["mrr"] for item in labeled) / len(labeled)
            if labeled
            else None
        )
        summary["vector_threshold_calibration"] = calibration
    return {"summary": summary, "queries": details}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", type=Path, default=DEFAULT_DATASET, help="中文注释：固定问题集 JSON 文件路径")
    parser.add_argument("--include-answers", action="store_true", help="中文注释：调用完整 SSE 搜索并评估回答质量")
    parser.add_argument("--calibrate-threshold", action="store_true", help="中文注释：按已标注正负例校准向量相关度门槛")
    parser.add_argument("--stream-usage", action="store_true", help="中文注释：请求模型返回 token usage；需模型供应商支持")
    parser.add_argument("--input-price-per-million", type=float, help="中文注释：输入 token 每百万个的价格，单位美元")
    parser.add_argument("--output-price-per-million", type=float, help="中文注释：输出 token 每百万个的价格，单位美元")
    parser.add_argument("--output", type=Path, help="中文注释：可选 JSON 报告输出文件路径")
    args = parser.parse_args()
    try:
        result = asyncio.run(
            evaluate_dataset(
                args.dataset,
                include_answers=args.include_answers,
                calibrate_threshold=args.calibrate_threshold,
                stream_usage=args.stream_usage,
                input_price_per_million=args.input_price_per_million,
                output_price_per_million=args.output_price_per_million,
            )
        )
    except ValueError as error:
        parser.error(str(error))
    rendered = json.dumps(result, ensure_ascii=False, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered + "\n", encoding="utf-8")
    print(rendered)


if __name__ == "__main__":
    main()
