import asyncio
import json
from types import SimpleNamespace

import pytest

from app.agents.tools import chunks_to_tool_payload, parse_tool_payload
from app.retrieval.repository import RetrievedChunk
from app.agents import tools as agent_tools
from app.agents import meme_agent
from app.agents import search_agent
from app.agents.prompts import AGENT_SYSTEM_PROMPT
from app.core.settings import Settings
from app.retrieval.knowledge_repository import RetrievedKnowledgeChunk
from app.schemas.stream import StreamRequest


def test_tool_payload_roundtrip():
    chunks = [
        RetrievedChunk(meme_id="12", content="标题: 只因\n内容", score=0.88, title="只因"),
    ]
    raw = chunks_to_tool_payload(chunks)
    parsed = parse_tool_payload(raw)
    payload = json.loads(raw)

    assert payload["source"] == "meme_retrieval"
    assert payload["trust_level"] == "untrusted"
    assert payload["items"][0]["meme_id"] == "12"
    assert len(parsed) == 1
    assert parsed[0].meme_id == "12"
    assert parsed[0].title == "只因"
    assert parsed[0].score == 0.88


def test_retrieval_tool_output_schemas_constrain_provenance_and_items():
    for schema_name in (
        "MemeRetrievalToolResult",
        "AdminKnowledgeToolResult",
    ):
        schema_model = getattr(agent_tools, schema_name, None)
        assert schema_model is not None
        schema = schema_model.model_json_schema()
        assert {"source", "trust_level", "items"} <= set(schema["properties"])
        assert "trust_level" in schema["required"]
        assert schema["properties"]["trust_level"]["const"] == "untrusted"


def test_build_meme_search_agent_imports():
    from app.agents.meme_agent import build_meme_search_agent
    from app.agents.tools import build_search_meme_tool

    assert callable(build_meme_search_agent)
    assert callable(build_search_meme_tool)


def test_agent_tool_allowlist_stays_fixed_and_read_only():
    assert meme_agent.AGENT_TOOL_ALLOWLIST == frozenset(
        {
            "search_meme_knowledge",
            "search_admin_knowledge",
        }
    )
    assert not hasattr(agent_tools, "build_search_live_web_tool")
    with pytest.raises(ValueError, match="outside the fixed allowlist"):
        meme_agent.build_meme_search_agent(object(), [SimpleNamespace(name="run_sql")])


def test_system_prompt_uses_only_database_retrieval_sources():
    assert "search_live_meme_web" not in AGENT_SYSTEM_PROMPT
    assert "live_web_retrieval" not in AGENT_SYSTEM_PROMPT
    assert "meme_retrieval" in AGENT_SYSTEM_PROMPT
    assert "admin_knowledge_retrieval" in AGENT_SYSTEM_PROMPT


def test_agent_builder_installs_the_canonical_fixed_system_prompt(monkeypatch):
    captured = {}

    def create_agent(_llm, tools, prompt):
        captured.update(tools=tools, prompt=prompt)
        return object()

    monkeypatch.setattr(meme_agent, "create_tool_calling_agent", create_agent)
    monkeypatch.setattr(meme_agent, "AgentExecutor", lambda **kwargs: kwargs)
    tool = SimpleNamespace(name="search_meme_knowledge")

    meme_agent.build_meme_search_agent(object(), [tool])

    assert captured["prompt"].messages[0].prompt.template == AGENT_SYSTEM_PROMPT
    assert captured["tools"] == [tool]


async def test_search_meme_tool_reuses_initial_retrieval_once(monkeypatch):
    initial = [RetrievedChunk(meme_id="12", content="标题: 只因", score=0.8, title="只因")]
    calls = []

    async def retrieve_again(**kwargs):
        calls.append(kwargs["query"])
        return []

    monkeypatch.setattr(agent_tools, "retrieve_meme_chunks", retrieve_again)
    tool = agent_tools.build_search_meme_tool(
        initial_query="只因你太美是什么梗",
        initial_chunks=initial,
    )

    first = await tool.ainvoke({"query": "只因你太美是什么梗"})
    second = await tool.ainvoke({"query": "只因你太美的梗"})

    assert [chunk.meme_id for chunk in parse_tool_payload(first)] == ["12"]
    assert parse_tool_payload(second) == []
    assert calls == ["只因你太美的梗"]


async def test_degraded_search_tool_does_not_fall_back_to_business_candidates(monkeypatch):
    calls = []

    async def retrieve_again(**kwargs):
        calls.append(kwargs["query"])
        return []

    monkeypatch.setattr(agent_tools, "retrieve_meme_chunks", retrieve_again)
    tool = agent_tools.build_search_meme_tool(
        initial_query="原始问题",
        retrieval_unavailable=True,
    )

    result = await tool.ainvoke({"query": "改写后的检索词"})

    assert parse_tool_payload(result) == []
    payload = json.loads(result)
    assert payload["source"] == "meme_retrieval"
    assert payload["trust_level"] == "untrusted"
    assert payload["unavailable"] is True
    assert calls == []


async def test_rewritten_query_retry_exhaustion_returns_cached_candidates_once(monkeypatch):
    from app.core.resilience import UpstreamRetriesExhausted

    retrieval_calls = []

    async def retrieval_exhausted(**kwargs):
        retrieval_calls.append(kwargs["query"])
        raise UpstreamRetriesExhausted("meme_retrieval", 2)

    initial = [RetrievedChunk(meme_id="m1", content="标题: 已缓存候选", score=0.7, title="已缓存候选")]
    monkeypatch.setattr(agent_tools, "retrieve_meme_chunks", retrieval_exhausted)
    tool = agent_tools.build_search_meme_tool(
        initial_query="原始问题",
        initial_chunks=initial,
    )

    first = json.loads(await tool.ainvoke({"query": "改写查询一"}))
    second = json.loads(await tool.ainvoke({"query": "改写查询二"}))

    for result in (first, second):
        assert result["source"] == "meme_retrieval"
        assert result["trust_level"] == "untrusted"
        assert result["unavailable"] is True
        assert result["items"][0]["meme_id"] == "m1"
    assert retrieval_calls == ["改写查询一"]


def _settings_for_stream(**values):
    return Settings(_env_file=None, INTERNAL_API_KEY="test", **values)


def _request(*, extra=None, query="只因是什么梗"):
    return StreamRequest(
        request_id="request-test",
        session_id="session-test",
        query=query,
        context={"locale": "zh-CN", "extra": extra or {}},
    )


def _decode_sse(events):
    decoded = []
    for block in "".join(events).split("\n\n"):
        if not block.strip():
            continue
        lines = block.splitlines()
        name = next(line[7:] for line in lines if line.startswith("event: "))
        payload = json.loads(next(line[6:] for line in lines if line.startswith("data: ")))
        decoded.append((name, payload))
    return decoded


@pytest.fixture(autouse=True)
def _default_admin_knowledge_retrieval(monkeypatch):
    async def no_admin_hits(*_args, **_kwargs):
        return []

    monkeypatch.setattr(
        search_agent,
        "retrieve_admin_knowledge_chunks",
        no_admin_hits,
        raising=False,
    )


async def test_agent_prefetches_admin_knowledge_into_untrusted_json_context(monkeypatch):
    retrieved = [
        RetrievedKnowledgeChunk(
            doc_id="doc-17",
            content="标题: 上传规则\n章节: 文件大小\n\n单个文件最大 20 MB。",
            score=0.91,
            title="上传规则",
            section_path="文件大小",
        )
    ]
    captured = {}

    async def retrieve_admin(query, top_k, request_id):
        captured["admin_query"] = (query, top_k, request_id)
        return retrieved

    class AnswerExecutor:
        async def astream_events(self, input_data, **_kwargs):
            captured["input"] = input_data
            yield {
                "event": "on_chat_model_start",
                "run_id": "answer-run",
                "data": {},
            }
            yield {
                "event": "on_chat_model_stream",
                "run_id": "answer-run",
                "data": {"chunk": SimpleNamespace(content="单个文件最大 20 MB。", tool_call_chunks=[])},
            }
            yield {
                "event": "on_chat_model_end",
                "run_id": "answer-run",
                "data": {"output": SimpleNamespace(tool_calls=[])},
            }

    monkeypatch.setattr(search_agent, "get_settings", lambda: _settings_for_stream())
    monkeypatch.setattr(search_agent, "retrieve_meme_chunks", lambda **_kwargs: _empty_async_list())
    monkeypatch.setattr(search_agent, "retrieve_admin_knowledge_chunks", retrieve_admin)
    monkeypatch.setattr(search_agent, "_build_llm", lambda *_args: object())
    monkeypatch.setattr(
        search_agent,
        "build_meme_search_agent",
        lambda _llm, tools, **_kwargs: captured.update(tool_names=[tool.name for tool in tools]) or AnswerExecutor(),
    )

    events = _decode_sse(
        [event async for event in search_agent.stream_ai_search(_request(query="上传文件有什么限制？"))]
    )
    payload = json.loads(captured["input"]["input"])
    sources = payload["context"]["retrieval_context"]["sources"]

    assert captured["admin_query"] == ("上传文件有什么限制？", 5, "request-test")
    assert captured["tool_names"] == ["search_meme_knowledge", "search_admin_knowledge"]
    assert sources[1]["source"] == "admin_knowledge_retrieval"
    assert sources[1]["trust_level"] == "untrusted"
    assert sources[1]["items"][0]["doc_id"] == "doc-17"
    assert events[-1][0] == "done"


async def _empty_async_list():
    return []


async def test_retrieve_meme_chunks_retries_a_transient_failure(monkeypatch):
    calls = 0
    expected = [RetrievedChunk(meme_id="m1", content="梗", score=0.9, title="梗")]

    async def similarity_search(_query, _top_k):
        nonlocal calls
        calls += 1
        if calls == 1:
            raise TimeoutError("transient")
        return expected

    async def no_delay(_seconds):
        return None

    monkeypatch.setattr(agent_tools, "get_settings", lambda: _settings_for_stream())
    monkeypatch.setattr(agent_tools, "similarity_search", similarity_search)
    monkeypatch.setattr("app.core.resilience._sleep", no_delay)

    actual = await agent_tools.retrieve_meme_chunks("q", request_id="request-test")

    assert actual == expected
    assert calls == 2


def test_database_tool_queries_have_bounded_length():
    from pydantic import ValidationError

    for input_model, maximum in (
        (agent_tools.SearchMemeInput, 2000),
        (agent_tools.SearchAdminKnowledgeInput, 500),
    ):
        with pytest.raises(ValidationError):
            input_model.model_validate({"query": "x" * (maximum + 1)})
        with pytest.raises(ValidationError):
            input_model.model_validate({"query": "   "})
    assert agent_tools.SearchAdminKnowledgeInput.model_validate({"query": "  管理规则  "}).query == "管理规则"


@pytest.mark.parametrize(
    "input_model",
    [
        agent_tools.SearchMemeInput,
        agent_tools.SearchAdminKnowledgeInput,
    ],
)
def test_tool_argument_json_schema_rejects_undeclared_fields(input_model):
    from pydantic import ValidationError

    schema = input_model.model_json_schema()

    assert schema["additionalProperties"] is False
    with pytest.raises(ValidationError):
        input_model.model_validate({"query": "正常检索", "unexpected": "额外字段"})


async def test_admin_knowledge_retrieval_retries_and_hides_provider_errors(monkeypatch):
    calls = 0

    async def search_knowledge(_query, _top_k, _min_similarity=None):
        nonlocal calls
        calls += 1
        if calls == 1:
            raise TimeoutError("transient")
        return []

    async def no_delay(_seconds):
        return None

    monkeypatch.setattr(agent_tools, "get_settings", lambda: _settings_for_stream())
    monkeypatch.setattr(agent_tools, "similarity_search_knowledge", search_knowledge)
    monkeypatch.setattr("app.core.resilience._sleep", no_delay)
    tool = agent_tools.build_search_admin_knowledge_tool(request_id="request-test")

    successful_result = json.loads(await tool.ainvoke({"query": "规则"}))
    assert successful_result == {
        "source": "admin_knowledge_retrieval",
        "trust_level": "untrusted",
        "unavailable": False,
        "items": [],
    }
    assert calls == 2

    async def search_unavailable(_query, _top_k, _min_similarity=None):
        raise RuntimeError("provider secret detail")

    monkeypatch.setattr(agent_tools, "similarity_search_knowledge", search_unavailable)
    safe_failure = json.loads(await tool.ainvoke({"query": "规则"}))

    assert safe_failure == {
        "source": "admin_knowledge_retrieval",
        "trust_level": "untrusted",
        "unavailable": True,
        "items": [],
    }


async def test_zero_hits_and_retrieval_outage_return_only_unknown_without_evidence(monkeypatch):
    monkeypatch.setattr(search_agent, "get_settings", lambda: _settings_for_stream())
    monkeypatch.setattr(search_agent, "_build_llm", lambda *_args: None)

    async def no_hits(**_kwargs):
        return []

    monkeypatch.setattr(search_agent, "retrieve_meme_chunks", no_hits)
    no_hit_events = _decode_sse([event async for event in search_agent.stream_ai_search(_request())])
    no_hit_answer = "".join(payload["text"] for name, payload in no_hit_events if name == "token")

    async def retrieval_failed(**_kwargs):
        from app.core.resilience import UpstreamRetriesExhausted

        raise UpstreamRetriesExhausted("retrieval", 2)

    monkeypatch.setattr(search_agent, "retrieve_meme_chunks", retrieval_failed)
    outage_events = _decode_sse([event async for event in search_agent.stream_ai_search(_request())])
    outage_answer = "".join(payload["text"] for name, payload in outage_events if name == "token")

    assert "检索" not in no_hit_answer
    assert "检索" not in outage_answer
    assert "梗库" not in outage_answer
    expected_no_result = (
        "我这边暂时还没找到和你这个问题特别相关的内容，你可以换个说法，"
        "或者再补充一点信息，我再帮你找找。"
    )
    assert no_hit_answer == expected_no_result
    assert outage_answer == expected_no_result
    assert no_hit_events[-1][0] == outage_events[-1][0] == "done"
    assert no_hit_events[-1][1]["finish_reason"] != outage_events[-1][1]["finish_reason"]


async def test_pure_greeting_skips_retrieval_and_returns_empty_related_memes(monkeypatch):
    retrieval_calls = []

    async def retrieval_should_not_run(**kwargs):
        retrieval_calls.append(kwargs)
        return []

    monkeypatch.setattr(search_agent, "get_settings", lambda: _settings_for_stream())
    monkeypatch.setattr(search_agent, "retrieve_meme_chunks", retrieval_should_not_run)
    monkeypatch.setattr(
        search_agent,
        "_build_llm",
        lambda *_args: (_ for _ in ()).throw(AssertionError("greetings must not call the model")),
    )

    events = _decode_sse([event async for event in search_agent.stream_ai_search(_request(query="你好！"))])
    related_meme_ids = next(payload["retrieved"] for name, payload in events if name == "meta")
    answer = "".join(payload["text"] for name, payload in events if name == "token")

    assert retrieval_calls == []
    assert related_meme_ids == []
    assert answer == "你好！"
    assert events[-1][0] == "done"


async def test_retrieval_outage_does_not_use_java_keyword_candidate(monkeypatch):
    from app.core.resilience import UpstreamRetriesExhausted

    monkeypatch.setattr(search_agent, "get_settings", lambda: _settings_for_stream())
    monkeypatch.setattr(search_agent, "_build_llm", lambda *_args: None)

    async def retrieval_failed(**_kwargs):
        raise UpstreamRetriesExhausted("retrieval", 2)

    monkeypatch.setattr(search_agent, "retrieve_meme_chunks", retrieval_failed)
    request = _request(
        extra={
            "snippets": [
                {"meme_id": "meme_12", "title": "候选梗", "introduction": "Java 的公开候选"}
            ]
        }
    )

    events = _decode_sse([event async for event in search_agent.stream_ai_search(request)])
    answer = "".join(payload["text"] for name, payload in events if name == "token")

    assert answer == (
        "我这边暂时还没找到和你这个问题特别相关的内容，你可以换个说法，"
        "或者再补充一点信息，我再帮你找找。"
    )
    assert all(name != "cite" for name, _ in events)
    assert events[-1][0] == "done"
    assert events[-1][1]["finish_reason"] == "fallback_retrieval_unavailable"


async def test_agent_tool_does_not_spend_a_second_retrieval_budget_after_outage(monkeypatch):
    from app.core.resilience import UpstreamRetriesExhausted

    retrieval_calls = 0
    tool_results = []
    tools = []

    async def retrieval_failed(**_kwargs):
        nonlocal retrieval_calls
        retrieval_calls += 1
        raise UpstreamRetriesExhausted("meme_retrieval", 2)

    class ToolCallingExecutor:
        async def astream_events(self, *_args, **_kwargs):
            output = await tools[0].ainvoke({"query": "改写后的检索问题"})
            tool_results.append(json.loads(output))
            yield {"event": "on_tool_end", "data": {"output": output}}
            yield {
                "event": "on_chat_model_stream",
                "run_id": "answer-run",
                "data": {"chunk": SimpleNamespace(content="根据候选梗回答", tool_call_chunks=[])},
            }
            yield {
                "event": "on_chat_model_end",
                "run_id": "answer-run",
                "data": {"output": SimpleNamespace(tool_calls=[])},
            }

    def build_agent(_llm, provided_tools, **_kwargs):
        tools.extend(provided_tools)
        return ToolCallingExecutor()

    monkeypatch.setattr(search_agent, "get_settings", lambda: _settings_for_stream())
    monkeypatch.setattr(search_agent, "retrieve_meme_chunks", retrieval_failed)
    monkeypatch.setattr(search_agent, "_build_llm", lambda *_args: object())
    monkeypatch.setattr(search_agent, "build_meme_search_agent", build_agent)
    request = _request(
        extra={
            "snippets": [
                {"meme_id": "meme_12", "title": "Java 候选", "introduction": "公开梗摘要"}
            ]
        }
    )

    events = _decode_sse([event async for event in search_agent.stream_ai_search(request)])

    assert retrieval_calls == 1
    assert tool_results[0]["unavailable"] is True
    assert tool_results[0]["items"] == []
    assert events[-1][0] == "done"


async def test_pre_token_model_failure_emits_complete_fallback(monkeypatch):
    chunks = [RetrievedChunk(meme_id="m1", content="标题: 候选梗", score=0.8, title="候选梗")]

    async def retrieval(**_kwargs):
        return chunks

    class FailedExecutor:
        async def astream_events(self, *_args, **_kwargs):
            if False:
                yield {}
            raise RuntimeError("provider secret detail")

    monkeypatch.setattr(search_agent, "get_settings", lambda: _settings_for_stream())
    monkeypatch.setattr(search_agent, "retrieve_meme_chunks", retrieval)
    monkeypatch.setattr(search_agent, "_build_llm", lambda *_args: object())
    monkeypatch.setattr(search_agent, "build_meme_search_agent", lambda *_args, **_kwargs: FailedExecutor())

    events = _decode_sse([event async for event in search_agent.stream_ai_search(_request())])

    assert any(name == "token" for name, _payload in events)
    assert events[-1][0] == "done"
    assert events[-1][1]["finish_reason"] == "fallback_llm_error"


async def test_post_token_model_failure_emits_error_without_done(monkeypatch):
    async def retrieval(**_kwargs):
        return []

    class PartialFailureExecutor:
        async def astream_events(self, *_args, **_kwargs):
            yield {
                "event": "on_chat_model_stream",
                "run_id": "answer-run",
                "data": {"chunk": SimpleNamespace(content="部分回答", tool_call_chunks=[])},
            }
            yield {
                "event": "on_chat_model_end",
                "run_id": "answer-run",
                "data": {"output": SimpleNamespace(tool_calls=[])},
            }
            raise RuntimeError("provider secret detail")

    monkeypatch.setattr(search_agent, "get_settings", lambda: _settings_for_stream())
    monkeypatch.setattr(search_agent, "retrieve_meme_chunks", retrieval)
    monkeypatch.setattr(search_agent, "_build_llm", lambda *_args: object())
    monkeypatch.setattr(
        search_agent,
        "build_meme_search_agent",
        lambda *_args, **_kwargs: PartialFailureExecutor(),
    )

    events = _decode_sse([event async for event in search_agent.stream_ai_search(_request())])

    assert any(name == "token" for name, _payload in events)
    assert events[-1][0] == "error"
    assert not any(name == "done" for name, _payload in events)
    assert "provider secret detail" not in str(events[-1][1])


async def test_stream_drops_intermediate_tool_text_and_forwards_only_final_text(monkeypatch):
    async def retrieval(**_kwargs):
        return []

    class ToolThenAnswerExecutor:
        async def astream_events(self, *_args, **_kwargs):
            yield {
                "event": "on_chat_model_stream",
                "run_id": "tool-run",
                "data": {
                    "chunk": SimpleNamespace(
                        content="内部工具参数不应展示",
                        tool_call_chunks=[{"name": "search_meme_knowledge"}],
                    )
                },
            }
            yield {
                "event": "on_chat_model_stream",
                "run_id": "answer-run",
                "data": {"chunk": SimpleNamespace(content="最终答案", tool_call_chunks=[])},
            }
            yield {
                "event": "on_chat_model_end",
                "run_id": "tool-run",
                "data": {"output": SimpleNamespace(tool_calls=[{"name": "search_meme_knowledge"}])},
            }
            yield {
                "event": "on_chat_model_end",
                "run_id": "answer-run",
                "data": {"output": SimpleNamespace(tool_calls=[])},
            }

    monkeypatch.setattr(search_agent, "get_settings", lambda: _settings_for_stream())
    monkeypatch.setattr(search_agent, "retrieve_meme_chunks", retrieval)
    monkeypatch.setattr(search_agent, "_build_llm", lambda *_args: object())
    monkeypatch.setattr(search_agent, "build_meme_search_agent", lambda *_args, **_kwargs: ToolThenAnswerExecutor())

    events = _decode_sse([event async for event in search_agent.stream_ai_search(_request())])
    answer = "".join(payload["text"] for name, payload in events if name == "token")

    assert answer == "最终答案"


async def test_plain_answer_tokens_are_emitted_before_the_model_run_finishes(monkeypatch):
    async def retrieval(**_kwargs):
        return []

    class SlowAnswerExecutor:
        finished = False

        async def astream_events(self, *_args, **_kwargs):
            yield {
                "event": "on_chat_model_stream",
                "run_id": "answer-run",
                "data": {"chunk": SimpleNamespace(content="回答开头", tool_call_chunks=[])},
            }
            await asyncio.sleep(0.01)
            self.finished = True
            yield {
                "event": "on_chat_model_end",
                "run_id": "answer-run",
                "data": {"output": SimpleNamespace(tool_calls=[])},
            }

    executor = SlowAnswerExecutor()
    monkeypatch.setattr(search_agent, "get_settings", lambda: _settings_for_stream())
    monkeypatch.setattr(search_agent, "retrieve_meme_chunks", retrieval)
    monkeypatch.setattr(search_agent, "_build_llm", lambda *_args: object())
    monkeypatch.setattr(
        search_agent,
        "build_meme_search_agent",
        lambda *_args, **_kwargs: executor,
    )

    first_token = None
    async for event in search_agent.stream_ai_search(_request()):
        name, payload = _decode_sse([event])[0]
        if name == "token":
            first_token = payload["text"]
            break

    assert first_token == "回答开头"
    assert executor.finished is False


async def test_stream_preserves_partial_answer_when_model_completion_event_is_missing(monkeypatch):
    chunks = [RetrievedChunk(meme_id="m1", content="标题: 降级候选梗", score=0.8, title="降级候选梗")]

    async def retrieval(**_kwargs):
        return chunks

    class MissingEndEventExecutor:
        async def astream_events(self, *_args, **_kwargs):
            yield {
                "event": "on_chat_model_stream",
                "run_id": "unconfirmed-run",
                "data": {
                    "chunk": SimpleNamespace(content="不可泄漏的未确认文本", tool_call_chunks=[])
                },
            }

    monkeypatch.setattr(search_agent, "get_settings", lambda: _settings_for_stream())
    monkeypatch.setattr(search_agent, "retrieve_meme_chunks", retrieval)
    monkeypatch.setattr(search_agent, "_build_llm", lambda *_args: object())
    monkeypatch.setattr(
        search_agent,
        "build_meme_search_agent",
        lambda *_args, **_kwargs: MissingEndEventExecutor(),
    )

    events = _decode_sse([event async for event in search_agent.stream_ai_search(_request())])
    answer = "".join(payload["text"] for name, payload in events if name == "token")

    assert answer == "不可泄漏的未确认文本"
    assert events[-1][0] == "error"


async def test_agent_stream_logs_rag_stages_without_logging_user_content(monkeypatch, caplog):
    query = "日志不得包含的完整用户问题"

    async def retrieval(**_kwargs):
        return [RetrievedChunk(meme_id="m1", content="标题: 只因", score=0.9, title="只因")]

    monkeypatch.setattr(search_agent, "get_settings", lambda: _settings_for_stream())
    monkeypatch.setattr(search_agent, "_build_llm", lambda *_args: None)
    monkeypatch.setattr(search_agent, "retrieve_meme_chunks", retrieval)
    caplog.set_level("INFO")

    events = _decode_sse(
        [event async for event in search_agent.stream_ai_search(_request(query=query))]
    )
    messages = "\n".join(record.getMessage() for record in caplog.records)

    assert events[-1][0] == "done"
    assert "rag_request_started request_id=request-test" in messages
    assert "rag_stage_started request_id=request-test stage=meme_retrieval" in messages
    assert "rag_stage_completed request_id=request-test stage=meme_retrieval" in messages
    assert "hits=1" in messages
    assert "candidates=m1:0.9000" in messages
    assert "rag_generation_skipped request_id=request-test reason=model_not_configured" in messages
    assert "rag_request_finished request_id=request-test outcome=completed" in messages
    assert query not in messages


async def test_agent_stream_logs_tool_boundaries_without_tool_payload(monkeypatch, caplog):
    async def retrieval(**_kwargs):
        return []

    class ToolThenAnswerExecutor:
        async def astream_events(self, *_args, **_kwargs):
            yield {
                "event": "on_tool_start",
                "run_id": "tool-run",
                "name": "search_meme_knowledge",
                "data": {"input": {"query": "工具中的私密查询"}},
            }
            yield {
                "event": "on_tool_end",
                "run_id": "tool-run",
                "name": "search_meme_knowledge",
                "data": {"output": "工具返回的私密正文"},
            }
            yield {
                "event": "on_chat_model_start",
                "run_id": "answer-run",
                "data": {},
            }
            yield {
                "event": "on_chat_model_stream",
                "run_id": "answer-run",
                "data": {"chunk": SimpleNamespace(content="最终答案", tool_call_chunks=[])},
            }
            yield {
                "event": "on_chat_model_end",
                "run_id": "answer-run",
                "data": {"output": SimpleNamespace(tool_calls=[])},
            }

    monkeypatch.setattr(search_agent, "get_settings", lambda: _settings_for_stream())
    monkeypatch.setattr(search_agent, "retrieve_meme_chunks", retrieval)
    monkeypatch.setattr(search_agent, "_build_llm", lambda *_args: object())
    monkeypatch.setattr(
        search_agent,
        "build_meme_search_agent",
        lambda *_args, **_kwargs: ToolThenAnswerExecutor(),
    )
    caplog.set_level("INFO")

    events = _decode_sse([event async for event in search_agent.stream_ai_search(_request())])
    messages = "\n".join(record.getMessage() for record in caplog.records)

    assert events[-1][0] == "done"
    assert "rag_tool_started request_id=request-test tool=search_meme_knowledge" in messages
    assert "rag_tool_completed request_id=request-test tool=search_meme_knowledge" in messages
    assert "rag_model_call_started request_id=request-test" in messages
    assert "rag_model_call_completed request_id=request-test" in messages
    assert "rag_stage_completed request_id=request-test stage=agent_generation" in messages
    assert "工具中的私密查询" not in messages
    assert "工具返回的私密正文" not in messages
    assert "最终答案" not in messages


async def test_python_total_timeout_does_not_fall_back_to_java_candidates(monkeypatch):
    async def retrieval_hangs(**_kwargs):
        await asyncio.sleep(0.05)

    monkeypatch.setattr(
        search_agent,
        "get_settings",
        lambda: _settings_for_stream(AGENT_TOTAL_TIMEOUT_SECONDS=0.001),
    )
    monkeypatch.setattr(search_agent, "retrieve_meme_chunks", retrieval_hangs)
    request = _request(
        extra={"snippets": [{"meme_id": "meme_12", "title": "降级候选梗", "introduction": "简介"}]}
    )

    events = _decode_sse([event async for event in search_agent.stream_ai_search(request)])
    answer = "".join(payload["text"] for name, payload in events if name == "token")

    assert answer == (
        "我这边暂时还没找到和你这个问题特别相关的内容，你可以换个说法，"
        "或者再补充一点信息，我再帮你找找。"
    )
    assert all(name != "cite" for name, _ in events)
    assert events[-1][0] == "done"
    assert events[-1][1]["finish_reason"] == "fallback_total_timeout"


async def test_python_total_timeout_after_visible_text_emits_error_without_done(monkeypatch):
    async def retrieval(**_kwargs):
        return []

    class SlowFinalAnswerExecutor:
        async def astream_events(self, *_args, **_kwargs):
            yield {
                "event": "on_chat_model_stream",
                "run_id": "answer-run",
                "data": {"chunk": SimpleNamespace(content="部分回答", tool_call_chunks=[])},
            }
            yield {
                "event": "on_chat_model_end",
                "run_id": "answer-run",
                "data": {"output": SimpleNamespace(tool_calls=[])},
            }
            await asyncio.sleep(0.05)

    monkeypatch.setattr(
        search_agent,
        "get_settings",
        lambda: _settings_for_stream(AGENT_TOTAL_TIMEOUT_SECONDS=0.01),
    )
    monkeypatch.setattr(search_agent, "retrieve_meme_chunks", retrieval)
    monkeypatch.setattr(search_agent, "_build_llm", lambda *_args: object())
    monkeypatch.setattr(
        search_agent,
        "build_meme_search_agent",
        lambda *_args, **_kwargs: SlowFinalAnswerExecutor(),
    )

    events = _decode_sse([event async for event in search_agent.stream_ai_search(_request())])

    assert any(name == "token" for name, _payload in events)
    assert events[-1][0] == "error"
    assert not any(name == "done" for name, _payload in events)
