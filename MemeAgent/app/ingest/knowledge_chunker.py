"""管理员知识文档切块：LangChain RecursiveCharacterTextSplitter。"""

from __future__ import annotations

from langchain_core.documents import Document
from langchain_text_splitters import RecursiveCharacterTextSplitter

from app.schemas.knowledge import KnowledgeDocument

_DEFAULT_CHUNK_SIZE = 1000
_DEFAULT_CHUNK_OVERLAP = 200


def build_knowledge_chunks(
    doc: KnowledgeDocument,
    *,
    chunk_size: int = _DEFAULT_CHUNK_SIZE,
    chunk_overlap: int = _DEFAULT_CHUNK_OVERLAP,
) -> list[str]:
    tags = " ".join(tag.strip() for tag in doc.tags if tag.strip())
    category = (doc.category or "").strip()
    header_parts = [f"标题: {doc.title.strip()}"]
    if category:
        header_parts.append(f"分类: {category}")
    if tags:
        header_parts.append(f"标签: {tags}")
    header = "\n".join(header_parts)
    body = (doc.content or "").strip()
    full = f"{header}\n\n{body}".strip()

    if len(full) <= chunk_size:
        return [full]

    splitter = RecursiveCharacterTextSplitter(
        chunk_size=chunk_size,
        chunk_overlap=chunk_overlap,
        length_function=len,
        is_separator_regex=False,
        separators=["\n\n", "\n", "。", "！", "？", "；", " ", ""],
    )
    lc_docs = splitter.create_documents(
        [body],
        metadatas=[{"doc_id": doc.doc_id, "title": doc.title}],
    )
    chunks: list[str] = [header]
    for item in lc_docs:
        text = item.page_content.strip() if isinstance(item, Document) else str(item).strip()
        if text:
            chunks.append(f"{header}\n\n{text}")
    return chunks or [full]
