from types import SimpleNamespace

from app.agents import tools
from app.retrieval import repository
from app.retrieval.repository import RetrievedChunk


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


async def test_retrieve_meme_chunks_filters_below_calibrated_threshold_and_prefers_title(monkeypatch):
    vector_hits = [
        RetrievedChunk(
            meme_id="intro-only",
            content="标题: 普通梗\n标签: 日常\n\n不明觉厉只在介绍里出现",
            score=0.91,
            title="普通梗",
        ),
        RetrievedChunk(
            meme_id="exact-title",
            content="标题: 不明觉厉\n标签: 评价\n\n不明白但觉得厉害",
            score=0.76,
            title="不明觉厉",
        ),
        RetrievedChunk(
            meme_id="below-threshold",
            content="标题: 低分候选",
            score=0.69,
            title="低分候选",
        ),
    ]
    monkeypatch.setattr(
        tools,
        "get_settings",
        lambda: SimpleNamespace(
            retrieval_timeout_seconds=3,
            retrieval_max_retries=0,
            vector_min_similarity=0.70,
        ),
    )

    async def search_vectors(_query, _top_k):
        return vector_hits

    monkeypatch.setattr(tools, "similarity_search", search_vectors)

    actual = await tools.retrieve_meme_chunks(query="不明觉厉是什么意思", top_k=5)

    assert [chunk.meme_id for chunk in actual] == ["exact-title", "intro-only"]


def test_tag_hit_precedes_higher_scoring_introduction_only_match():
    actual = tools.rank_vector_candidates(
        "网络用语有什么梗",
        [
            RetrievedChunk(
                meme_id="intro-only",
                content="标题: 普通梗\n标签: 日常\n\n介绍里提到网络用语",
                score=0.93,
                title="普通梗",
            ),
            RetrievedChunk(
                meme_id="tag-hit",
                content="标题: 另一个梗\n标签: 网络用语 评价\n\n其它介绍",
                score=0.75,
                title="另一个梗",
            ),
        ],
        min_similarity=0.70,
        top_k=2,
    )

    assert [chunk.meme_id for chunk in actual] == ["tag-hit", "intro-only"]
    assert actual[0].score == 0.75
