from datetime import datetime, timezone

import pytest
from pydantic import ValidationError

from app.api import knowledge as knowledge_api
from app.schemas.knowledge import (
    KnowledgeDeleteRequest,
    KnowledgeDocument,
    KnowledgeIngestRequest,
)


def _document(status="published"):
    return KnowledgeDocument(
        doc_id="123",
        title="管理员 FAQ",
        content="知识正文",
        status=status,
        updated_at=datetime.now(timezone.utc),
        content_hash="legacy-content-hash",
        revision=4,
    )


@pytest.mark.asyncio
async def test_ingest_endpoint_waits_for_vector_transaction_before_acknowledging(monkeypatch):
    processed = False

    async def process_request(_payload):
        nonlocal processed
        processed = True
        return 1, 0

    monkeypatch.setattr(knowledge_api, "process_knowledge_ingest", process_request)

    response = await knowledge_api.ingest_knowledge(KnowledgeIngestRequest(documents=[_document()]), None)

    assert processed is True
    assert response.accepted is True
    assert response.task_count == 1
    assert response.skipped == 0


@pytest.mark.asyncio
async def test_versioned_delete_endpoint_waits_for_vector_delete_ack(monkeypatch):
    received = {}

    async def delete_documents(doc_ids, revision):
        received["doc_ids"] = doc_ids
        received["revision"] = revision
        return 3

    monkeypatch.setattr(knowledge_api, "delete_knowledge_by_ids", delete_documents)
    request = KnowledgeDeleteRequest(doc_ids=["123"], revision=4)

    response = await knowledge_api.ingest_knowledge_delete(request, None)

    assert received == {"doc_ids": ["123"], "revision": 4}
    assert response.deleted_count == 3


def test_knowledge_delete_request_requires_authoritative_revision():
    with pytest.raises(ValidationError):
        KnowledgeDeleteRequest(doc_ids=["123"])
