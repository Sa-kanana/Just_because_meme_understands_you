import json
from pathlib import Path


DATASET = Path(__file__).parents[1] / "app" / "eval" / "golden_queries.json"


def test_golden_queries_are_fixed_and_have_relevance_labels():
    cases = json.loads(DATASET.read_text(encoding="utf-8"))

    assert len(cases) >= 5
    assert len({case["query"] for case in cases}) == len(cases)
    assert all(case["query"].strip() for case in cases)
    assert all(case.get("expected_meme_names") for case in cases)
    assert all(case.get("expected_answer_fact_groups") for case in cases)


def test_golden_query_fact_groups_contain_non_empty_alternatives():
    cases = json.loads(DATASET.read_text(encoding="utf-8"))

    for case in cases:
        assert all(
            isinstance(group, list) and any(str(item).strip() for item in group)
            for group in case["expected_answer_fact_groups"]
        )
