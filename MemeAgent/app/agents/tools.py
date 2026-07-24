"""LangChain tools：站内梗检索（pgvector + MySQL hint 融合）。"""

from __future__ import annotations

import json
import logging
from typing import Any

from langchain_core.tools import StructuredTool
from pydantic import BaseModel, Field

from app.retrieval.repository import (
    RetrievedChunk,
    fetch_chunks_by_meme_ids,
    merge_retrieved,
    similarity_search,
)

logger = logging.getLogger(__name__)


class SearchMemeInput(BaseModel):
    query: str = Field(description="用户搜梗问句或改写后的检索关键词")
    hint_meme_ids: list[str] = Field(
        default_factory=list,
        description="Java 预检索给出的候选 meme_id 列表，可为空",
    )
    top_k: int = Field(default=5, ge=1, le=20, description="返回条数上限")


async def retrieve_meme_chunks(
    query: str,
    hint_meme_ids: list[str] | None = None,
    mysql_snippets: list[dict[str, Any]] | None = None,
    top_k: int = 5,
) -> list[RetrievedChunk]:
    """供 Agent tool 与 SSE 编排共用的检索实现。"""
    hints = [str(x).strip() for x in (hint_meme_ids or []) if str(x).strip()]
    vector_hits = await similarity_search(query, top_k)
    hint_hits = await fetch_chunks_by_meme_ids(hints) if hints else []
    mysql_chunks: list[RetrievedChunk] = []
    for item in mysql_snippets or []:
        if not isinstance(item, dict):
            continue
        meme_id = str(item.get("meme_id") or item.get("memeId") or "").strip()
        if not meme_id:
            continue
        title = str(item.get("title") or "").strip()
        intro = str(item.get("introduction") or "").strip()
        tags = item.get("tags") or []
        tag_text = (
            " ".join(str(t).strip() for t in tags if str(t).strip())
            if isinstance(tags, list)
            else ""
        )
        content = f"标题: {title}\n标签: {tag_text}\n\n{intro}".strip()
        mysql_chunks.append(
            RetrievedChunk(
                meme_id=meme_id,
                content=content,
                score=0.45,
                title=title or None,
            )
        )
    return merge_retrieved(vector_hits, hint_hits, mysql_chunks, top_k)


def chunks_to_tool_payload(chunks: list[RetrievedChunk]) -> str:
    payload = [
        {
            "meme_id": c.meme_id,
            "score": round(c.score, 4),
            "title": c.title,
            "content": c.content,
        }
        for c in chunks
    ]
    return json.dumps(payload, ensure_ascii=False)


def parse_tool_payload(raw: str) -> list[RetrievedChunk]:
    try:
        data = json.loads(raw)
    except json.JSONDecodeError:
        return []
    if not isinstance(data, list):
        return []
    out: list[RetrievedChunk] = []
    for item in data:
        if not isinstance(item, dict):
            continue
        meme_id = str(item.get("meme_id") or "").strip()
        if not meme_id:
            continue
        out.append(
            RetrievedChunk(
                meme_id=meme_id,
                content=str(item.get("content") or ""),
                score=float(item.get("score") or 0),
                title=(str(item["title"]) if item.get("title") is not None else None),
            )
        )
    return out


def build_search_meme_tool(
    *,
    default_hint_ids: list[str] | None = None,
    default_snippets: list[dict[str, Any]] | None = None,
    default_top_k: int = 5,
) -> StructuredTool:
    """构建带闭包上下文的检索 Tool（每次请求一份）。"""

    hints = list(default_hint_ids or [])
    snippets = list(default_snippets or [])
    top_k = default_top_k

    async def _run(query: str, hint_meme_ids: list[str] | None = None, top_k: int = 5) -> str:
        merged_hints = list(hint_meme_ids or []) or hints
        k = top_k or default_top_k
        chunks = await retrieve_meme_chunks(
            query=query,
            hint_meme_ids=merged_hints,
            mysql_snippets=snippets,
            top_k=k,
        )
        logger.info("search_meme_knowledge hits=%s query=%s", len(chunks), query[:80])
        return chunks_to_tool_payload(chunks)

    return StructuredTool.from_function(
        coroutine=_run,
        name="search_meme_knowledge",
        description=(
            "检索站内梗知识库（pgvector 语义检索，并融合 Java 传入的 MySQL 候选）。"
            "回答用户搜梗问题前必须先调用本工具；不要编造 meme_id。"
        ),
        args_schema=SearchMemeInput,
    )
