import json
from types import SimpleNamespace

from app.eval import retrieval_eval
from app.eval.retrieval_eval import (
    calibrate_vector_similarity_threshold,
    estimate_cost_usd,
    score_answer_grounding,
    score_retrieval,
    score_retrieval_names,
)
from app.retrieval.repository import RetrievedChunk


def test_score_retrieval_reports_recall_mrr_and_deduplicates_ids():
    metrics = score_retrieval(
        expected_ids=["a", "b"],
        retrieved_ids=["x", "b", "b", "a"],
        top_ks=(1, 3),
    )

    assert metrics == {
        "recall_at_1": 0.0,
        "recall_at_3": 1.0,
        "mrr": 0.5,
    }


def test_score_retrieval_skips_unlabeled_cases():
    assert score_retrieval(expected_ids=[], retrieved_ids=["a"], top_ks=(5,)) is None


def test_score_retrieval_names_uses_titles_case_insensitively():
    assert score_retrieval_names(
        expected_names=["AI水果"],
        retrieved_names=["SBTI", "AI水果"],
        top_ks=(1, 2),
    ) == {"recall_at_1": 0.0, "recall_at_2": 1.0, "mrr": 0.5}


def test_calibrates_similarity_threshold_from_relevant_and_empty_golden_cases():
    calibration = calibrate_vector_similarity_threshold(
        [
            {
                "query": "不明觉厉是什么意思？",
                "expected_meme_names": ["不明觉厉"],
                "candidates": [
                    {"meme_id": "60", "title": "不明觉厉", "score": 0.82},
                    {"meme_id": "9", "title": "普通梗", "score": 0.74},
                ],
            },
            {
                "query": "你好",
                "expected_empty": True,
                "candidates": [
                    {"meme_id": "9", "title": "普通梗", "score": 0.73},
                ],
            },
        ]
    )

    assert calibration["threshold"] == 0.82
    assert calibration["precision"] == 1.0
    assert calibration["recall"] == 1.0
    assert calibration["false_positive_count"] == 0


def test_calibrated_threshold_rounding_keeps_its_selected_positive_score():
    positive_score = 0.6322187
    calibration = calibrate_vector_similarity_threshold(
        [
            {
                # 中文注释：人工确认的目标梗标题标签。
                "expected_meme_names": ["目标梗"],
                # 中文注释：用于校准的正例候选向量分数。
                "candidates": [
                    {
                        # 中文注释：目标梗的测试 ID。
                        "meme_id": "1",
                        # 中文注释：应命中的梗标题。
                        "title": "目标梗",
                        # 中文注释：正例候选相似度边界。
                        "score": positive_score,
                    },
                ],
            },
            {
                # 中文注释：该问题被人工标记为没有相关梗。
                "expected_empty": True,
                # 中文注释：用于校准的负例候选向量分数。
                "candidates": [
                    {
                        # 中文注释：无关候选梗的测试 ID。
                        "meme_id": "2",
                        # 中文注释：无关候选梗标题。
                        "title": "无关梗",
                        # 中文注释：低于正例的候选相似度。
                        "score": 0.4,
                    },
                ],
            },
        ]
    )

    assert calibration["true_positive_count"] == 1
    assert calibration["threshold"] <= positive_score


def test_score_answer_grounding_checks_evidence_titles_and_fact_groups():
    metrics = score_answer_grounding(
        expected_meme_names=["养龙虾"],
        expected_answer_fact_groups=[["OpenClaw", "开源智能体"]],
        evidence_titles=["养龙虾"],
        answer="养龙虾是把安装 OpenClaw 智能体戏称为养龙虾。",
    )

    assert metrics == {
        "expected_meme_name_coverage": 1.0,
        "expected_answer_fact_coverage": 1.0,
        "grounded": True,
    }


def test_score_answer_grounding_flags_answer_without_matching_evidence():
    metrics = score_answer_grounding(
        expected_meme_names=["养龙虾"],
        expected_answer_fact_groups=[["OpenClaw"]],
        evidence_titles=["背手负鼠"],
        answer="养龙虾是把安装 OpenClaw 智能体戏称为养龙虾。",
    )

    assert metrics["expected_meme_name_coverage"] == 0.0
    assert metrics["expected_answer_fact_coverage"] == 1.0
    assert metrics["grounded"] is False


def test_score_answer_grounding_skips_unlabeled_cases():
    assert score_answer_grounding([], [], [], "回答") is None


def test_estimate_cost_usd_requires_usage_and_both_prices():
    usage = {"prompt_tokens": 1000, "completion_tokens": 500}

    assert estimate_cost_usd(None, 1.0, 2.0) is None
    assert estimate_cost_usd(usage, None, 2.0) is None
    assert estimate_cost_usd(usage, 1.0, None) is None


def test_estimate_cost_usd_uses_input_and_output_prices_per_million_tokens():
    assert estimate_cost_usd(
        {"prompt_tokens": 1_000_000, "completion_tokens": 500_000},
        input_price_per_million=0.5,
        output_price_per_million=2.0,
    ) == 1.5


async def test_full_answer_evaluation_collects_sse_evidence_and_token_usage(monkeypatch):
    async def fake_stream(_request):
        yield 'event: meta\ndata: {"retrieved":["meme-1"]}\n\n'
        yield 'event: cite\ndata: {"meme_id":"meme-1","title":"养龙虾","score":0.9}\n\n'
        yield 'event: token\ndata: {"text":"OpenClaw 是龙虾智能体。"}\n\n'
        yield 'event: done\ndata: {"usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}\n\n'

    monkeypatch.setattr(retrieval_eval, "stream_ai_search", fake_stream)

    result = await retrieval_eval._evaluate_answer(
        "网络上说养龙虾指的是什么？",
        {"hint_meme_ids": [], "snippets": []},
        0,
    )

    assert result["answer"] == "OpenClaw 是龙虾智能体。"
    assert result["evidence_titles"] == ["养龙虾"]
    assert result["retrieved_meme_ids"] == ["meme-1"]
    assert result["usage"] == {
        "prompt_tokens": 10,
        "completion_tokens": 5,
        "total_tokens": 15,
    }
    assert result["time_to_first_token_ms"] is not None


async def test_retrieval_evaluation_skips_social_greetings_in_both_modes(tmp_path, monkeypatch):
    dataset = tmp_path / "golden.json"
    dataset.write_text(
        json.dumps(
            [
                {
                    # 中文注释：完整纯问候问题，应短路向量检索。
                    "query": "你好",
                    # 中文注释：问候问题应返回空梗结果。
                    "expected_empty": True,
                },
                {
                    # 中文注释：正向梗问题用于验证检索仍会执行。
                    "query": "不明觉厉是什么意思？",
                    # 中文注释：人工标记的正确梗标题。
                    "expected_meme_names": ["不明觉厉"],
                },
                {
                    # 中文注释：与梗无关的问题仍进入检索并受门槛过滤。
                    "query": "太阳系有几颗行星？",
                    # 中文注释：该问题没有相关梗结果。
                    "expected_empty": True,
                },
            ],
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )
    search_calls = []
    retrieved_chunk = RetrievedChunk("60", "不明觉厉", 0.9, "不明觉厉")

    async def fake_search(query, _top_k):
        search_calls.append(query)
        if query == "你好":
            return [RetrievedChunk("1", "好家伙", 0.99, "好家伙")]
        if query == "不明觉厉是什么意思？":
            return [retrieved_chunk]
        return [RetrievedChunk("2", "宇宙", 0.2, "宇宙")]

    async def fake_retrieve(query, top_k):
        chunks = await fake_search(query, top_k)
        return chunks if query == "不明觉厉是什么意思？" else []

    async def no_pool(*_args, **_kwargs):
        return None

    monkeypatch.setattr(retrieval_eval, "create_pool", no_pool)
    monkeypatch.setattr(retrieval_eval, "close_pool", no_pool)
    monkeypatch.setattr(retrieval_eval, "similarity_search", fake_search)
    monkeypatch.setattr(retrieval_eval, "retrieve_meme_chunks", fake_retrieve)

    regular = await retrieval_eval.evaluate_dataset(dataset)
    assert regular["queries"][0]["retrieved_meme_ids"] == []
    assert regular["queries"][0]["empty_query_correct"] is True
    assert regular["summary"]["empty_query_accuracy"] == 1.0
    assert "你好" not in search_calls

    search_calls.clear()
    calibrated = await retrieval_eval.evaluate_dataset(dataset, calibrate_threshold=True)
    assert "你好" not in search_calls
    assert calibrated["summary"]["vector_threshold_calibration"]["positive_label_count"] == 1


async def test_calibration_report_scores_ten_results_separately_from_serving_top_five(
    tmp_path, monkeypatch
):
    dataset = tmp_path / "golden-top-ten.json"
    dataset.write_text(
        json.dumps(
            [
                {
                    # 中文注释：语义检索问题，不含目标标题以避免标题优先级改变排名。
                    "query": "这个表达代表什么？",
                    # 中文注释：人工确认的目标梗标题。
                    "expected_meme_names": ["目标梗"],
                }
            ],
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )
    requested_top_k = []
    candidates = [
        RetrievedChunk(str(index), f"无关梗{index}", 0.99 - index * 0.05, f"无关梗{index}")
        for index in range(1, 7)
    ]
    candidates.append(RetrievedChunk("target", "目标梗", 0.5, "目标梗"))

    async def fake_search(query, top_k):
        requested_top_k.append(top_k)
        return candidates

    async def no_pool(*_args, **_kwargs):
        return None

    monkeypatch.setattr(
        retrieval_eval,
        "get_settings",
        lambda: SimpleNamespace(retrieval_top_k=5, llm_stream_usage=False),
    )
    monkeypatch.setattr(retrieval_eval, "create_pool", no_pool)
    monkeypatch.setattr(retrieval_eval, "close_pool", no_pool)
    monkeypatch.setattr(retrieval_eval, "similarity_search", fake_search)
    monkeypatch.setattr(
        retrieval_eval,
        "calibrate_vector_similarity_threshold",
        lambda _cases: {
            "threshold": 0.5,
            "precision": 1.0,
            "recall": 1.0,
            "f1": 1.0,
            "true_positive_count": 1,
            "false_positive_count": 0,
            "false_negative_count": 0,
            "positive_label_count": 1,
            "labeled_query_count": 1,
        },
    )

    report = await retrieval_eval.evaluate_dataset(dataset, calibrate_threshold=True)

    assert requested_top_k == [10]
    assert report["summary"]["recall_at_5"] == 0.0
    assert report["summary"]["recall_at_10"] == 1.0
    assert report["queries"][0]["retrieved_meme_names"][-1] == "目标梗"
