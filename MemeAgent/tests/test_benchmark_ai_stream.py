import argparse

from scripts.benchmark_ai_stream import _build_payload, _consume_sse, percentile


def test_benchmark_parser_counts_first_token_and_error_events():
    lines = [
        b"event: meta\n",
        b"data: {}\n",
        b"\n",
        b"event: token\n",
        b"data: {\"text\":\"hello\"}\n",
        b"\n",
        b"event: error\n",
        b"data: {\"code\":\"failed\"}\n",
        b"\n",
    ]

    ttft_ms, token_events, error_events = _consume_sse(lines, 0)

    assert ttft_ms is not None
    assert token_events == 1
    assert error_events == 1


def test_benchmark_payload_uses_unique_request_ids_and_documented_defaults():
    args = argparse.Namespace(
        session_id="session-test",
        query="测试梗",
        max_tokens=128,
    )

    first = _build_payload(1, args, None)
    second = _build_payload(2, args, None)

    assert first["request_id"] != second["request_id"]
    assert first["session_id"] == "session-test"
    assert first["query"] == "测试梗"
    assert first["context"]["locale"] == "zh-CN"


def test_benchmark_percentile_handles_empty_and_single_sample():
    assert percentile([], 95) is None
    assert percentile([12.5], 95) == 12.5
