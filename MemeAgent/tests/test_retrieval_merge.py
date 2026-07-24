from app.retrieval.repository import RetrievedChunk, merge_retrieved


def test_merge_retrieved_prefers_higher_score():
    vector = [RetrievedChunk(meme_id="1", content="v", score=0.9, title="A")]
    hint = [RetrievedChunk(meme_id="1", content="h", score=0.55, title="A")]
    mysql = [RetrievedChunk(meme_id="2", content="m", score=0.45, title="B")]
    merged = merge_retrieved(vector, hint, mysql, top_k=5)
    assert [c.meme_id for c in merged] == ["1", "2"]
    assert merged[0].score == 0.9
    assert merged[0].content == "v"


def test_merge_retrieved_respects_top_k():
    chunks = [
        RetrievedChunk(meme_id=str(i), content=str(i), score=1.0 - i * 0.1)
        for i in range(5)
    ]
    merged = merge_retrieved(chunks, [], [], top_k=2)
    assert len(merged) == 2
    assert merged[0].meme_id == "0"
