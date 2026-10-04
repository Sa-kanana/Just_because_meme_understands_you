import json
from types import SimpleNamespace

from app.agents import search_agent, tools
from app.retrieval.repository import RetrievedChunk
from app.schemas.stream import StreamRequest


def _chunk() -> RetrievedChunk:
    return RetrievedChunk(
        meme_id="12",
        content="标题: 只因你太美\n简介: 一个流行梗",
        score=0.91,
        title="只因你太美",
    )


def _request() -> StreamRequest:
    return StreamRequest(
        request_id="request-1",
        session_id="session-1",
        query="只因是什么梗",
        context={"hint_meme_ids": ["12"]},
    )


def _event_records(frames: list[str]) -> list[tuple[str, dict]]:
    records = []
    for frame in frames:
        lines = frame.splitlines()
        name = next(line.removeprefix("event: ") for line in lines if line.startswith("event: "))
        data = next(line.removeprefix("data: ") for line in lines if line.startswith("data: "))
        records.append((name, json.loads(data)))
    return records


def _install_settings(monkeypatch, llm):
    settings = SimpleNamespace(retrieval_top_k=5, max_output_tokens=128)
    monkeypatch.setattr(search_agent, "get_settings", lambda: settings)
    monkeypatch.setattr(search_agent, "apply_langsmith_env", lambda _: None)
    monkeypatch.setattr(search_agent, "_build_llm", lambda *_: llm)


def _install_retriever(monkeypatch, *, error: Exception | None = None):
    calls = []

    async def fake_retrieve(**kwargs):
        calls.append(kwargs)
        if error is not None:
            raise error
        return [_chunk()]

    monkeypatch.setattr(search_agent, "retrieve_meme_chunks", fake_retrieve)
    monkeypatch.setattr(tools, "retrieve_meme_chunks", fake_retrieve)
    return calls


class _FakeExecutor:
    def __init__(self, meme_tool):
        self.meme_tool = meme_tool

    async def astream_events(self, _inputs, *, version, config):
        assert version == "v2"
        assert config["tags"] == ["meme-search-agent"]
        result = await self.meme_tool.ainvoke(
            {
                "query": "只因是什么梗",
                "hint_meme_ids": ["12"],
                "top_k": 5,
            }
        )
        yield {"event": "on_tool_end", "data": {"output": result}}
        yield {
            "event": "on_chat_model_stream",
            "data": {"chunk": SimpleNamespace(content="这个梗源自一段流行歌曲。")},
        }


async def test_stream_reuses_prefetch_without_changing_sse_order(monkeypatch):
    _install_settings(monkeypatch, llm=object())
    calls = _install_retriever(monkeypatch)

    def build_agent(_llm, agent_tools, **_kwargs):
        return _FakeExecutor(agent_tools[0])

    monkeypatch.setattr(search_agent, "build_meme_search_agent", build_agent)

    frames = [frame async for frame in search_agent.stream_ai_search(_request())]
    records = _event_records(frames)

    assert len(calls) == 1
    assert calls[0] == {
        "query": "只因是什么梗",
        "hint_meme_ids": ["12"],
        "mysql_snippets": [],
        "top_k": 5,
    }
    assert [name for name, _ in records] == ["meta", "cite", "token", "done"]
    assert records[0][1] == {"request_id": "request-1", "retrieved": ["12"]}
    assert records[1][1] == {
        "meme_id": "12",
        "score": 0.91,
        "title": "只因你太美",
    }


async def test_stream_fallback_preserves_sse_order(monkeypatch):
    _install_settings(monkeypatch, llm=None)
    calls = _install_retriever(monkeypatch)

    async def unexpected_agent(*_args, **_kwargs):
        raise AssertionError("fallback should not build an Agent")

    monkeypatch.setattr(search_agent, "build_meme_search_agent", unexpected_agent)

    frames = [frame async for frame in search_agent.stream_ai_search(_request())]
    names = [name for name, _ in _event_records(frames)]

    assert len(calls) == 1
    assert names[:2] == ["meta", "cite"]
    assert names[-1] == "done"
    assert names[2:-1]
    assert all(name == "token" for name in names[2:-1])


async def test_stream_prefetch_failure_preserves_error_event(monkeypatch):
    _install_settings(monkeypatch, llm=object())
    calls = _install_retriever(monkeypatch, error=RuntimeError("retrieval unavailable"))
    llm_builds = []
    tool_builds = []
    agent_builds = []

    monkeypatch.setattr(
        search_agent,
        "_build_llm",
        lambda *_args: llm_builds.append(True),
    )
    monkeypatch.setattr(
        search_agent,
        "build_search_meme_tool",
        lambda **_kwargs: tool_builds.append(True),
    )
    monkeypatch.setattr(
        search_agent,
        "build_meme_search_agent",
        lambda *_args, **_kwargs: agent_builds.append(True),
    )

    frames = [frame async for frame in search_agent.stream_ai_search(_request())]
    records = _event_records(frames)

    assert len(calls) == 1
    assert [name for name, _ in records] == ["error"]
    assert records[0][1] == {
        "code": "agent_error",
        "message": "retrieval unavailable",
    }
    assert llm_builds == []
    assert tool_builds == []
    assert agent_builds == []
