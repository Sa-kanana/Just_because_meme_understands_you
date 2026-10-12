from datetime import datetime, timezone

import pytest

from app.ingest import worker as ingest_worker
from app.retrieval import repository
from app.schemas.ingest import IngestDocument


class _Transaction:
    def __init__(self, connection):
        self.connection = connection

    async def __aenter__(self):
        self.connection.transaction_entered = True
        return self

    async def __aexit__(self, *_args):
        return False


class _Acquire:
    def __init__(self, connection):
        self.connection = connection

    async def __aenter__(self):
        return self.connection

    async def __aexit__(self, *_args):
        return False


class _Pool:
    def __init__(self, connection):
        self.connection = connection

    def acquire(self, **_kwargs):
        return _Acquire(self.connection)


class _Connection:
    def __init__(self, matching_chunk_count):
        self.matching_chunk_count = matching_chunk_count
        self.executed = []
        self.inserted_rows = []
        self.transaction_entered = False

    def transaction(self):
        return _Transaction(self)

    async def fetchrow(self, query, *_args):
        if "SELECT 1 FROM meme_vector_chunk" in query:
            return {"exists": 1} if self.matching_chunk_count else None
        return None

    async def fetchval(self, query, *_args):
        if "COUNT(*)" in query:
            return self.matching_chunk_count
        return None

    async def execute(self, query, *_args):
        self.executed.append(query)
        return "DELETE 9"

    async def executemany(self, query, rows):
        self.executed.append(query)
        self.inserted_rows = list(rows)


class _PageConnection:
    def __init__(self, pages):
        self.pages = pages
        self.calls = []
        self.queries = []

    async def fetch(self, query, *args):
        self.queries.append(query)
        if "WHERE meme_id > $1" in query:
            cursor, limit = args
        else:
            cursor = None
            (limit,) = args
        self.calls.append((cursor, limit))
        return [{"meme_id": meme_id} for meme_id in self.pages.get(cursor, [])]


def _document():
    return IngestDocument(
        meme_id="123",
        title="测试梗",
        introduction="梗内容",
        tags=["网络用语"],
        updated_at=datetime(2026, 10, 12, tzinfo=timezone.utc),
        content_hash="same-hash-123",
    )


@pytest.mark.asyncio
async def test_upsert_skips_same_hash_document_within_chunk_limit(monkeypatch):
    connection = _Connection(matching_chunk_count=3)
    monkeypatch.setattr(repository, "get_pool", lambda: _Pool(connection))

    async def fail_if_called(_chunks):
        raise AssertionError("unchanged documents within the chunk limit must not embed")

    monkeypatch.setattr(repository, "embed_texts", fail_if_called)

    await repository.upsert_document(_document(), ["新分块"])

    assert connection.transaction_entered is False
    assert connection.executed == []


@pytest.mark.asyncio
async def test_upsert_rebuilds_same_hash_legacy_document_over_chunk_limit(monkeypatch):
    connection = _Connection(matching_chunk_count=7)
    embed_calls = []
    monkeypatch.setattr(repository, "get_pool", lambda: _Pool(connection))

    async def embed_chunks(chunks):
        embed_calls.append(list(chunks))
        return [[0.1, 0.2] for _ in chunks]

    monkeypatch.setattr(repository, "embed_texts", embed_chunks)

    await repository.upsert_document(_document(), ["新分块一", "新分块二"])

    assert embed_calls == [["新分块一", "新分块二"]]
    assert connection.transaction_entered is True
    assert any("DELETE FROM meme_vector_chunk" in query for query in connection.executed)
    assert len(connection.inserted_rows) == 2


@pytest.mark.asyncio
async def test_chunk_overflow_fails_before_embedding_or_write(monkeypatch):
    doc = _document().model_copy(update={"introduction": "正文" * 500})
    original_build_chunks = ingest_worker.build_chunks
    calls = {"embedding": 0, "pool": 0}

    def force_small_chunks(document):
        return original_build_chunks(document, max_chunk_chars=80)

    async def fail_if_embedded(_chunks):
        calls["embedding"] += 1
        raise AssertionError("overflow must be rejected before embedding")

    def fail_if_db_accessed():
        calls["pool"] += 1
        raise AssertionError("overflow must be rejected before database access")

    monkeypatch.setattr(ingest_worker, "build_chunks", force_small_chunks)
    monkeypatch.setattr(repository, "embed_texts", fail_if_embedded)
    monkeypatch.setattr(repository, "get_pool", fail_if_db_accessed)

    with pytest.raises(ValueError, match="超过上限"):
        await ingest_worker._safe_upsert(doc)

    assert calls == {"embedding": 0, "pool": 0}


@pytest.mark.asyncio
async def test_list_meme_ids_page_uses_distinct_keyset_cursor(monkeypatch):
    connection = _PageConnection(
        {
            None: ["101", "102", "103"],
            "102": ["103"],
            "103": [],
        }
    )
    monkeypatch.setattr(repository, "get_pool", lambda: _Pool(connection))

    page_function = getattr(repository, "list_meme_ids_page", None)
    assert page_function is not None

    first_page = await page_function(None, 2)
    second_page = await page_function(first_page[1], 2)
    empty_page = await page_function("103", 2)

    assert first_page == (["101", "102"], "102")
    assert second_page == (["103"], None)
    assert empty_page == ([], None)
    assert connection.calls == [(None, 3), ("102", 3), ("103", 3)]
    assert "SELECT DISTINCT meme_id" in connection.queries[0]
    assert "WHERE" not in connection.queries[0]
    assert "meme_id > $1" in connection.queries[1]
    assert "ORDER BY meme_id" in connection.queries[1]
    assert "LIMIT $2" in connection.queries[1]
