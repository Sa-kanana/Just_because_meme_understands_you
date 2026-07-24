from functools import lru_cache
import os

from pydantic import AliasChoices, Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        populate_by_name=True,
    )

    app_name: str = Field(default="meme-agent", alias="APP_NAME")
    app_env: str = Field(default="dev", alias="APP_ENV")
    app_host: str = Field(default="0.0.0.0", alias="APP_HOST")
    app_port: int = Field(default=8000, alias="APP_PORT")
    log_level: str = Field(default="INFO", alias="LOG_LEVEL")

    internal_api_key: str = Field(default="", alias="INTERNAL_API_KEY")

    vector_database_url: str = Field(default="", alias="VECTOR_DATABASE_URL")
    vector_embedding_dim: int = Field(default=1536, alias="VECTOR_EMBEDDING_DIM")

    openai_api_key: str = Field(default="", alias="OPENAI_API_KEY")
    openai_base_url: str | None = Field(default=None, alias="OPENAI_BASE_URL")
    openai_chat_model: str = Field(default="gpt-4o-mini", alias="OPENAI_CHAT_MODEL")
    openai_embedding_model: str = Field(
        default="text-embedding-3-small",
        alias="OPENAI_EMBEDDING_MODEL",
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

    retrieval_top_k: int = Field(default=5, alias="RETRIEVAL_TOP_K")
    max_output_tokens: int = Field(default=512, alias="MAX_OUTPUT_TOKENS")

    @field_validator("internal_api_key")
    @classmethod
    def validate_internal_api_key(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("INTERNAL_API_KEY is required")
        return value.strip()

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
    settings = Settings()
    apply_langsmith_env(settings)
    return settings
