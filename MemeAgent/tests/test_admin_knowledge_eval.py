import pytest

from app.eval import admin_knowledge_eval
from app.eval.admin_knowledge_eval import calibrate_admin_knowledge_threshold
from app.retrieval.knowledge_repository import RetrievedKnowledgeChunk
from app.retrieval.score_filter import rank_admin_knowledge_candidates


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


def test_admin_eval_preserves_zero_chunk_index_when_adapting_candidates():
    ordered = admin_knowledge_eval._rank_candidates(
        [
            {"doc_id": "z", "score": 0.8, "chunk_index": 0},
            {"doc_id": "a", "score": 0.8, "chunk_index": 0},
        ],
        threshold=0.5,
    )

    assert [candidate["doc_id"] for candidate in ordered] == ["a", "z"]


def test_rank_admin_knowledge_candidates_filters_and_reranks_before_section_dedup():
    candidates = [
        RetrievedKnowledgeChunk(
            doc_id="below-threshold",
            content="不相关向量候选",
            score=0.49,
            section_path="噪声章节",
            vector_rank=1,
            candidate_sources=("vector",),
            chunk_index=0,
        ),
        RetrievedKnowledgeChunk(
            doc_id="semantic-a",
            content="语义相关候选 A",
            score=0.80,
            section_path="语义 A",
            vector_rank=2,
            candidate_sources=("vector",),
            chunk_index=1,
        ),
        RetrievedKnowledgeChunk(
            doc_id="semantic-b",
            content="语义相关候选 B",
            score=0.75,
            section_path="语义 B",
            vector_rank=3,
            candidate_sources=("vector",),
            chunk_index=2,
        ),
        RetrievedKnowledgeChunk(
            doc_id="duplicate-section",
            content="高分章节副本",
            score=0.60,
            section_path="正文命中章节",
            vector_rank=4,
            candidate_sources=("vector",),
            chunk_index=3,
        ),
        RetrievedKnowledgeChunk(
            doc_id="title-hit",
            content="标题命中候选",
            score=0.30,
            exact_match=True,
            section_path="标题章节",
            lexical_rank=1,
            candidate_sources=("lexical",),
            chunk_index=4,
        ),
        RetrievedKnowledgeChunk(
            doc_id="content-hit",
            content="完整正文短语命中",
            score=0.40,
            content_match=True,
            section_path="正文命中章节",
            lexical_rank=2,
            candidate_sources=("lexical",),
            chunk_index=5,
        ),
    ]

    ranked = rank_admin_knowledge_candidates(candidates, min_similarity=0.5, top_k=3)

    assert [(item.doc_id, item.section_path) for item in ranked] == [
        ("title-hit", "标题章节"),
        ("content-hit", "正文命中章节"),
        ("semantic-a", "语义 A"),
    ]
    assert ranked[0].rank_score == pytest.approx(1 / 61)
    assert ranked[1].rank_score == pytest.approx(1 / 62)
    assert ranked[2].rank_score == pytest.approx(1 / 61)


def test_rank_admin_knowledge_candidates_without_threshold_keeps_only_lexical_hits():
    candidates = [
        RetrievedKnowledgeChunk(
            doc_id="vector-only",
            content="高相似向量候选",
            score=0.99,
            vector_rank=1,
            candidate_sources=("vector",),
            chunk_index=0,
        ),
        RetrievedKnowledgeChunk(
            doc_id="title",
            content="标题匹配",
            score=0.10,
            exact_match=True,
            section_path="标题",
            lexical_rank=1,
            candidate_sources=("lexical",),
            chunk_index=1,
        ),
        RetrievedKnowledgeChunk(
            doc_id="phrase",
            content="正文短语匹配",
            score=0.20,
            content_match=True,
            section_path="正文",
            lexical_rank=2,
            candidate_sources=("lexical",),
            chunk_index=2,
        ),
    ]

    ranked = rank_admin_knowledge_candidates(candidates, min_similarity=None)

    assert [item.doc_id for item in ranked] == ["title", "phrase"]


def test_admin_candidate_sources_are_authoritative_when_present():
    candidate = RetrievedKnowledgeChunk(
        doc_id="vector-hit",
        content="正文包含查询短语",
        score=0.8,
        content_match=True,
        candidate_sources=("vector",),
        chunk_index=0,
    )

    ranked = rank_admin_knowledge_candidates([candidate], min_similarity=0.5)

    assert len(ranked) == 1
    assert ranked[0].vector_rank == 1
    assert ranked[0].lexical_rank is None
    assert ranked[0].rank_score == pytest.approx(1 / 61)


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

    async def search(_query, candidate_limit):
        assert candidate_limit == 100
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
