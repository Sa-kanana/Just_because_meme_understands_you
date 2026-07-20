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
    accepted: bool = True
    task_count: int
    skipped: int = 0


class IngestDeleteRequest(BaseModel):
    meme_ids: list[str] = Field(min_length=1, max_length=100)


class IngestDeleteResponse(BaseModel):
    deleted_count: int
