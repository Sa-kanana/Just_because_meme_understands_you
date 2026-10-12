from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field, field_validator

from app.core.limits import (
    MAX_TAG_CHARS,
    MAX_TAG_COUNT,
    MAX_TOTAL_TAG_CHARS,
)


class IngestDocument(BaseModel):
    meme_id: str = Field(min_length=1, max_length=64)
    title: str = Field(min_length=1, max_length=200)
    introduction: str = Field(default="", max_length=5000)
    tags: list[str] = Field(default_factory=list, max_length=MAX_TAG_COUNT)
    status: Literal["published", "deleted"] = "published"
    updated_at: datetime
    content_hash: str = Field(min_length=8, max_length=128)

    @field_validator("tags")
    @classmethod
    def validate_tags(cls, tags: list[str]) -> list[str]:
        normalized = [tag.strip() for tag in tags]
        if any(len(tag) > MAX_TAG_CHARS for tag in normalized):
            raise ValueError(f"单个标签去除首尾空白后不能超过 {MAX_TAG_CHARS} 个字符")
        total_chars = sum(len(tag) for tag in normalized)
        if total_chars > MAX_TOTAL_TAG_CHARS:
            raise ValueError(
                f"标签去除首尾空白后的累计长度不能超过 {MAX_TOTAL_TAG_CHARS} 个字符"
            )
        return normalized


class IngestRequest(BaseModel):
    documents: list[IngestDocument] = Field(min_length=1, max_length=50)


class IngestAcceptedResponse(BaseModel):
    """兼容旧字段结构；接口返回时本批向量任务已经完成。"""

    # 中文注释：表示请求已成功处理完成，保留该字段兼容原有内部调用方。
    accepted: bool = True
    # 中文注释：已经完成的文档向量处理或删除任务数。
    task_count: int
    # 中文注释：本批次中因数据未变化等原因而跳过的任务数。
    skipped: int = 0


class IngestDeleteRequest(BaseModel):
    meme_ids: list[str] = Field(min_length=1, max_length=100)


class IngestDeleteResponse(BaseModel):
    deleted_count: int


class MemeIdPageResponse(BaseModel):
    meme_ids: list[str] = Field(description="按梗 ID 排序的向量库梗 ID 列表")
    next_cursor: str | None = Field(
        description="下一页游标；为 null 时表示当前页是最后一页"
    )
