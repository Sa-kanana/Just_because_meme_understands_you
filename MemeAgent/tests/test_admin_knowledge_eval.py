import pytest

from app.eval import admin_knowledge_eval
from app.eval.admin_knowledge_eval import calibrate_admin_knowledge_threshold


def test_admin_knowledge_calibration_selects_threshold_from_labeled_results():
    report = calibrate_admin_knowledge_threshold(
        [
            {
                "query": "相关问题",
                "expected_doc_ids": ["relevant"],
                "candidates": [
                    {"doc_id": "relevant", "score": 0.82},
                    {"doc_id": "nearby", "score": 0.41},
                ],
            },
            {
                "query": "无关问题",
                "expected_empty": True,
                "candidates": [{"doc_id": "nearby", "score": 0.41}],
            },
        ]
    )

    assert report["threshold"] == 0.82
    assert report["precision"] == 1.0
    assert report["recall"] == 1.0
    assert report["false_positive_count"] == 0
    assert report["precision_at_k"] == 0.2
    assert report["recall_at_k"] == 1.0
    assert report["mrr"] == 1.0
    assert report["empty_query_accuracy"] == 1.0


def test_admin_knowledge_fusion_rank_uses_exact_and_content_matches_before_rrf():
    ordered = admin_knowledge_eval._rank_candidates(
        [
            {
                "doc_id": "semantic",
                "score": 0.94,
                "vector_rank_score": 0.016,
                "lexical_rank_score": 0.0,
            },
            {
                "doc_id": "content",
                "score": 0.50,
                "content_match": True,
                "vector_rank_score": 0.0,
                "lexical_rank_score": 0.016,
            },
            {
                "doc_id": "title",
                "score": 0.30,
                "exact_match": True,
                "vector_rank_score": 0.0,
                "lexical_rank_score": 0.016,
            },
        ],
        threshold=0.70,
    )

    assert [candidate["doc_id"] for candidate in ordered] == ["title", "content", "semantic"]


@pytest.mark.asyncio
async def test_admin_eval_collects_unlabeled_candidates_before_threshold_calibration(
    monkeypatch, tmp_path
):
    dataset = tmp_path / "queries.json"
    dataset.write_text('[{"query":"上传限制是什么？"}]', encoding="utf-8")
    closed = []

    async def create_pool(_settings):
        return None

    async def close_pool():
        closed.append(True)

    async def search(_query, _top_k, _minimum):
        return [
            type(
                "Hit",
                (),
                {
                    "doc_id": "doc-1",
                    "score": 0.84,
                    "rank_score": 0.03,
                    "vector_rank_score": 0.02,
                    "lexical_rank_score": 0.01,
                    "exact_match": False,
                    "content_match": True,
                    "title": "上传规则",
                    "section_path": "文件大小",
                    "page_number": None,
                },
            )()
        ]

    monkeypatch.setattr(admin_knowledge_eval, "get_settings", lambda: object())
    monkeypatch.setattr(admin_knowledge_eval, "create_pool", create_pool)
    monkeypatch.setattr(admin_knowledge_eval, "close_pool", close_pool)
    monkeypatch.setattr(admin_knowledge_eval, "similarity_search_knowledge", search)

    result = await admin_knowledge_eval.evaluate_admin_knowledge_dataset(dataset)

    assert result["calibration"] is None
    assert result["queries"][0]["candidates"][0]["doc_id"] == "doc-1"
    assert result["queries"][0]["candidates"][0]["content_match"] is True
    assert result["latency_p50_ms"] is not None
    assert closed == [True]


def test_admin_knowledge_calibration_requires_positive_labels():
    with pytest.raises(ValueError, match="正例"):
        calibrate_admin_knowledge_threshold([{"query": "无关", "expected_empty": True}])


def test_admin_knowledge_calibration_mirrors_section_limit_and_counts_documents_once():
    report = calibrate_admin_knowledge_threshold(
        [
            {
                "query": "规则",
                "expected_doc_ids": ["relevant", "other"],
                "top_k": 2,
                "candidates": [
                    {"doc_id": "relevant", "section_path": "登录", "score": 0.90},
                    {"doc_id": "relevant", "section_path": "登录", "score": 0.89},
                    {"doc_id": "relevant", "section_path": "注册", "score": 0.85},
                    {"doc_id": "other", "section_path": "处罚", "score": 0.80},
                ],
            }
        ]
    )

    assert report["threshold"] == 0.9
    assert report["precision"] == 1.0
    assert report["true_positive_count"] == 1
    assert report["false_positive_count"] == 0
    assert report["false_negative_count"] == 1
