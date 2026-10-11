from datetime import datetime, timezone
from types import SimpleNamespace

import pytest

from app.ingest import knowledge_worker
from app.schemas.knowledge import KnowledgeDocument, KnowledgeIngestRequest


@pytest.mark.asyncio
async def test_knowledge_ingest_uses_configured_chunk_experiment_parameters(monkeypatch):
    captured = {}
    document = KnowledgeDocument(
        doc_id="doc-1",
        title="上传规则",
        content="文件规则正文",
        updated_at=datetime.now(timezone.utc),
        content_hash="legacy-hash-123",
    )

    def build_chunks(_document, *, chunk_size, chunk_overlap):
        captured["chunk_size"] = chunk_size
        captured["chunk_overlap"] = chunk_overlap
        return ["chunk"]

    async def upsert(_document, chunks):
        captured["chunks"] = chunks
        return True

    monkeypatch.setattr(
        knowledge_worker,
        "get_settings",
        lambda: SimpleNamespace(knowledge_chunk_size=640, knowledge_chunk_overlap=96),
    )
    monkeypatch.setattr(knowledge_worker, "build_knowledge_chunks", build_chunks)
    monkeypatch.setattr(knowledge_worker, "upsert_knowledge_document", upsert)

    result = await knowledge_worker.process_knowledge_ingest(
        KnowledgeIngestRequest(documents=[document])
    )

    assert result == (1, 0)
    assert captured == {"chunk_size": 640, "chunk_overlap": 96, "chunks": ["chunk"]}
