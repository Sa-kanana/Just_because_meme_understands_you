import asyncio
from collections import OrderedDict

import pytest
from pydantic import ValidationError

from app.agents import tools
from app.core.settings import Settings
from app.retrieval import db, embeddings
from app.retrieval.repository import RetrievedChunk


@pytest.mark.asyncio
async def test_repeated_agent_retrieval_reuses_request_result(monkeypatch):
    calls = []
    expected = [RetrievedChunk(meme_id="meme-1", content="梗内容", score=0.9, title="梗")]

    async def retrieve(**kwargs):
        calls.append(kwargs)
        await asyncio.sleep(0.01)
        return expected

    monkeypatch.setattr(tools, "retrieve_meme_chunks", retrieve)
    search_tool = tools.build_search_meme_tool(
        initial_query="首轮问题",
        initial_chunks=[],
    )

    first, second = await asyncio.gather(
        search_tool.ainvoke({"query": "改写后的问题"}),
        search_tool.ainvoke({"query": "改写后的问题"}),
    )
    cached = await search_tool.ainvoke({"query": "改写后的问题"})

    assert first == second == cached
    assert len(calls) == 1


@pytest.mark.asyncio
async def test_query_embedding_cache_reuses_success_and_coalesces_concurrent_calls(monkeypatch):
    monkeypatch.setattr(embeddings, "_embedding_cache", OrderedDict(), raising=False)
    monkeypatch.setattr(embeddings, "_embedding_inflight", {}, raising=False)
    calls = []

    class FakeEmbeddings:
        async def aembed_documents(self, texts):
            calls.append(texts)
            await asyncio.sleep(0.01)
            return [[0.1, 0.2]]

    settings = Settings(
        _env_file=None,
        INTERNAL_API_KEY="test",
        OPENAI_API_KEY="test-key",
        VECTOR_EMBEDDING_DIM=2,
        EMBEDDING_CACHE_MAX_ENTRIES=8,
        EMBEDDING_CACHE_TTL_SECONDS=60,
    )
    monkeypatch.setattr(embeddings, "_get_embeddings", lambda _settings: FakeEmbeddings())

    results = await asyncio.gather(
        embeddings.embed_text("  同一个查询  ", settings),
        embeddings.embed_text("同一个查询", settings),
    )
    cached = await embeddings.embed_text("同一个查询", settings)

    assert results == [[0.1, 0.2], [0.1, 0.2]]
    assert cached == [0.1, 0.2]
    assert calls == [["同一个查询"]]


@pytest.mark.asyncio
async def test_vector_schema_creates_hnsw_indexes_for_both_search_tables(monkeypatch):
    class FakeConnection:
        def __init__(self):
            self.statements = []

        async def execute(self, statement, *_args):
            self.statements.append(statement)

        async def fetch(self, statement, *_args):
            if "information_schema.columns" in statement:
                return [{"column_name": "content_sha256"}]
            raise AssertionError(f"Unexpected query: {statement}")

        async def fetchval(self, statement, *_args):
            if "format_type" in statement:
                return "vector(2)"
            if "pg_advisory_lock" in statement or "pg_advisory_unlock" in statement:
                return True
            raise AssertionError(f"Unexpected query: {statement}")

    class AcquireContext:
        def __init__(self, connection):
            self.connection = connection

        async def __aenter__(self):
            return self.connection

        async def __aexit__(self, *_args):
            return False

    class FakePool:
        def __init__(self, connection):
            self.connection = connection

        def acquire(self):
            return AcquireContext(self.connection)

    connection = FakeConnection()
    monkeypatch.setattr(db, "get_pool", lambda: FakePool(connection))

    await db.ensure_schema(
        Settings(_env_file=None, INTERNAL_API_KEY="test", VECTOR_EMBEDDING_DIM=2)
    )

    statements = "\n".join(connection.statements).lower()
    assert "idx_meme_vector_chunk_embedding_hnsw" in statements
    assert "idx_knowledge_vector_chunk_embedding_hnsw" in statements
    assert "pg_trgm" in statements
    assert "idx_knowledge_vector_chunk_content_trgm" in statements
    assert "gin (lower(content) gin_trgm_ops)" in statements
    assert "create index concurrently if not exists" in statements
    assert "using hnsw (embedding vector_cosine_ops)" in statements
    assert "comment on column meme_vector_chunk.embedding" in statements
    assert "comment on column knowledge_vector_chunk.embedding" in statements
    checkpoint_index = next(
        statement
        for statement in connection.statements
        if "idx_knowledge_embedding_checkpoint_created_at" in statement
    )
    assert "create index concurrently if not exists" in checkpoint_index.lower()


@pytest.mark.asyncio
async def test_vector_pool_uses_configured_per_worker_limits(monkeypatch):
    captured = {}
    monkeypatch.setattr(db, "_pool", None)

    async def fake_create_pool(**kwargs):
        captured.update(kwargs)
        return object()

    monkeypatch.setattr(db.asyncpg, "create_pool", fake_create_pool)
    settings = Settings(
        _env_file=None,
        INTERNAL_API_KEY="test",
        VECTOR_DATABASE_URL="postgresql://user:password@localhost/db",
        VECTOR_POOL_MIN_SIZE=2,
        VECTOR_POOL_MAX_SIZE=12,
        VECTOR_DB_COMMAND_TIMEOUT_SECONDS=45,
    )

    await db.create_pool(settings)

    assert captured["min_size"] == 2
    assert captured["max_size"] == 12
    assert captured["command_timeout"] == 45


@pytest.mark.asyncio
async def test_vector_pool_limits_stale_transactions_and_tags_database_sessions(monkeypatch):
    captured = {}
    monkeypatch.setattr(db, "_pool", None)

    async def fake_create_pool(**kwargs):
        captured.update(kwargs)
        return object()

    monkeypatch.setattr(db.asyncpg, "create_pool", fake_create_pool)
    settings = Settings(
        _env_file=None,
        INTERNAL_API_KEY="test",
        VECTOR_DATABASE_URL="postgresql://user:password@localhost/db",
    )

    await db.create_pool(settings)

    assert captured["server_settings"] == {
        "application_name": "memeagent",
        "transaction_timeout": "120s",
        "idle_in_transaction_session_timeout": "60s",
        "lock_timeout": "10s",
    }


def test_hnsw_vector_dimension_rejects_unsupported_dimensions():
    with pytest.raises(ValidationError, match="2000"):
        Settings(_env_file=None, INTERNAL_API_KEY="test", VECTOR_EMBEDDING_DIM=3072)
