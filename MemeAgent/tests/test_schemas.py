import os

os.environ.setdefault("INTERNAL_API_KEY", "test-key")
os.environ.setdefault("VECTOR_DATABASE_URL", "")

from datetime import datetime, timezone

import pytest
from pydantic import ValidationError

from app.schemas.ingest import IngestDocument, IngestRequest
from app.schemas.stream import BusinessContext, StreamRequest


def test_ingest_request_limits():
    doc = IngestDocument(
        meme_id="1",
        title="测试梗",
        introduction="介绍",
        tags=["热梗"],
        updated_at=datetime.now(timezone.utc),
        content_hash="abc12345",
    )
    req = IngestRequest(documents=[doc])
    assert len(req.documents) == 1


def _ingest_document(tags):
    return {
        "meme_id": "1",
        "title": "梗标题",
        "introduction": "介绍",
        "tags": tags,
        "updated_at": datetime.now(timezone.utc),
        "content_hash": "abc12345",
    }


def test_ingest_accepts_twenty_tags_and_trimmed_64_character_tag():
    tags = ["标签"] * 19 + [f" {'x' * 64} "]

    document = IngestDocument(**_ingest_document(tags))

    assert len(document.tags) == 20
    assert document.tags[-1] == "x" * 64


def test_ingest_rejects_tag_longer_than_64_trimmed_characters():
    with pytest.raises(ValidationError):
        IngestDocument(**_ingest_document(["x" * 65]))


def test_ingest_rejects_total_trimmed_tag_text_over_240_characters():
    with pytest.raises(ValidationError):
        IngestDocument(**_ingest_document(["x" * 64] * 3 + ["x" * 49]))


def test_ingest_accepts_total_trimmed_tag_text_at_240_characters():
    document = IngestDocument(**_ingest_document(["x" * 64] * 3 + ["x" * 48]))

    assert sum(len(tag.strip()) for tag in document.tags) == 240


def test_stream_request_defaults():
    req = StreamRequest(
        request_id="r1",
        session_id="s1",
        query="这个梗什么意思",
    )
    assert req.context.locale == "zh-CN"
    assert req.max_tokens == 512
