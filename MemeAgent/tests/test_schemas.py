import os

os.environ.setdefault("INTERNAL_API_KEY", "test-key")
os.environ.setdefault("VECTOR_DATABASE_URL", "")

from datetime import datetime, timezone

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


def test_stream_request_defaults():
    req = StreamRequest(
        request_id="r1",
        session_id="s1",
        query="这个梗什么意思",
    )
    assert req.context.locale == "zh-CN"
    assert req.max_tokens == 512
