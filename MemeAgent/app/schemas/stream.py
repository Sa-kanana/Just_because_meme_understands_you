from typing import Any, Literal

from pydantic import BaseModel, Field


class ChatMessage(BaseModel):
    role: Literal["system", "user", "assistant"]
    content: str = Field(min_length=1, max_length=8000)


class BusinessContext(BaseModel):
    """由 Java 组装；Agent 不查业务 MySQL。"""

    user_id: str | None = None
    locale: str = "zh-CN"
    hint_meme_ids: list[str] = Field(default_factory=list)
    extra: dict[str, Any] = Field(default_factory=dict)


class StreamRequest(BaseModel):
    request_id: str = Field(min_length=1, max_length=64)
    session_id: str = Field(min_length=1, max_length=64)
    query: str = Field(min_length=1, max_length=2000)
    messages: list[ChatMessage] = Field(default_factory=list)
    context: BusinessContext = Field(default_factory=BusinessContext)
    max_tokens: int = Field(default=512, ge=64, le=2048)


class StreamMetaEvent(BaseModel):
    request_id: str
    retrieved: list[str] = Field(default_factory=list)


class StreamTokenEvent(BaseModel):
    text: str


class StreamCiteEvent(BaseModel):
    meme_id: str
    score: float
    title: str | None = None


class StreamDoneEvent(BaseModel):
    finish_reason: str = "stop"
    usage: dict[str, int] = Field(default_factory=dict)


class StreamErrorEvent(BaseModel):
    code: str
    message: str
