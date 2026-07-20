from app.ingest.chunker import build_chunks
from app.schemas.ingest import IngestDocument
from datetime import datetime, timezone


def test_build_chunks_includes_title():
    doc = IngestDocument(
        meme_id="42",
        title="只因你太美",
        introduction="来自某综艺的流行梗",
        tags=["音乐", "流行"],
        updated_at=datetime.now(timezone.utc),
        content_hash="hashhash12",
    )
    chunks = build_chunks(doc)
    assert len(chunks) >= 1
    assert "只因你太美" in chunks[0]
    assert "音乐" in chunks[0]
