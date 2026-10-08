from app.eval import retrieval_eval
from app.eval.retrieval_eval import (
    estimate_cost_usd,
    score_answer_grounding,
    score_retrieval,
    score_retrieval_names,
)


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
