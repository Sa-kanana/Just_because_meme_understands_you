import pytest

from app.retrieval.db import _ensure_embedding_dimension


class FakeConnection:
    def __init__(self, current_type, has_rows=False):
        self.current_type = current_type
        self.has_rows = has_rows
        self.executed = []

    async def fetchval(self, query, *_args):
        if "format_type" in query:
            return self.current_type
        if "SELECT EXISTS" in query:
            return self.has_rows
        raise AssertionError(f"Unexpected query: {query}")

    async def execute(self, query, *_args):
        self.executed.append(query)

    def transaction(self):
        return FakeTransaction()


class FakeTransaction:
    async def __aenter__(self):
        return self

    async def __aexit__(self, *_args):
        return False


@pytest.mark.asyncio
async def test_embedding_dimension_mismatch_updates_empty_table():
    conn = FakeConnection("vector(1536)")

    await _ensure_embedding_dimension(conn, "meme_vector_chunk", 1024)

    assert conn.executed == [
        "LOCK TABLE meme_vector_chunk IN ACCESS EXCLUSIVE MODE",
        "ALTER TABLE meme_vector_chunk ALTER COLUMN embedding TYPE vector(1024)"
    ]


@pytest.mark.asyncio
async def test_embedding_dimension_mismatch_refuses_to_change_populated_table():
    conn = FakeConnection("vector(1536)", has_rows=True)

    with pytest.raises(RuntimeError, match="re-embed"):
        await _ensure_embedding_dimension(conn, "meme_vector_chunk", 1024)

    assert conn.executed == ["LOCK TABLE meme_vector_chunk IN ACCESS EXCLUSIVE MODE"]


@pytest.mark.asyncio
async def test_embedding_dimension_match_leaves_table_unchanged():
    conn = FakeConnection("vector(1024)")

    await _ensure_embedding_dimension(conn, "knowledge_vector_chunk", 1024)

    assert conn.executed == []
