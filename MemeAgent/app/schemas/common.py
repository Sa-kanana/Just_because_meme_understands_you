from typing import Any, Literal

from pydantic import BaseModel, Field


class HealthResponse(BaseModel):
    """公开探活，禁止附带模型/密钥细节。"""

    status: Literal["ok", "degraded"] = "ok"
    service: str
    env: str
    vector_db: bool = False


class HealthDetailResponse(HealthResponse):
    """需内网鉴权的运维视图。"""

    chat_model: str | None = None
    embedding_model: str | None = None
    openai_base_host: str | None = None
    write_key_configured: bool = False


class ErrorResponse(BaseModel):
    code: str
    message: str
    details: dict[str, Any] = Field(default_factory=dict)
