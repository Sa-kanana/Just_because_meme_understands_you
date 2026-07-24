from app.agents.tools import chunks_to_tool_payload, parse_tool_payload
from app.retrieval.repository import RetrievedChunk


def test_tool_payload_roundtrip():
    chunks = [
        RetrievedChunk(meme_id="12", content="标题: 只因\n内容", score=0.88, title="只因"),
    ]
    raw = chunks_to_tool_payload(chunks)
    parsed = parse_tool_payload(raw)
    assert len(parsed) == 1
    assert parsed[0].meme_id == "12"
    assert parsed[0].title == "只因"
    assert parsed[0].score == 0.88


def test_build_meme_search_agent_imports():
    from app.agents.meme_agent import build_meme_search_agent
    from app.agents.tools import build_search_meme_tool

    assert callable(build_meme_search_agent)
    assert callable(build_search_meme_tool)
