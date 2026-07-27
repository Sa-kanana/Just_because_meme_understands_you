"""Firecrawl 实时热梗采集契约。"""

from __future__ import annotations

from pydantic import BaseModel, Field


class CrawlHotMemesRequest(BaseModel):
    """Java 触发的热梗采集请求。"""

    query: str | None = Field(
        default=None,
        max_length=200,
        description="自定义梗词；为空则抓 B 站投稿/空间发现新梗",
    )
    limit: int = Field(default=5, ge=1, le=20, description="返回候选条数上限（默认对齐投稿前5条）")
    include_markdown: bool = Field(
        default=True,
        description="是否附带页面正文摘要（Firecrawl scrape_options）",
    )


class CrawlMemeCandidate(BaseModel):
    """结构化热梗候选，供 Java 写入业务库。"""

    title: str = Field(min_length=1, max_length=200)
    introduction: str = Field(default="", max_length=5000)
    source_url: str = Field(min_length=1, max_length=1000, description="主出处，优先 B 站原视频")
    image_url: str | None = Field(default=None, max_length=1000, description="封面/配图 URL")
    detail_url: str | None = Field(
        default=None,
        max_length=1000,
        description="Firecrawl 补充详情页（可选第二资源）",
    )
    tags: list[str] = Field(default_factory=list, max_length=10)
    score: float = Field(default=0.0, description="排序分，越高越优先入库")


class CrawlHotMemesResponse(BaseModel):
    query_used: list[str] = Field(default_factory=list)
    candidates: list[CrawlMemeCandidate] = Field(default_factory=list)
    credits_hint: str | None = None
