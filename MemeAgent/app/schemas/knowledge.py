from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field, field_validator

from app.core.settings import get_settings


class KnowledgeDocument(BaseModel):
    """管理员知识文档（与梗向量分离，doc_id 和版本由 Java 传入）。"""

    # 中文注释：MySQL 管理员知识表中的雪花 ID 字符串。
    doc_id: str = Field(min_length=1, max_length=64)
    # 中文注释：知识文档标题，用于向量片段展示和精确标题辅助。
    title: str = Field(min_length=1, max_length=200)
    # 中文注释：提取后的原始正文，支持 Markdown 与 PDF 文本。
    content: str = Field(min_length=1, max_length=1_000_000)
    # 中文注释：文档分类元数据。
    category: str = Field(default="", max_length=64)
    # 中文注释：精确标签元数据，用于检索排序辅助。
    tags: list[str] = Field(default_factory=list, max_length=20)
    # 中文注释：已发布文档写入向量，删除文档写入墓碑。
    status: Literal["published", "deleted"] = "published"
    # 中文注释：最近一次业务修改时间。
    updated_at: datetime
    # 中文注释：兼容旧接口的内容哈希字段；新知识去重使用 content_sha256。
    content_hash: str = Field(min_length=8, max_length=128)
    # 中文注释：MySQL 传入的标准化正文 SHA-256；缺省时由 Python 按同一规则计算。
    content_sha256: str | None = Field(default=None, min_length=64, max_length=64)
    # 中文注释：单调递增的向量操作版本，过期版本不会覆盖较新状态。
    revision: int = Field(default=1, ge=1)

    @field_validator("content")
    @classmethod
    def validate_content_length(cls, value: str) -> str:
        """按运行配置拒绝过长正文，避免 Java 与 Python 接受范围不一致。"""
        limit = get_settings().knowledge_max_content_chars
        if len(value) > limit:
            raise ValueError(f"知识正文超过 {limit} 个字符")
        return value


class KnowledgeIngestRequest(BaseModel):
    documents: list[KnowledgeDocument] = Field(min_length=1, max_length=20)


class KnowledgeIngestAcceptedResponse(BaseModel):
    # 中文注释：兼容字段；返回前已完成 pgvector 事务并提交。
    accepted: bool = True
    # 中文注释：本次处理的文档任务数量。
    task_count: int
    # 中文注释：因重复版本或过期版本而安全跳过的数量。
    skipped: int = 0


class KnowledgeDeleteRequest(BaseModel):
    # 中文注释：待删除的管理员知识文档 ID 列表。
    doc_ids: list[str] = Field(min_length=1, max_length=100)
    # 中文注释：必须由 Java 文档权威来源提供，禁止 Python 单方面推进版本。
    revision: int = Field(ge=1)


class KnowledgeDeleteResponse(BaseModel):
    # 中文注释：本次实际移除的向量片段数量。
    deleted_count: int
