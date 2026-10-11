from functools import lru_cache
import logging
import os
from pathlib import Path
from typing import Annotated

from pydantic import AliasChoices, Field, field_validator, model_validator
from pydantic_settings import BaseSettings, NoDecode, SettingsConfigDict

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
    # 中文注释：Uvicorn 收到关停信号后等待在途 HTTP/SSE 请求完成的秒数，超时后取消请求。
    uvicorn_graceful_shutdown_timeout_seconds: int = Field(
        default=5,
        ge=0,
        le=120,
        validation_alias=AliasChoices(
            "UVICORN_GRACEFUL_SHUTDOWN_TIMEOUT_SECONDS",
            "uvicorn_graceful_shutdown_timeout_seconds",
        ),
    )

    internal_api_key: str = Field(
        default="",
        validation_alias=AliasChoices("INTERNAL_API_KEY", "internal_api_key"),
    )
    # 写接口密钥（ingest/crawl）；空则与 INTERNAL_API_KEY 相同（仅建议开发环境）
    internal_api_key_write: str = Field(
        default="",
        validation_alias=AliasChoices(
            "INTERNAL_API_KEY_WRITE",
            "internal_api_key_write",
        ),
    )

    vector_database_url: str = Field(
        default="",
        validation_alias=AliasChoices("VECTOR_DATABASE_URL", "vector_database_url"),
    )
    # 管理员知识 embedding 检查点保留天数；只清理未完成的临时向量，正式索引不受影响。
    knowledge_embedding_checkpoint_ttl_days: int = Field(
        default=7,
        ge=1,
        le=365,
        validation_alias=AliasChoices(
            "KNOWLEDGE_EMBEDDING_CHECKPOINT_TTL_DAYS",
            "knowledge_embedding_checkpoint_ttl_days",
        ),
    )
    # HNSW vector 索引的维度上限；pgvector 的 vector HNSW 索引最多支持 2000 维。
    vector_embedding_dim: int = Field(
        default=1536,
        ge=1,
        le=2000,
        validation_alias=AliasChoices("VECTOR_EMBEDDING_DIM", "vector_embedding_dim"),
    )
    # 梗向量检索的最小 cosine 相似度，需由 app/eval/golden_queries.json 离线校准。
    vector_min_similarity: float = Field(
        default=0.6322177756757377,
        ge=-1.0,
        le=1.0,
        validation_alias=AliasChoices("VECTOR_MIN_SIMILARITY", "vector_min_similarity"),
    )
    # 每个 MemeAgent worker 的 PostgreSQL 连接池最小连接数。
    vector_pool_min_size: int = Field(
        default=1,
        ge=1,
        le=32,
        validation_alias=AliasChoices("VECTOR_POOL_MIN_SIZE", "vector_pool_min_size"),
    )
    # 每个 MemeAgent worker 的 PostgreSQL 连接池最大连接数；部署时按 worker 数和数据库连接预算设置。
    vector_pool_max_size: int = Field(
        default=10,
        ge=1,
        le=64,
        validation_alias=AliasChoices("VECTOR_POOL_MAX_SIZE", "vector_pool_max_size"),
    )
    # 等待 PostgreSQL 连接池空闲连接的最长时间（秒）。
    vector_pool_acquire_timeout_seconds: float = Field(
        default=5,
        gt=0,
        le=60,
        validation_alias=AliasChoices(
            "VECTOR_POOL_ACQUIRE_TIMEOUT_SECONDS",
            "vector_pool_acquire_timeout_seconds",
        ),
    )
    # PostgreSQL 单条命令超时时间（秒）。
    vector_db_command_timeout_seconds: float = Field(
        default=30,
        gt=0,
        le=300,
        validation_alias=AliasChoices(
            "VECTOR_DB_COMMAND_TIMEOUT_SECONDS",
            "vector_db_command_timeout_seconds",
        ),
    )
    # 进程内查询 embedding 缓存最多保留的向量条数，设为 0 可关闭缓存。
    embedding_cache_max_entries: int = Field(
        default=128,
        ge=0,
        le=4096,
        validation_alias=AliasChoices(
            "EMBEDDING_CACHE_MAX_ENTRIES",
            "embedding_cache_max_entries",
        ),
    )
    # 查询 embedding 缓存有效期（秒），设为 0 可关闭缓存。
    embedding_cache_ttl_seconds: int = Field(
        default=300,
        ge=0,
        le=86400,
        validation_alias=AliasChoices(
            "EMBEDDING_CACHE_TTL_SECONDS",
            "embedding_cache_ttl_seconds",
        ),
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
    # 单次聊天模型请求超时（秒）
    llm_request_timeout_seconds: float = Field(
        default=35,
        gt=0,
        le=300,
        validation_alias=AliasChoices(
            "LLM_REQUEST_TIMEOUT_SECONDS",
            "llm_request_timeout_seconds",
        ),
    )
    # 单次聊天模型请求最大重试次数（不含第一次请求）
    llm_max_retries: int = Field(
        default=2,
        ge=0,
        le=5,
        validation_alias=AliasChoices("LLM_MAX_RETRIES", "llm_max_retries"),
    )
    # 仅在模型供应商支持时请求流式 token 用量，默认关闭以兼容 OpenAI 兼容端点
    llm_stream_usage: bool = Field(
        default=False,
        validation_alias=AliasChoices("LLM_STREAM_USAGE", "llm_stream_usage"),
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
    # 站内向量与运营知识检索单次超时（秒）
    retrieval_timeout_seconds: float = Field(
        default=12,
        gt=0,
        le=120,
        validation_alias=AliasChoices(
            "RETRIEVAL_TIMEOUT_SECONDS",
            "retrieval_timeout_seconds",
        ),
    )
    # 站内检索最大重试次数（不含第一次请求）
    retrieval_max_retries: int = Field(
        default=1,
        ge=0,
        le=5,
        validation_alias=AliasChoices(
            "RETRIEVAL_MAX_RETRIES",
            "retrieval_max_retries",
        ),
    )
    # 中文注释：会话上下文检索词改写的最长时间；超时后直接使用原问题，不重试。
    retrieval_query_rewrite_timeout_seconds: float = Field(
        default=5,
        gt=0,
        le=30,
        validation_alias=AliasChoices(
            "RETRIEVAL_QUERY_REWRITE_TIMEOUT_SECONDS",
            "retrieval_query_rewrite_timeout_seconds",
        ),
    )
    # 管理员知识独立余弦相似度门槛；须用人工标注问题集校准，未配置时只放行标题、标签或正文短语命中。
    admin_knowledge_min_similarity: float | None = Field(
        default=None,
        ge=-1.0,
        le=1.0,
        validation_alias=AliasChoices(
            "ADMIN_KNOWLEDGE_MIN_SIMILARITY",
            "admin_knowledge_min_similarity",
        ),
    )
    # 管理员知识正文切块目标长度（字符）；修改后需增加文档 revision 并重新入库。
    knowledge_chunk_size: int = Field(
        default=1000,
        ge=256,
        le=8000,
        validation_alias=AliasChoices("KNOWLEDGE_CHUNK_SIZE", "knowledge_chunk_size"),
    )
    # 管理员知识相邻切块的重叠长度（字符）；用于减少段落边界信息损失。
    knowledge_chunk_overlap: int = Field(
        default=160,
        ge=0,
        le=2000,
        validation_alias=AliasChoices(
            "KNOWLEDGE_CHUNK_OVERLAP",
            "knowledge_chunk_overlap",
        ),
    )
    # 中文注释：单份管理员知识正文允许进入入库 API 的最大字符数；与 Java 上传配置保持一致。
    knowledge_max_content_chars: int = Field(
        default=1_000_000,
        ge=1_000,
        le=1_000_000,
        validation_alias=AliasChoices(
            "KNOWLEDGE_MAX_CONTENT_CHARS",
            "knowledge_max_content_chars",
        ),
    )
    # 中文注释：单文档同时调用 embedding 服务并保存检查点的批次数，限制供应商和数据库并发。
    knowledge_embedding_batch_concurrency: int = Field(
        default=2,
        ge=1,
        le=4,
        validation_alias=AliasChoices(
            "KNOWLEDGE_EMBEDDING_BATCH_CONCURRENCY",
            "knowledge_embedding_batch_concurrency",
        ),
    )
    # Python 搜索 Agent 整条请求总时限（秒）
    agent_total_timeout_seconds: float = Field(
        default=120,
        gt=0,
        le=600,
        validation_alias=AliasChoices(
            "AGENT_TOTAL_TIMEOUT_SECONDS",
            "agent_total_timeout_seconds",
        ),
    )
    max_output_tokens: int = Field(
        default=512,
        validation_alias=AliasChoices("MAX_OUTPUT_TOKENS", "max_output_tokens"),
    )

    # Firecrawl（仅供后台热梗采集，不接入用户问答 Agent）
    firecrawl_api_key: str = Field(
        default="",
        validation_alias=AliasChoices("FIRECRAWL_API_KEY", "firecrawl_api_key"),
    )
    firecrawl_enabled: bool = Field(
        default=True,
        validation_alias=AliasChoices("FIRECRAWL_ENABLED", "firecrawl_enabled"),
    )
    # 单次热梗采集的总时限（秒），覆盖发现来源和候选详情抓取。
    firecrawl_crawl_total_timeout_seconds: float = Field(
        default=150,
        gt=0,
        le=175,
        validation_alias=AliasChoices(
            "FIRECRAWL_CRAWL_TOTAL_TIMEOUT_SECONDS",
            "firecrawl_crawl_total_timeout_seconds",
        ),
    )
    firecrawl_default_queries: Annotated[list[str], NoDecode] = Field(
        default_factory=lambda: [
            "网络流行语",
            "网络梗",
        ],
        validation_alias=AliasChoices(
            "FIRECRAWL_DEFAULT_QUERIES",
            "firecrawl_default_queries",
        ),
    )
    # B 站投稿视频页（SPA，需 Firecrawl actions 交互滚动）
    firecrawl_bilibili_upload_url: str = Field(
        default="https://space.bilibili.com/94510621/upload/video",
        validation_alias=AliasChoices(
            "FIRECRAWL_BILIBILI_UPLOAD_URL",
            "firecrawl_bilibili_upload_url",
        ),
    )
    # 每次取投稿列表前 N 条视频总结梗
    firecrawl_bilibili_top_videos: int = Field(
        default=5,
        ge=1,
        le=20,
        validation_alias=AliasChoices(
            "FIRECRAWL_BILIBILI_TOP_VIDEOS",
            "firecrawl_bilibili_top_videos",
        ),
    )
    # scrape proxy: auto | basic | stealth（空则不传）
    firecrawl_scrape_proxy: str = Field(
        default="stealth",
        validation_alias=AliasChoices(
            "FIRECRAWL_SCRAPE_PROXY",
            "firecrawl_scrape_proxy",
        ),
    )
    # B 站发现源：空间主页兜底
    firecrawl_bilibili_dynamic_url: str = Field(
        default="https://space.bilibili.com/94510621",
        validation_alias=AliasChoices(
            "FIRECRAWL_BILIBILI_DYNAMIC_URL",
            "firecrawl_bilibili_dynamic_url",
        ),
    )
    # 额外抓取 URL，| 分隔；默认同账号动态页（有内容时补充）
    firecrawl_bilibili_extra_urls: Annotated[list[str], NoDecode] = Field(
        default_factory=lambda: [
            "https://space.bilibili.com/94510621/dynamic",
        ],
        validation_alias=AliasChoices(
            "FIRECRAWL_BILIBILI_EXTRA_URLS",
            "firecrawl_bilibili_extra_urls",
        ),
    )

    @model_validator(mode="after")
    def validate_vector_pool_bounds(self):
        """确保连接池最小连接数不超过最大连接数。"""
        if self.vector_pool_min_size > self.vector_pool_max_size:
            raise ValueError("VECTOR_POOL_MIN_SIZE must be <= VECTOR_POOL_MAX_SIZE")
        if self.knowledge_chunk_overlap >= self.knowledge_chunk_size:
            raise ValueError("KNOWLEDGE_CHUNK_OVERLAP must be < KNOWLEDGE_CHUNK_SIZE")
        return self

    @field_validator("firecrawl_default_queries", "firecrawl_bilibili_extra_urls", mode="before")
    @classmethod
    def parse_pipe_list(cls, value, info):
        field = getattr(info, "field_name", "") or ""
        defaults = {
            "firecrawl_default_queries": ["网络流行语", "网络梗"],
            "firecrawl_bilibili_extra_urls": [
                "https://space.bilibili.com/94510621/dynamic",
            ],
        }
        fallback = defaults.get(field, [])
        if value is None or value == "":
            return fallback
        if isinstance(value, str):
            parts = [p.strip() for p in value.split("|") if p.strip()]
            return parts or fallback
        if isinstance(value, list):
            return [str(x).strip() for x in value if str(x).strip()]
        return value

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
