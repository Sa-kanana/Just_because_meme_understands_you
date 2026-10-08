import asyncio
import importlib
import importlib.util

import httpx
import openai
import pytest

from app.core.settings import Settings
from app.agents import search_agent


def _settings(**values):
    return Settings(_env_file=None, INTERNAL_API_KEY="test", **values)


def _resilience():
    spec = importlib.util.find_spec("app.core.resilience")
    assert spec is not None, "app.core.resilience must expose the bounded retry policy"
    return importlib.import_module("app.core.resilience")


def test_resilience_settings_defaults():
    settings = _settings()

    assert settings.llm_request_timeout_seconds == 35
    assert settings.llm_max_retries == 2
    assert settings.retrieval_timeout_seconds == 12
    assert settings.retrieval_max_retries == 1
    assert settings.live_web_timeout_seconds == 25
    assert settings.agent_total_timeout_seconds == 120
    assert settings.llm_stream_usage is False


def test_resilience_settings_can_be_overridden_from_environment(monkeypatch):
    monkeypatch.setenv("LLM_REQUEST_TIMEOUT_SECONDS", "22")
    monkeypatch.setenv("LLM_MAX_RETRIES", "4")
    monkeypatch.setenv("RETRIEVAL_TIMEOUT_SECONDS", "8")
    monkeypatch.setenv("LLM_STREAM_USAGE", "true")

    settings = Settings(_env_file=None, INTERNAL_API_KEY="test")

    assert settings.llm_request_timeout_seconds == 22
    assert settings.llm_max_retries == 4
    assert settings.retrieval_timeout_seconds == 8
    assert settings.llm_stream_usage is True


def test_build_llm_passes_timeout_and_retry_limit(monkeypatch):
    captured = {}

    def fake_chat_openai(**kwargs):
        captured.update(kwargs)
        return object()

    monkeypatch.setattr(search_agent, "ChatOpenAI", fake_chat_openai)
    settings = _settings(OPENAI_API_KEY="provider-key")

    search_agent._build_llm(settings, max_tokens=100)

    assert captured["timeout"] == 35
    assert captured["max_retries"] == 2
    assert "stream_usage" not in captured


def test_build_llm_requests_stream_usage_only_when_enabled(monkeypatch):
    captured = {}

    def fake_chat_openai(**kwargs):
        captured.update(kwargs)
        return object()

    monkeypatch.setattr(search_agent, "ChatOpenAI", fake_chat_openai)
    settings = _settings(OPENAI_API_KEY="provider-key", LLM_STREAM_USAGE=True)

    search_agent._build_llm(settings, max_tokens=100)

    assert captured["stream_usage"] is True


def test_extract_usage_reads_provider_token_metadata_without_fabricating_values():
    from types import SimpleNamespace

    assert search_agent._extract_usage(
        SimpleNamespace(
            usage_metadata={"input_tokens": 10, "output_tokens": 4, "total_tokens": 14}
        )
    ) == {"prompt_tokens": 10, "completion_tokens": 4, "total_tokens": 14}
    assert search_agent._extract_usage(SimpleNamespace(usage_metadata=None)) == {}


async def test_run_with_retry_retries_transient_status_errors_with_bounded_backoff(monkeypatch):
    resilience = _resilience()
    attempts = 0
    delays = []

    async def no_delay(seconds):
        delays.append(seconds)

    monkeypatch.setattr(resilience, "_sleep", no_delay)

    async def operation():
        nonlocal attempts
        attempts += 1
        if attempts < 3:
            request = httpx.Request("GET", "https://example.test")
            response = httpx.Response(503, request=request)
            raise openai.APIStatusError("temporary", response=response, body=None)
        return "ok"

    result = await resilience.run_with_retry(
        operation,
        timeout_seconds=1,
        max_retries=2,
        stage="test",
        request_id="request-1",
    )

    assert result == "ok"
    assert attempts == 3
    assert delays == [0.5, 1.0]


async def test_run_with_retry_logs_stage_latency_and_attempt_metrics(monkeypatch, caplog):
    resilience = _resilience()
    attempts = 0
    caplog.set_level("INFO")

    async def no_delay(_seconds):
        return None

    async def operation():
        nonlocal attempts
        attempts += 1
        if attempts == 1:
            raise TimeoutError("transient")
        return "ok"

    monkeypatch.setattr(resilience, "_sleep", no_delay)

    assert await resilience.run_with_retry(
        operation,
        timeout_seconds=1,
        max_retries=1,
        stage="meme_retrieval",
        request_id="request-metrics",
    ) == "ok"

    metrics = next(record.getMessage() for record in caplog.records if "upstream_call_metrics" in record.getMessage())
    assert "stage=meme_retrieval" in metrics
    assert "outcome=success" in metrics
    assert "attempts=2" in metrics
    assert "retry_count=1" in metrics
    assert "duration_ms=" in metrics


async def test_run_with_retry_does_not_retry_non_transient_errors(monkeypatch):
    resilience = _resilience()
    attempts = 0

    async def no_delay(_seconds):
        raise AssertionError("non-transient errors must not back off")

    monkeypatch.setattr(resilience, "_sleep", no_delay)

    async def operation():
        nonlocal attempts
        attempts += 1
        request = httpx.Request("GET", "https://example.test")
        response = httpx.Response(400, request=request)
        raise openai.APIStatusError("invalid request", response=response, body=None)

    with pytest.raises(resilience.UpstreamRetriesExhausted) as exc_info:
        await resilience.run_with_retry(
            operation,
            timeout_seconds=1,
            max_retries=2,
            stage="test",
            request_id="request-2",
        )

    assert attempts == 1
    assert exc_info.value.attempts == 1


async def test_run_with_retry_applies_timeout_to_each_attempt(monkeypatch):
    resilience = _resilience()
    attempts = 0

    async def no_delay(_seconds):
        return None

    monkeypatch.setattr(resilience, "_sleep", no_delay)

    async def operation():
        nonlocal attempts
        attempts += 1
        await asyncio.sleep(0.02)
        return "too late"

    with pytest.raises(resilience.UpstreamRetriesExhausted) as exc_info:
        await resilience.run_with_retry(
            operation,
            timeout_seconds=0.001,
            max_retries=1,
            stage="test",
            request_id="request-3",
        )

    assert attempts == 2
    assert exc_info.value.attempts == 2
