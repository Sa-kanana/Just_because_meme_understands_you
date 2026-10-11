from datetime import datetime, timezone
from types import SimpleNamespace

import pytest

from app.retrieval import knowledge_repository as repository
from app.schemas.knowledge import KnowledgeDocument


def test_knowledge_content_query_escapes_sql_like_wildcards():
    assert repository._escape_like_fragment("100%_abc\\") == "100\\%\\_abc\\\\"


class _Transaction:
    async def __aenter__(self):
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
    def __init__(self, states, delete_result="DELETE 0", rows=None, locked_state=None):
        self.states = list(states)
        self.delete_result = delete_result
        self.rows = rows or []
        self.locked_state = locked_state
        self.executed = []
        self.fetch_query = ""
        self.fetch_args = ()

    def transaction(self):
        return _Transaction()

    async def fetchrow(self, query, *_args):
        if "FOR UPDATE" in query:
            return self.locked_state if self.locked_state is not None else self.states[0]
        return self.states[0]

    async def fetchval(self, _query, *_args):
        return None

    async def fetch(self, query, *args):
        self.fetch_query = query
        self.fetch_args = args
        return self.rows

    async def execute(self, query, *_args):
        self.executed.append(query)
        if query.startswith("DELETE FROM knowledge_vector_chunk"):
            return self.delete_result
        return "INSERT 0 1"

    async def executemany(self, query, rows):
        self.executed.append((query, list(rows)))


def _document(revision=1, status="published"):
    return KnowledgeDocument(
        doc_id="123",
        title="FAQ",
        content="规则正文",
        status=status,
        updated_at=datetime.now(timezone.utc),
        content_hash="legacy-content-hash",
        revision=revision,
    )


@pytest.mark.asyncio
async def test_stale_upsert_after_delete_does_not_call_embedding(monkeypatch):
    state = {"revision": 4, "status": "DELETED", "content_sha256": "0" * 64}
    monkeypatch.setattr(repository, "get_pool", lambda: _Pool(_Connection([state])))

    async def fail_if_called(_chunks):
        raise AssertionError("stale request must not embed")

    monkeypatch.setattr(repository, "embed_texts", fail_if_called)

    applied = await repository.upsert_knowledge_document(_document(revision=3), ["正文"])

    assert applied is False


@pytest.mark.asyncio
async def test_replayed_same_revision_upsert_does_not_call_embedding(monkeypatch):
    doc = _document(revision=1)
    state = {
        "revision": 1,
        "status": "READY",
        "content_sha256": repository._knowledge_sha256(doc),
    }
    monkeypatch.setattr(repository, "get_pool", lambda: _Pool(_Connection([state])))

    async def fail_if_called(_chunks):
        raise AssertionError("idempotent replay must not embed")

    monkeypatch.setattr(repository, "embed_texts", fail_if_called)

    applied = await repository.upsert_knowledge_document(doc, ["正文"])

    assert applied is False


@pytest.mark.asyncio
async def test_same_revision_with_legacy_fingerprint_is_reindexed(monkeypatch):
    doc = _document(revision=1)
    connection = _Connection(
        [{"revision": 1, "status": "READY", "content_sha256": None}],
        locked_state={"revision": 1, "status": "READY", "content_sha256": None},
    )
    saved_checkpoints = {}
    monkeypatch.setattr(repository, "get_pool", lambda: _Pool(connection))
    monkeypatch.setattr(
        repository,
        "get_settings",
        lambda: SimpleNamespace(
            vector_embedding_dim=2,
            # 中文注释：与应用默认配置一致，限制并发生成并保存向量检查点的批次数。
            knowledge_embedding_batch_concurrency=2,
        ),
    )
    monkeypatch.setattr(
        repository, "build_embedding_fingerprint", lambda _settings: "fingerprint"
    )

    async def load_checkpoints(_conn, *_args):
        return dict(saved_checkpoints)

    async def store_checkpoints(
        _conn, _doc_id, _content_hash, _fingerprint, indices, hashes, vectors
    ):
        saved_checkpoints.update(
            (index, (chunk_hash, vector))
            for index, chunk_hash, vector in zip(indices, hashes, vectors, strict=True)
        )

    monkeypatch.setattr(repository, "load_knowledge_embedding_checkpoints", load_checkpoints)
    monkeypatch.setattr(repository, "store_knowledge_embedding_checkpoints", store_checkpoints)

    async def embed_chunks(_chunks, _settings):
        return [[0.1, 0.2]]

    monkeypatch.setattr(repository, "embed_texts", embed_chunks)

    applied = await repository.upsert_knowledge_document(doc, ["正文"])

    assert applied is True
    assert any(
        isinstance(statement, str) and "INSERT INTO knowledge_doc_state" in statement
        for statement in connection.executed
    )


@pytest.mark.asyncio
async def test_missing_saved_checkpoint_is_repaired_before_publishing(monkeypatch):
    doc = _document(revision=2)
    connection = _Connection([None], locked_state=None)
    saved_checkpoints = {}
    repair_calls = []
    monkeypatch.setattr(repository, "get_pool", lambda: _Pool(connection))
    monkeypatch.setattr(
        repository,
        "get_settings",
        lambda: SimpleNamespace(
            vector_embedding_dim=2,
            # 中文注释：与应用默认配置一致，限制并发生成并保存向量检查点的批次数。
            knowledge_embedding_batch_concurrency=2,
        ),
    )
    monkeypatch.setattr(
        repository, "build_embedding_fingerprint", lambda _settings: "fingerprint"
    )

    async def load_checkpoints(_conn, *_args):
        return dict(saved_checkpoints)

    async def persist_checkpoint_batch(*_args):
        # 中文注释：模拟检查点批次确认成功，但发布时重新读取暂时看不到该批次。
        return True

    async def repair_checkpoints(
        _conn, _doc_id, _content_hash, _fingerprint, indices, hashes, vectors
    ):
        repair_calls.append(list(indices))
        saved_checkpoints.update(
            (index, (chunk_hash, vector))
            for index, chunk_hash, vector in zip(indices, hashes, vectors, strict=True)
        )

    async def embed_chunks(_chunks, _settings):
        return [[0.1, 0.2]]

    monkeypatch.setattr(repository, "load_knowledge_embedding_checkpoints", load_checkpoints)
    monkeypatch.setattr(repository, "_store_checkpoint_batch", persist_checkpoint_batch)
    monkeypatch.setattr(repository, "store_knowledge_embedding_checkpoints", repair_checkpoints)
    monkeypatch.setattr(repository, "embed_texts", embed_chunks)

    applied = await repository.upsert_knowledge_document(doc, ["正文"])

    assert applied is True
    assert repair_calls == [[0]]
    assert any(isinstance(statement, tuple) for statement in connection.executed)


@pytest.mark.asyncio
async def test_embedding_finishing_after_newer_delete_cannot_restore_vectors(monkeypatch):
    doc = _document(revision=1)
    deleted_state = {"revision": 2, "status": "DELETED", "content_sha256": "3" * 64}
    connection = _Connection([None], locked_state=deleted_state)
    monkeypatch.setattr(repository, "get_pool", lambda: _Pool(connection))
    monkeypatch.setattr(
        repository,
        "get_settings",
        lambda: SimpleNamespace(
            vector_embedding_dim=2,
            # 中文注释：与应用默认配置一致，限制并发生成并保存向量检查点的批次数。
            knowledge_embedding_batch_concurrency=2,
        ),
    )
    monkeypatch.setattr(
        repository, "build_embedding_fingerprint", lambda _settings: "fingerprint"
    )

    async def embed_chunks(_chunks, _settings):
        return [[0.1, 0.2]]

    monkeypatch.setattr(repository, "embed_texts", embed_chunks)

    applied = await repository.upsert_knowledge_document(doc, ["正文"])

    assert applied is False
    assert not any(
        isinstance(statement, str) and statement.startswith("DELETE FROM knowledge_vector_chunk")
        for statement in connection.executed
    )
    assert not any(isinstance(statement, tuple) for statement in connection.executed)


@pytest.mark.asyncio
async def test_delete_with_older_revision_cannot_remove_newer_vectors(monkeypatch):
    connection = _Connection([{"revision": 5, "status": "READY", "content_sha256": "1" * 64}])
    monkeypatch.setattr(repository, "get_pool", lambda: _Pool(connection))

    applied, deleted_count = await repository.delete_knowledge_document("123", revision=4)

    assert (applied, deleted_count) == (False, 0)
    assert not any(
        isinstance(statement, str) and statement.startswith("DELETE FROM knowledge_vector_chunk")
        for statement in connection.executed
    )


@pytest.mark.asyncio
async def test_delete_acknowledges_chunk_count_and_marks_revision(monkeypatch):
    connection = _Connection(
        [{"revision": 2, "status": "READY", "content_sha256": "2" * 64}],
        delete_result="DELETE 3",
    )
    monkeypatch.setattr(repository, "get_pool", lambda: _Pool(connection))

    applied, deleted_count = await repository.delete_knowledge_document("123", revision=3)

    assert (applied, deleted_count) == (True, 3)
    assert any(
        isinstance(statement, str) and "knowledge_doc_state" in statement
        for statement in connection.executed
    )


@pytest.mark.asyncio
async def test_embedding_dimension_mismatch_is_rejected_before_database_write(monkeypatch):
    connection = _Connection([None])
    monkeypatch.setattr(repository, "get_pool", lambda: _Pool(connection))

    async def wrong_dimension(_chunks, _settings):
        return [[0.1, 0.2]]

    monkeypatch.setattr(repository, "embed_texts", wrong_dimension)

    with pytest.raises(RuntimeError, match="维度"):
        await repository.upsert_knowledge_document(_document(), ["正文"], expected_dim=1024)

    assert connection.executed == []


@pytest.mark.asyncio
async def test_similarity_search_knowledge_returns_unfiltered_bounded_candidates(monkeypatch):
    connection = _Connection(
        [None],
        rows=[
            {
                "doc_id": "123",
                "content": "标题: FAQ\n\n具体规则",
                "title": "FAQ",
                "section_path": "登录",
                "page_number": None,
                "score": 0.81,
                "exact_match": True,
                "content_match": False,
                "rank_score": 0.0321,
                "vector_rank_score": 0.0161,
                "lexical_rank_score": 0.0160,
                "vector_rank": 1,
                "lexical_rank": 1,
                "chunk_index": 3,
                "candidate_sources": ["vector", "lexical"],
            }
        ],
    )
    monkeypatch.setattr(repository, "get_pool", lambda: _Pool(connection))
    monkeypatch.setattr(
        repository,
        "get_settings",
        lambda: SimpleNamespace(vector_pool_acquire_timeout_seconds=1),
    )

    async def embed_query(_query):
        return [0.1, 0.2]

    monkeypatch.setattr(repository, "embed_text", embed_query)

    results = await repository.similarity_search_knowledge("FAQ", candidate_limit=23)

    assert len(results) == 1
    assert results[0].exact_match is True
    assert results[0].content_match is False
    assert results[0].rank_score == 0.0321
    assert results[0].vector_rank == 1
    assert results[0].lexical_rank == 1
    assert results[0].chunk_index == 3
    assert "JOIN knowledge_doc_state AS state" in connection.fetch_query
    assert "state.status = 'READY'" in connection.fetch_query
    assert "$4" in connection.fetch_query
    assert "$4::float IS NOT NULL" not in connection.fetch_query
    assert "vector_candidates" in connection.fetch_query
    assert "lexical_candidates" in connection.fetch_query
    assert "ready_chunks AS NOT MATERIALIZED" in connection.fetch_query
    assert "lower(chunk.content) LIKE" in connection.fetch_query
    assert "section_rank" not in connection.fetch_query
    assert "LIMIT $2" not in connection.fetch_query
    assert connection.fetch_args[3] == 23
    assert connection.fetch_args[2] == "faq"
