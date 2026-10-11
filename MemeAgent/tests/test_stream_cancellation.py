import asyncio
import importlib.util
import sys
from pathlib import Path
from types import ModuleType

import pytest

from app.schemas.stream import StreamRequest


@pytest.mark.asyncio
async def test_streaming_response_cancellation_closes_agent_generator(monkeypatch):
    upstream_started = asyncio.Event()
    upstream_cancelled = asyncio.Event()

    async def fake_stream(_request):
        try:
            yield "event: token\ndata: {\"text\":\"部分回答\"}\n\n"
            upstream_started.set()
            await asyncio.Event().wait()
        finally:
            upstream_cancelled.set()

    route_module_name = "stream_route_under_test"
    fake_search_agent = ModuleType("app.agents.search_agent")
    fake_search_agent.stream_ai_search = fake_stream
    with monkeypatch.context() as import_patch:
        import_patch.setitem(sys.modules, "app.agents.search_agent", fake_search_agent)
        route_spec = importlib.util.spec_from_file_location(
            route_module_name,
            Path(__file__).parents[1] / "app" / "api" / "stream.py",
        )
        assert route_spec is not None and route_spec.loader is not None
        stream_route = importlib.util.module_from_spec(route_spec)
        route_spec.loader.exec_module(stream_route)

    monkeypatch.setattr(stream_route, "stream_ai_search", fake_stream)
    response = await stream_route.stream_search(
        StreamRequest(request_id="request-1", session_id="99", query="梗搜索"),
        _auth=None,
    )
    body = response.body_iterator.__aiter__()

    assert "部分回答" in await anext(body)
    pending_chunk = asyncio.create_task(anext(body))
    await asyncio.wait_for(upstream_started.wait(), timeout=1)
    pending_chunk.cancel()

    with pytest.raises(asyncio.CancelledError):
        await pending_chunk
    assert upstream_cancelled.is_set()
