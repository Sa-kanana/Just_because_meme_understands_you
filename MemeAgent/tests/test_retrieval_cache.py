from app.agents import tools
from app.retrieval.repository import RetrievedChunk


def _chunk(meme_id: str) -> RetrievedChunk:
    return RetrievedChunk(
        meme_id=meme_id,
        content=f"梗条目 {meme_id}",
        score=0.8,
        title=f"标题 {meme_id}",
    )


async def test_search_meme_tool_reuses_prefetched_result_for_query_alias(monkeypatch):
    async def unexpected_retrieval(**_kwargs):
        raise AssertionError("prefetched results should satisfy this tool call")

    monkeypatch.setattr(tools, "retrieve_meme_chunks", unexpected_retrieval)
    tool = tools.build_search_meme_tool(
        initial_query="只因你太美是什么梗",
        initial_query_aliases=["只因是什么梗"],
        initial_chunks=[_chunk("meme-1")],
    )

    result = await tool.ainvoke({"query": "只因是什么梗"})

    assert "标题：标题 meme-1" in result
    assert "正文：" in result


async def test_search_meme_tools_keep_prefetch_cache_request_scoped(monkeypatch):
    async def unexpected_retrieval(**_kwargs):
        raise AssertionError("matching request-local prefetch should be reused")

    monkeypatch.setattr(tools, "retrieve_meme_chunks", unexpected_retrieval)
    first_tool = tools.build_search_meme_tool(
        initial_query="同一个问题",
        initial_chunks=[_chunk("first-request")],
    )
    second_tool = tools.build_search_meme_tool(
        initial_query="同一个问题",
        initial_chunks=[_chunk("second-request")],
    )

    first_result = await first_tool.ainvoke({"query": "同一个问题"})
    second_result = await second_tool.ainvoke({"query": "同一个问题"})

    assert "标题：标题 first-request" in first_result
    assert "标题：标题 second-request" in second_result
