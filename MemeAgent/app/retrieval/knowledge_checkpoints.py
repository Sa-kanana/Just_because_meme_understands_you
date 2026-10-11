"""管理员知识 embedding 的 PostgreSQL 断点读写。"""

import hashlib
import json
import logging
import math
from urllib.parse import urlsplit

import asyncpg

from app.core.settings import Settings, get_settings
from app.retrieval.db import vector_literal

logger = logging.getLogger(__name__)


def build_embedding_fingerprint(settings: Settings | None = None) -> str:
    """按服务 endpoint、模型和维度生成指纹，不使用 API 密钥或 URL 凭据。"""
    cfg = settings or get_settings()
    configured_endpoint = cfg.openai_base_url or "https://api.openai.com/v1"
    parsed = urlsplit(configured_endpoint.strip())
    # 中文注释：仅保留服务地址身份，剔除可能携带令牌的用户信息、查询参数和片段。
    hostname = (parsed.hostname or "").lower()
    try:
        port = parsed.port
    except ValueError:
        port = None
    host = f"{hostname}:{port}" if port is not None else hostname
    endpoint = f"{parsed.scheme.lower()}://{host}{parsed.path.rstrip('/')}"
    material = {
        "endpoint": endpoint,
        "model": cfg.openai_embedding_model,
        "dimension": cfg.vector_embedding_dim,
    }
    encoded = json.dumps(material, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


async def load_knowledge_embedding_checkpoints(
    conn: asyncpg.Connection,
    doc_id: str,
    content_sha256: str,
    embedding_fingerprint: str,
) -> dict[int, tuple[str, list[float]]]:
    """读取匹配文档正文和 embedding 配置的片段检查点。"""
    rows = await conn.fetch(
        """
        SELECT chunk_index, chunk_sha256, embedding::text AS embedding_text
        FROM knowledge_embedding_checkpoint
        WHERE doc_id = $1 AND content_sha256 = $2 AND embedding_fingerprint = $3
        ORDER BY chunk_index
        """,
        doc_id,
        content_sha256,
        embedding_fingerprint,
    )
    checkpoints: dict[int, tuple[str, list[float]]] = {}
    for row in rows:
        try:
            embedding_text = row["embedding_text"]
            vector = [float(value) for value in embedding_text.strip("[]").split(",")]
            if not vector or any(not math.isfinite(value) for value in vector):
                raise ValueError("向量为空或包含非有限数值")
        except (TypeError, ValueError):
            # 中文注释：损坏或非有限的临时行不复用，后续同一序号会重新生成并覆盖。
            logger.warning(
                "Ignoring invalid knowledge embedding checkpoint doc_id=%s chunk_index=%s",
                doc_id,
                row["chunk_index"],
            )
            continue
        checkpoints[int(row["chunk_index"])] = (row["chunk_sha256"].strip(), vector)
    return checkpoints


async def store_knowledge_embedding_checkpoints(
    conn: asyncpg.Connection,
    doc_id: str,
    content_sha256: str,
    embedding_fingerprint: str,
    chunk_indices: list[int],
    chunk_hashes: list[str],
    embeddings: list[list[float]],
) -> None:
    """在调用方打开的短事务中幂等保存已验证的向量批次。"""
    if not (len(chunk_indices) == len(chunk_hashes) == len(embeddings)):
        raise ValueError("检查点序号、哈希和向量数量必须一致")
    if not chunk_indices:
        return
    await conn.executemany(
        """
        INSERT INTO knowledge_embedding_checkpoint
            (doc_id, content_sha256, embedding_fingerprint, chunk_index,
             chunk_sha256, embedding, created_at, updated_at)
        VALUES ($1, $2, $3, $4, $5, $6::vector, NOW(), NOW())
        ON CONFLICT (doc_id, content_sha256, embedding_fingerprint, chunk_index)
        DO UPDATE SET chunk_sha256 = EXCLUDED.chunk_sha256,
                      embedding = EXCLUDED.embedding,
                      updated_at = NOW()
        """,
        [
            (
                doc_id,
                content_sha256,
                embedding_fingerprint,
                chunk_index,
                chunk_hash,
                vector_literal(embedding),
            )
            for chunk_index, chunk_hash, embedding in zip(
                chunk_indices, chunk_hashes, embeddings, strict=True
            )
        ],
    )
