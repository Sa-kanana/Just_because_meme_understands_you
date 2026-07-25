from typing import Any, Literal

from pydantic import AliasChoices, BaseModel, ConfigDict, Field, field_validator


class ChatMessage(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    role: Literal["system", "user", "assistant"]
    content: str = Field(
        default="",
        max_length=8000,
        validation_alias=AliasChoices("content", "Content"),
    )

    @field_validator("content", mode="before")
    @classmethod
    def coerce_content(cls, value: Any) -> str:
        if value is None:
            return ""
        return str(value)


class BusinessContext(BaseModel):
    """由 Java 组装；Agent 不查业务 MySQL。"""

    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    user_id: str | None = Field(
        default=None,
        validation_alias=AliasChoices("user_id", "userId"),
    )
    locale: str = "zh-CN"
    hint_meme_ids: list[str] = Field(
        default_factory=list,
        validation_alias=AliasChoices("hint_meme_ids", "hintMemeIds"),
    )
    extra: dict[str, Any] = Field(default_factory=dict)

    @field_validator("hint_meme_ids", mode="before")
    @classmethod
    def coerce_hint_ids(cls, value: Any) -> list[str]:
        if value is None:
            return []
        if not isinstance(value, list):
            return [str(value)]
        return [str(item).strip() for item in value if str(item).strip()]


class StreamRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    request_id: str = Field(
        min_length=1,
        max_length=64,
        validation_alias=AliasChoices("request_id", "requestId"),
    )
    session_id: str = Field(
        min_length=1,
        max_length=64,
        validation_alias=AliasChoices("session_id", "sessionId"),
    )
    query: str = Field(min_length=1, max_length=2000)
    messages: list[ChatMessage] = Field(default_factory=list)
    context: BusinessContext = Field(default_factory=BusinessContext)
    max_tokens: int = Field(
        default=512,
        ge=64,
        le=8192,
        validation_alias=AliasChoices("max_tokens", "maxTokens"),
    )

    @field_validator("max_tokens", mode="before")
    @classmethod
    def clamp_max_tokens(cls, value: Any) -> Any:
        if value is None:
            return 512
        try:
            n = int(value)
        except (TypeError, ValueError):
            return 512
        return max(64, min(n, 8192))

    @field_validator("messages", mode="after")
    @classmethod
    def drop_blank_messages(cls, value: list[ChatMessage]) -> list[ChatMessage]:
        return [msg for msg in value if msg.content and msg.content.strip()]


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
