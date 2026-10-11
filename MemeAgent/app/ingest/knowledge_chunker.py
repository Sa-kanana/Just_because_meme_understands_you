"""管理员知识文档的标题、FAQ 与 PDF 页感知切块。"""

from __future__ import annotations

from dataclasses import dataclass
import re

from langchain_text_splitters import RecursiveCharacterTextSplitter

from app.schemas.knowledge import KnowledgeDocument

# 中文注释：知识向量片段的目标最大字符数，包含标题和章节元数据。
_DEFAULT_CHUNK_SIZE = 1000
# 中文注释：相邻正文片段之间保留的重叠字符数，减少边界信息丢失。
_DEFAULT_CHUNK_OVERLAP = 160
_HEADING_PATTERN = re.compile(r"^\s{0,3}(#{1,6})\s+(.+?)\s*#*\s*$")
_FAQ_PATTERN = re.compile(r"^\s*(?:Q\s*[:：.]|问\s*[:：]|问题\s*[:：])\s*(.+?)\s*$", re.IGNORECASE)


@dataclass(frozen=True)
class KnowledgeChunk:
    """用于向量写入的正文片段与可追溯章节元数据。"""

    # 中文注释：用于 embedding、检索和展示的完整片段文本。
    content: str
    # 中文注释：Markdown 标题或 FAQ 组成的章节路径。
    section_path: str = ""
    # 中文注释：PDF 原始页码；普通文本与 Markdown 不设置页码。
    page_number: int | None = None


def build_knowledge_chunks(
    doc: KnowledgeDocument,
    *,
    chunk_size: int = _DEFAULT_CHUNK_SIZE,
    chunk_overlap: int = _DEFAULT_CHUNK_OVERLAP,
) -> list[KnowledgeChunk]:
    """按 Markdown 标题/FAQ 和 PDF 页切分，跳过空章节和纯标题块。"""
    tags = [tag.strip() for tag in doc.tags if tag.strip()]
    category = (doc.category or "").strip()
    metadata_header = [f"标题: {doc.title.strip()}"]
    if category:
        metadata_header.append(f"分类: {category}")
    if tags:
        metadata_header.append(f"标签: {' '.join(tags)}")

    sections = _split_sections(doc.content or "")
    chunks: list[KnowledgeChunk] = []
    seen: set[str] = set()
    for section_path, page_number, body in sections:
        text = _deduplicate_exact_paragraphs(body)
        if not text:
            continue
        header = list(metadata_header)
        if section_path:
            header.append(f"章节: {section_path}")
        if page_number is not None:
            header.append(f"页码: {page_number}")
        header_text = "\n".join(header)
        budget = max(64, chunk_size - len(header_text) - 2)
        splitter = RecursiveCharacterTextSplitter(
            chunk_size=budget,
            chunk_overlap=min(max(0, chunk_overlap), max(0, budget - 1)),
            length_function=len,
            is_separator_regex=False,
            separators=["\n\n", "\n", "。", "！", "？", "；", "，", " ", ""],
        )
        body_parts = splitter.split_text(text) if len(text) > budget else [text]
        for body_part in body_parts:
            body_part = body_part.strip()
            if not body_part:
                continue
            value = f"{header_text}\n\n{body_part}"
            fingerprint = re.sub(r"\s+", " ", value).casefold().strip()
            if fingerprint in seen:
                continue
            seen.add(fingerprint)
            chunks.append(KnowledgeChunk(value, section_path, page_number))
    return chunks


def _split_sections(content: str) -> list[tuple[str, int | None, str]]:
    """保留 Markdown 标题路径与以 form-feed 标识的 PDF 页码。"""
    pages = (content or "").split("\f")
    has_page_breaks = len(pages) > 1
    headings: list[tuple[int, str]] = []
    sections: list[tuple[str, int | None, str]] = []
    current_path = ""
    current_lines: list[str] = []
    current_page: int | None = None

    def flush() -> None:
        nonlocal current_lines
        body = "\n".join(current_lines).strip()
        if body:
            sections.append((current_path, current_page, body))
        current_lines = []

    for page_index, page in enumerate(pages, start=1):
        if has_page_breaks:
            flush()
            current_page = page_index
        for line in page.splitlines():
            heading = _HEADING_PATTERN.match(line)
            faq = _FAQ_PATTERN.match(line)
            if heading:
                flush()
                level = len(heading.group(1))
                title = heading.group(2).strip()
                faq_heading = _FAQ_PATTERN.match(title)
                if faq_heading:
                    title = f"FAQ: {faq_heading.group(1).strip()}"
                headings = [(depth, value) for depth, value in headings if depth < level]
                headings.append((level, title))
                current_path = " / ".join(value for _depth, value in headings)
                continue
            if faq:
                flush()
                headings = [(depth, value) for depth, value in headings if depth < 7]
                headings.append((7, f"FAQ: {faq.group(1).strip()}"))
                current_path = " / ".join(value for _depth, value in headings)
                continue
            current_lines.append(line)
    flush()
    return sections


def _deduplicate_exact_paragraphs(text: str) -> str:
    """在切片前去除正文中的完全重复段落，同时保留首次出现顺序。"""
    paragraphs = re.split(r"\n\s*\n", text)
    result: list[str] = []
    seen: set[str] = set()
    for paragraph in paragraphs:
        value = paragraph.strip()
        fingerprint = re.sub(r"\s+", " ", value).casefold()
        if not value or fingerprint in seen:
            continue
        seen.add(fingerprint)
        result.append(value)
    return "\n\n".join(result)
