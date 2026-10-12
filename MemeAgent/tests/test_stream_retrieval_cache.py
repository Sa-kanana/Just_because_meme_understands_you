import json
from types import SimpleNamespace

from app.agents import search_agent, tools
from app.core.settings import Settings
from app.retrieval.repository import RetrievedChunk
from app.schemas.stream import StreamRequest


def _decode_sse(frames: list[str]) -> list[tuple[str, dict]]:
    records = []
    for frame in frames:
        lines = frame.splitlines()
        event_name = next(
            line.removeprefix("event: ")
            for line in lines
            if line.startswith("event: ")
        )
        payload = next(
            line.removeprefix("data: ")
            for line in lines
            if line.startswith("data: ")
        )
        records.append((event_name, json.loads(payload)))
    return records


async def test_stream_reuses_initial_retrieval_in_agent_tool(monkeypatch):
    retrieved = [
        RetrievedChunk(
            meme_id="meme-1",
            content="只因你太美的梗知识",
            score=0.91,
            title="只因你太美",
        )
    ]
    retrieval_calls = []

    async def retrieve_meme(**kwargs):
        retrieval_calls.append(kwargs)
        return retrieved

    async def retrieve_admin(**_kwargs):
        return []

    class ToolThenAnswerExecutor:
        def __init__(self, provided_tools):
            self.meme_tool = provided_tools[0]

        async def astream_events(self, _inputs, **_kwargs):
            tool_result = await self.meme_tool.ainvoke(
                {"query": "只因是什么梗"}
            )
            assert "只因你太美" in tool_result
            yield {
                "event": "on_tool_end",
                "name": "search_meme_knowledge",
                "data": {"output": tool_result},
            }
            yield {
                "event": "on_chat_model_stream",
                "data": {
                    "chunk": SimpleNamespace(
                        content="这是一个网络梗。",
                        tool_call_chunks=[],
                    )
                },
            }
            yield {
                "event": "on_chat_model_end",
                "data": {"output": SimpleNamespace(tool_calls=[])},
            }

    settings = Settings(_env_file=None, INTERNAL_API_KEY="test")
    monkeypatch.setattr(search_agent, "get_settings", lambda: settings)
    monkeypatch.setattr(search_agent, "apply_langsmith_env", lambda _settings: None)
    monkeypatch.setattr(search_agent, "retrieve_meme_chunks", retrieve_meme)
    monkeypatch.setattr(search_agent, "retrieve_admin_knowledge_chunks", retrieve_admin)
    monkeypatch.setattr(tools, "retrieve_meme_chunks", retrieve_meme)
    monkeypatch.setattr(search_agent, "_build_llm", lambda *_args: object())
    monkeypatch.setattr(
        search_agent,
        "build_meme_search_agent",
        lambda _llm, provided_tools, **_kwargs: ToolThenAnswerExecutor(provided_tools),
    )
    request = StreamRequest(
        request_id="request-1",
        session_id="session-1",
        query="只因是什么梗",
        context={"locale": "zh-CN"},
    )

    frames = [frame async for frame in search_agent.stream_ai_search(request)]
    events = _decode_sse(frames)

    assert len(retrieval_calls) == 1
    assert retrieval_calls[0]["query"] == "只因是什么梗"
    assert [name for name, _payload in events].count("cite") == 1
    assert events[-1][0] == "done"
