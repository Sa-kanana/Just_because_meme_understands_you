"""运行标注过的梗检索与回答评估，并输出 JSON 指标。"""

from __future__ import annotations

import argparse
import asyncio
import json
import time
from pathlib import Path
from typing import Any

from app.agents.search_agent import stream_ai_search
from app.agents.tools import retrieve_meme_chunks
from app.core.settings import get_settings
from app.retrieval.db import close_pool, create_pool
from app.schemas.stream import StreamRequest

DEFAULT_DATASET = Path(__file__).with_name("golden_queries.json")
TOP_KS = (5, 10)


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
    stream_usage: bool = False,
    input_price_per_million: float | None = None,
    output_price_per_million: float | None = None,
) -> dict[str, Any]:
    cases = json.loads(dataset_path.read_text(encoding="utf-8"))
    if not isinstance(cases, list):
        raise ValueError("Golden query dataset must be a JSON array")
    if stream_usage and not include_answers:
        raise ValueError("--stream-usage 需要同时启用 --include-answers")

    settings = get_settings()
    settings.llm_stream_usage = stream_usage
    await create_pool(settings)
    details: list[dict[str, Any]] = []
    latencies: list[float] = []
    first_token_latencies: list[float] = []
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
                chunks = await retrieve_meme_chunks(
                    query=query,
                    hint_meme_ids=case.get("hint_meme_ids") or [],
                    mysql_snippets=case.get("snippets") or [],
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
    return {"summary": summary, "queries": details}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", type=Path, default=DEFAULT_DATASET, help="中文注释：固定问题集 JSON 文件路径")
    parser.add_argument("--include-answers", action="store_true", help="中文注释：调用完整 SSE 搜索并评估回答质量")
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
