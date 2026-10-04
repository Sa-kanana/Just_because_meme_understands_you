import app.agents.tools as tools
from app.retrieval.repository import RetrievedChunk


def _chunk(meme_id: str) -> RetrievedChunk:
    return RetrievedChunk(
        meme_id=meme_id,
        content=f"梗条目 {meme_id}",
        score=0.8,
        title=f"标题 {meme_id}",
    )


def _payload_ids(raw: str) -> list[str]:
    return [chunk.meme_id for chunk in tools.parse_tool_payload(raw)]


def test_retrieval_cache_key_includes_exact_query_hints_and_top_k():
    key = tools.retrieval_cache_key("只因是什么梗", ["12", "13"], 5)

    assert key == ("只因是什么梗", ("12", "13"), 5)


async def test_search_meme_tool_reuses_prefetched_result(monkeypatch):
    calls = []

    async def fake_retrieve(**kwargs):
        calls.append(kwargs)
        return [_chunk("fresh")]

    monkeypatch.setattr(tools, "retrieve_meme_chunks", fake_retrieve)
    key = ("只因是什么梗", ("12",), 5)
    tool = tools.build_search_meme_tool(
        default_hint_ids=["12"],
        default_top_k=5,
        retrieval_cache={key: [_chunk("12")]},
    )

    raw = await tool.ainvoke({"query": "只因是什么梗", "hint_meme_ids": ["12"], "top_k": 5})

    assert _payload_ids(raw) == ["12"]
    assert calls == []


async def test_search_meme_tool_retrieves_when_query_changes(monkeypatch):
    calls = []

    async def fake_retrieve(**kwargs):
        calls.append(kwargs)
        return [_chunk("fresh")]

    monkeypatch.setattr(tools, "retrieve_meme_chunks", fake_retrieve)
    key = ("只因是什么梗", ("12",), 5)
    tool = tools.build_search_meme_tool(
        default_hint_ids=["12"],
        retrieval_cache={key: [_chunk("12")]},
    )

    raw = await tool.ainvoke({"query": "只因来源"})

    assert calls[0]["query"] == "只因来源"
    assert _payload_ids(raw) == ["fresh"]


async def test_search_meme_tool_retrieves_when_hint_ids_or_top_k_change(
    monkeypatch,
):
    calls = []

    async def fake_retrieve(**kwargs):
        calls.append(kwargs)
        return [_chunk("fresh")]

    monkeypatch.setattr(tools, "retrieve_meme_chunks", fake_retrieve)
    key = ("只因是什么梗", ("12",), 5)
    tool = tools.build_search_meme_tool(
        default_hint_ids=["12"],
        retrieval_cache={key: [_chunk("12")]},
    )

    for changed in (
        {"hint_meme_ids": ["13"]},
        {"hint_meme_ids": ["12"], "top_k": 4},
    ):
        calls.clear()
        args = {"query": "只因是什么梗", **changed}
        raw = await tool.ainvoke(args)

        assert len(calls) == 1
        assert _payload_ids(raw) == ["fresh"]


async def test_search_meme_tools_do_not_share_request_cache(monkeypatch):
    calls = []

    async def fake_retrieve(**kwargs):
        calls.append(kwargs)
        return [_chunk("fresh")]

    monkeypatch.setattr(tools, "retrieve_meme_chunks", fake_retrieve)
    key = ("只因是什么梗", ("12",), 5)
    first_tool = tools.build_search_meme_tool(
        default_hint_ids=["12"],
        retrieval_cache={key: [_chunk("first-request")]},
    )
    second_tool = tools.build_search_meme_tool(
        default_hint_ids=["12"],
        retrieval_cache={key: [_chunk("second-request")]},
    )

    first = await first_tool.ainvoke({"query": "只因是什么梗", "hint_meme_ids": ["12"]})
    second = await second_tool.ainvoke({"query": "只因是什么梗", "hint_meme_ids": ["12"]})

    assert _payload_ids(first) == ["first-request"]
    assert _payload_ids(second) == ["second-request"]
    assert calls == []
