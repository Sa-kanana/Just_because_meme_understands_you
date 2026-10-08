from app.retrieval.repository import RetrievedChunk, merge_retrieved
from app.retrieval import repository


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


def test_merge_retrieved_uses_rank_fusion_across_sources():
    vector = [
        RetrievedChunk(meme_id="a", content="vector-a", score=0.9),
        RetrievedChunk(meme_id="b", content="vector-b", score=0.8),
    ]
    hints = [
        RetrievedChunk(meme_id="b", content="hint-b", score=0.55),
        RetrievedChunk(meme_id="c", content="hint-c", score=0.55),
    ]
    mysql = [
        RetrievedChunk(meme_id="b", content="mysql-b", score=0.45),
        RetrievedChunk(meme_id="a", content="mysql-a", score=0.45),
    ]

    merged = merge_retrieved(vector, hints, mysql, top_k=5)

    assert [chunk.meme_id for chunk in merged] == ["b", "a", "c"]
    assert merged[0].content == "vector-b"
    assert merged[0].score == 0.8


def test_merge_retrieved_does_not_count_duplicate_meme_chunks_twice_per_source():
    vector = [
        RetrievedChunk(meme_id="a", content="a-1", score=0.9),
        RetrievedChunk(meme_id="a", content="a-2", score=0.8),
        RetrievedChunk(meme_id="a", content="a-3", score=0.7),
        RetrievedChunk(meme_id="b", content="b", score=0.6),
    ]
    hints = [RetrievedChunk(meme_id="b", content="hint-b", score=0.55)]

    merged = merge_retrieved(vector, hints, [], top_k=5)

    assert [chunk.meme_id for chunk in merged] == ["b", "a"]


def test_merge_retrieved_does_not_double_count_java_hint_and_snippet_candidates():
    vector = [RetrievedChunk(meme_id="a", content="vector-a", score=0.9)]
    hints = [
        RetrievedChunk(meme_id="b", content="hint-b", score=0.55),
        RetrievedChunk(meme_id="c", content="hint-c", score=0.55),
    ]
    mysql = [
        RetrievedChunk(meme_id="b", content="mysql-b", score=0.45),
        RetrievedChunk(meme_id="a", content="mysql-a", score=0.45),
    ]

    merged = merge_retrieved(vector, hints, mysql, top_k=5)

    assert [chunk.meme_id for chunk in merged] == ["a", "b", "c"]


async def test_similarity_search_oversamples_chunks_to_keep_distinct_meme_candidates(monkeypatch):
    class Connection:
        def __init__(self):
            self.limit = None

        async def fetch(self, _query, _vector, limit):
            self.limit = limit
            return []

    class Pool:
        def __init__(self, conn):
            self.conn = conn

        def acquire(self, **_kwargs):
            return self

        async def __aenter__(self):
            return self.conn

        async def __aexit__(self, *_args):
            return False

    conn = Connection()
    monkeypatch.setattr(repository, "get_pool", lambda: Pool(conn))

    async def fake_embed(_query):
        return [0.0, 0.0]

    monkeypatch.setattr(repository, "embed_text", fake_embed)
    await repository.similarity_search("query", top_k=5)

    assert conn.limit == 30
