from app.schemas.ingest import IngestDocument


def build_chunks(doc: IngestDocument, max_chunk_chars: int = 1200) -> list[str]:
    tags = " ".join(tag.strip() for tag in doc.tags if tag.strip())
    header = f"标题: {doc.title.strip()}\n标签: {tags}".strip()
    body = doc.introduction.strip()
    full = f"{header}\n\n{body}".strip()
    if len(full) <= max_chunk_chars:
        return [full]

    prefix = f"{header}\n\n"
    body_chunk_chars = max(1, max_chunk_chars - len(prefix))
    chunks: list[str] = []
    start = 0
    while start < len(body):
        end = min(start + body_chunk_chars, len(body))
        chunks.append(f"{prefix}{body[start:end]}".strip())
        start = end
    return chunks
