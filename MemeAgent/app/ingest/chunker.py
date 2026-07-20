from app.schemas.ingest import IngestDocument


def build_chunks(doc: IngestDocument, max_chunk_chars: int = 1200) -> list[str]:
    tags = " ".join(tag.strip() for tag in doc.tags if tag.strip())
    header = f"标题: {doc.title.strip()}\n标签: {tags}".strip()
    body = doc.introduction.strip()
    full = f"{header}\n\n{body}".strip()
    if len(full) <= max_chunk_chars:
        return [full]

    chunks: list[str] = [header]
    start = 0
    while start < len(body):
        end = min(start + max_chunk_chars, len(body))
        chunks.append(body[start:end])
        start = end
    return chunks
