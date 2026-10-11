from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field


class IngestDocument(BaseModel):
    meme_id: str = Field(min_length=1, max_length=64)
    title: str = Field(min_length=1, max_length=200)
    introduction: str = Field(default="", max_length=5000)
    tags: list[str] = Field(default_factory=list, max_length=20)
    status: Literal["published", "deleted"] = "published"
    updated_at: datetime
    content_hash: str = Field(min_length=8, max_length=128)


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
