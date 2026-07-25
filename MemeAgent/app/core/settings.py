from functools import lru_cache
import logging
import os
from pathlib import Path

from pydantic import AliasChoices, Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

# 固定指向 MemeAgent 根目录，避免从仓库根/IDE 启动时读不到 .env
_MEMEAGENT_ROOT = Path(__file__).resolve().parents[2]
_ENV_FILE = _MEMEAGENT_ROOT / ".env"

logger = logging.getLogger(__name__)


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=str(_ENV_FILE),
        env_file_encoding="utf-8",
        extra="ignore",
        populate_by_name=True,
        # 空字符串环境变量不覆盖 .env（常见于 IDE Run Config）
        env_ignore_empty=True,
    )

    app_name: str = Field(default="meme-agent", validation_alias=AliasChoices("APP_NAME", "app_name"))
    app_env: str = Field(default="dev", validation_alias=AliasChoices("APP_ENV", "app_env"))
    app_host: str = Field(default="0.0.0.0", validation_alias=AliasChoices("APP_HOST", "app_host"))
    app_port: int = Field(default=8000, validation_alias=AliasChoices("APP_PORT", "app_port"))
    log_level: str = Field(default="INFO", validation_alias=AliasChoices("LOG_LEVEL", "log_level"))

    internal_api_key: str = Field(
        default="",
        validation_alias=AliasChoices("INTERNAL_API_KEY", "internal_api_key"),
    )

    vector_database_url: str = Field(
        default="",
        validation_alias=AliasChoices("VECTOR_DATABASE_URL", "vector_database_url"),
    )
    vector_embedding_dim: int = Field(
        default=1536,
        validation_alias=AliasChoices("VECTOR_EMBEDDING_DIM", "vector_embedding_dim"),
    )

    openai_api_key: str = Field(
        default="",
        validation_alias=AliasChoices("OPENAI_API_KEY", "openai_api_key"),
    )
    openai_base_url: str | None = Field(
        default=None,
        validation_alias=AliasChoices("OPENAI_BASE_URL", "openai_base_url"),
    )
    openai_chat_model: str = Field(
        default="gpt-4o-mini",
        validation_alias=AliasChoices("OPENAI_CHAT_MODEL", "openai_chat_model"),
    )
    openai_embedding_model: str = Field(
        default="text-embedding-3-small",
        validation_alias=AliasChoices("OPENAI_EMBEDDING_MODEL", "openai_embedding_model"),
    )

    # 同时支持 LANGSMITH_*（官方推荐）与 LANGCHAIN_*
    langchain_tracing_v2: bool = Field(
        default=False,
        validation_alias=AliasChoices("LANGCHAIN_TRACING_V2", "LANGSMITH_TRACING"),
    )
    langchain_api_key: str | None = Field(
        default=None,
        validation_alias=AliasChoices("LANGCHAIN_API_KEY", "LANGSMITH_API_KEY"),
    )
    langchain_project: str = Field(
        default="memeagent",
        validation_alias=AliasChoices("LANGCHAIN_PROJECT", "LANGSMITH_PROJECT"),
    )

    retrieval_top_k: int = Field(
        default=5,
        validation_alias=AliasChoices("RETRIEVAL_TOP_K", "retrieval_top_k"),
    )
    max_output_tokens: int = Field(
        default=512,
        validation_alias=AliasChoices("MAX_OUTPUT_TOKENS", "max_output_tokens"),
    )

    @field_validator("internal_api_key")
    @classmethod
    def validate_internal_api_key(cls, value: str) -> str:
        if not value or not str(value).strip():
            raise ValueError(
                f"INTERNAL_API_KEY is required（请在 {_ENV_FILE} 中配置，"
                "或设置同名环境变量）"
            )
        return str(value).strip()

    @property
    def is_dev(self) -> bool:
        return self.app_env.lower() in {"dev", "development", "local"}


def apply_langsmith_env(settings: Settings) -> None:
    """把 Settings 写入 os.environ，供 LangSmith SDK 读取。"""
    if not settings.langchain_tracing_v2:
        return

    os.environ["LANGCHAIN_TRACING_V2"] = "true"
    os.environ["LANGSMITH_TRACING"] = "true"
    if settings.langchain_api_key:
        os.environ["LANGCHAIN_API_KEY"] = settings.langchain_api_key
        os.environ["LANGSMITH_API_KEY"] = settings.langchain_api_key
    project = (settings.langchain_project or "memeagent").strip() or "memeagent"
    os.environ["LANGCHAIN_PROJECT"] = project
    os.environ["LANGSMITH_PROJECT"] = project


@lru_cache
def get_settings() -> Settings:
    if not _ENV_FILE.is_file():
        logger.warning("未找到配置文件: %s", _ENV_FILE)
    settings = Settings()
    apply_langsmith_env(settings)
    return settings
