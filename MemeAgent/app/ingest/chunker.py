from app.core.limits import MAX_CHUNK_CHARS, MAX_CHUNKS_PER_MEME
from app.schemas.ingest import IngestDocument


def build_chunks(doc: IngestDocument, max_chunk_chars: int = MAX_CHUNK_CHARS) -> list[str]:
    if not isinstance(max_chunk_chars, int) or isinstance(max_chunk_chars, bool):
        raise ValueError("分块字符上限必须是整数")
    if not 1 <= max_chunk_chars <= MAX_CHUNK_CHARS:
        raise ValueError(f"分块字符上限必须在 1 到 {MAX_CHUNK_CHARS} 之间")

    title_header = f"标题: {doc.title.strip()}"
    tags = " ".join(tag for tag in doc.tags if tag)
    first_header = f"{title_header}\n标签: {tags}" if tags else title_header
    body = doc.introduction.strip()
    full = f"{first_header}\n\n{body}" if body else first_header
    if len(full) <= max_chunk_chars:
        return [full]

    first_prefix = f"{first_header}\n\n"
    later_prefix = f"{title_header}\n\n"
    first_capacity = max_chunk_chars - len(first_prefix)
    later_capacity = max_chunk_chars - len(later_prefix)
    if first_capacity <= 0 or later_capacity <= 0:
        raise ValueError(
            f"梗 {doc.meme_id} 的标题或标签前缀超过分块字符上限 {max_chunk_chars}"
        )

    required_chunks = 1
    if len(body) > first_capacity:
        remaining_chars = len(body) - first_capacity
        required_chunks += (remaining_chars + later_capacity - 1) // later_capacity
    if required_chunks > MAX_CHUNKS_PER_MEME:
        raise ValueError(
            f"梗 {doc.meme_id} 分块数 {required_chunks} 超过上限 {MAX_CHUNKS_PER_MEME}"
        )

    chunks: list[str] = []
    start = 0
    for index in range(required_chunks):
        prefix = first_prefix if index == 0 else later_prefix
        capacity = first_capacity if index == 0 else later_capacity
        segment = body[start : start + capacity]
        chunks.append(f"{prefix}{segment}")
        start += len(segment)

    return chunks
