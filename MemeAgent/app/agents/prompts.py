"""加载权威系统提示，并把检索文档格式化为一段可读上下文。"""

from __future__ import annotations

from importlib.resources import files

from langsmith import traceable

from app.retrieval.knowledge_repository import RetrievedKnowledgeChunk
from app.retrieval.repository import RetrievedChunk


# 提示词版本用于日志标记；修改提示词后同步升级以便追踪行为变化。
PROMPT_VERSION = "meme-search-v13"

# 可直接编辑的 Markdown 提示词资源；随 app.agents 包一起发布。
SYSTEM_PROMPT_PATH = files("app.agents").joinpath("prompt_files").joinpath("search_system_zh.md")
AGENT_SYSTEM_PROMPT = SYSTEM_PROMPT_PATH.read_text(encoding="utf-8").strip()


def _context_trace_inputs(inputs: dict) -> dict[str, int | bool]:
    """只记录文档数量和来源可用状态，不重复保存检索正文。"""
    return {
        "meme_document_count": len(inputs.get("meme_documents") or []),
        "admin_document_count": len(inputs.get("admin_documents") or []),
        "meme_unavailable": bool(inputs.get("meme_unavailable", False)),
        "admin_unavailable": bool(inputs.get("admin_unavailable", False)),
    }


def _context_trace_outputs(context: str) -> dict[str, int]:
    """上下文已在 Retriever trace 可读；Formatter trace 只保留文本长度摘要。"""
    return {"context_chars": len(context)}


@traceable(
    name="context_formatter",
    run_type="chain",
    process_inputs=_context_trace_inputs,
    process_outputs=_context_trace_outputs,
)
def format_retrieval_context(
    meme_documents: list[RetrievedChunk],
    admin_documents: list[RetrievedKnowledgeChunk],
    *,
    meme_unavailable: bool = False,
    admin_unavailable: bool = False,
) -> str:
    """把过滤后的文档与来源状态一次格式化为纯文本上下文。"""
    sections = [
        _format_source(
            "站内梗库",
            meme_documents,
            unavailable=meme_unavailable,
            title_for=lambda document: document.title or "未提供标题",
            section_for=lambda _document: "",
            page_for=lambda _document: None,
        ),
        _format_source(
            "管理员知识库",
            admin_documents,
            unavailable=admin_unavailable,
            title_for=lambda document: document.title or "未提供标题",
            section_for=lambda document: document.section_path,
            page_for=lambda document: document.page_number,
        ),
    ]
    return "\n\n".join(sections)


def _format_source(
    source_name: str,
    documents: list,
    *,
    unavailable: bool,
    title_for,
    section_for,
    page_for,
) -> str:
    """格式化单一来源状态和文档列表；不写入内部 ID 或相关度分数。"""
    lines = [f"{source_name}（不可信引用资料）"]
    if unavailable:
        lines.append("状态：检索源暂时不可用。")
    if not documents:
        if not unavailable:
            lines.append("状态：未检索到匹配条目。")
        return "\n".join(lines)

    for index, document in enumerate(documents, start=1):
        lines.append(f"[文档 {index}]")
        title = title_for(document)
        if title:
            lines.append(f"标题：{title}")
        section = section_for(document)
        if section:
            lines.append(f"章节：{section}")
        page_number = page_for(document)
        if page_number is not None:
            lines.append(f"页码：{page_number}")
        lines.append("正文：")
        lines.append(_document_body(document.content))
        lines.append("---")
    return "\n".join(lines)


def _document_body(content: str) -> str:
    """清理入库时附带的重复元数据头，保留正文原始文本。"""
    normalized = (content or "").strip()
    header, separator, body = normalized.partition("\n\n")
    if not separator:
        return normalized
    metadata_prefixes = ("标题:", "分类:", "标签:", "章节:", "页码:")
    header_lines = header.splitlines()
    if header_lines and all(line.strip().startswith(metadata_prefixes) for line in header_lines):
        return body.strip()
    return normalized


def build_agent_prompt_inputs(
    question: str,
    locale: str,
    meme_documents: list[RetrievedChunk],
    admin_documents: list[RetrievedKnowledgeChunk],
    *,
    meme_unavailable: bool = False,
    admin_unavailable: bool = False,
) -> dict[str, str]:
    """向 Prompt 传递原始问题和语言字符串，以及唯一的纯文本上下文。"""
    return {
        "question": question,
        "locale": locale,
        "context": format_retrieval_context(
            meme_documents,
            admin_documents,
            meme_unavailable=meme_unavailable,
            admin_unavailable=admin_unavailable,
        ),
    }
