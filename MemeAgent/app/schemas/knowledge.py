from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field


class KnowledgeDocument(BaseModel):
    """管理员知识文档（与梗向量分离，doc_id 由 Java 雪花 ID 传入）。"""

    doc_id: str = Field(min_length=1, max_length=64)
    title: str = Field(min_length=1, max_length=200)
    content: str = Field(min_length=1, max_length=200_000)
    category: str = Field(default="", max_length=64)
    tags: list[str] = Field(default_factory=list, max_length=20)
    status: Literal["published", "deleted"] = "published"
    updated_at: datetime
    content_hash: str = Field(min_length=8, max_length=128)


class KnowledgeIngestRequest(BaseModel):
    documents: list[KnowledgeDocument] = Field(min_length=1, max_length=20)


class KnowledgeIngestAcceptedResponse(BaseModel):
    accepted: bool = True
    task_count: int
    skipped: int = 0


class KnowledgeDeleteRequest(BaseModel):
    doc_ids: list[str] = Field(min_length=1, max_length=100)


class KnowledgeDeleteResponse(BaseModel):
    deleted_count: int
