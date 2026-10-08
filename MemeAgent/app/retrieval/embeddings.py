import asyncio
import hashlib
import logging
import math
import re
import time
from collections import OrderedDict

from langchain_openai import OpenAIEmbeddings

from app.core.settings import Settings, get_settings

logger = logging.getLogger(__name__)

_embeddings: OpenAIEmbeddings | None = None
# 进程内缓存只存向量与哈希键，不保留原始查询文本。
_embedding_cache: OrderedDict[str, tuple[float, list[float]]] = OrderedDict()
# 相同查询并发到达时共用一次 embedding API 请求。
_embedding_inflight: dict[str, asyncio.Task[list[float]]] = {}


def _get_embeddings(settings: Settings | None = None) -> OpenAIEmbeddings:
    global _embeddings
    cfg = settings or get_settings()
    if _embeddings is None:
        kwargs: dict = {
            "model": cfg.openai_embedding_model,
            "api_key": cfg.openai_api_key or "not-set",
            # Application chunking keeps all inputs within the model limit. Keep raw
            # text input for compatible providers that reject token-id arrays.
            "check_embedding_ctx_length": False,
        }
        if cfg.openai_base_url:
            kwargs["base_url"] = cfg.openai_base_url
        _embeddings = OpenAIEmbeddings(**kwargs)
    return _embeddings


async def embed_text(text: str, settings: Settings | None = None) -> list[float]:
    cfg = settings or get_settings()
    normalized = re.sub(r"\s+", " ", text.strip())
    if not cfg.openai_api_key:
        return _deterministic_embedding(normalized, cfg.vector_embedding_dim)

    cache_key = _embedding_cache_key(normalized, cfg)
    cached = _get_cached_embedding(cache_key)
    if cached is not None:
        return cached

    task = _embedding_inflight.get(cache_key)
    if task is None:
        task = asyncio.create_task(_load_and_cache_embedding(cache_key, normalized, cfg))
        _embedding_inflight[cache_key] = task
        task.add_done_callback(
            lambda completed: _embedding_inflight.pop(cache_key, None)
            if _embedding_inflight.get(cache_key) is completed
            else None
        )
    # 取消某个客户端请求时，不取消其他相同查询正在等待的共享 embedding 请求。
    return list(await asyncio.shield(task))


def _embedding_cache_key(text: str, settings: Settings) -> str:
    """用配置命名空间和文本摘要构造缓存键，避免保存原始查询。"""
    material = "\0".join(
        (
            settings.openai_embedding_model,
            settings.openai_base_url or "",
            str(settings.vector_embedding_dim),
            text,
        )
    )
    return hashlib.sha256(material.encode("utf-8")).hexdigest()


def _get_cached_embedding(cache_key: str) -> list[float] | None:
    entry = _embedding_cache.get(cache_key)
    if entry is None:
        return None
    expires_at, vector = entry
    if expires_at <= time.monotonic():
        _embedding_cache.pop(cache_key, None)
        return None
    _embedding_cache.move_to_end(cache_key)
    return list(vector)


async def _load_and_cache_embedding(
    cache_key: str,
    normalized: str,
    settings: Settings,
) -> list[float]:
    try:
        vectors = await _get_embeddings(settings).aembed_documents([normalized])
        if len(vectors) != 1:
            raise ValueError("Embedding API returned an unexpected number of vectors")
        vector = list(vectors[0])
        if settings.embedding_cache_max_entries > 0 and settings.embedding_cache_ttl_seconds > 0:
            _embedding_cache[cache_key] = (
                time.monotonic() + settings.embedding_cache_ttl_seconds,
                vector,
            )
            _embedding_cache.move_to_end(cache_key)
            while len(_embedding_cache) > settings.embedding_cache_max_entries:
                _embedding_cache.popitem(last=False)
        return vector
    except Exception as exc:
        logger.exception("Embedding API request failed")
        raise RuntimeError(
            "Embedding API request failed; refusing an incompatible local fallback"
        ) from exc


async def embed_texts(texts: list[str], settings: Settings | None = None) -> list[list[float]]:
    """Embed a document's chunks in batches instead of issuing one request per chunk."""
    cfg = settings or get_settings()
    normalized = [re.sub(r"\s+", " ", text.strip()) for text in texts]
    if not normalized:
        return []
    if not cfg.openai_api_key:
        return [_deterministic_embedding(text, cfg.vector_embedding_dim) for text in normalized]
    try:
        embeddings = await _get_embeddings(cfg).aembed_documents(normalized)
        if len(embeddings) != len(normalized):
            raise ValueError("Embedding API returned an unexpected number of vectors")
        return embeddings
    except Exception as exc:
        logger.exception("Embedding API batch request failed")
        raise RuntimeError(
            "Embedding API request failed; refusing incompatible local fallback"
        ) from exc


def _deterministic_embedding(text: str, dim: int) -> list[float]:
    """无 API Key 时的本地 fallback，便于开发联调。"""
    digest = hashlib.sha256(text.encode("utf-8")).digest()
    values: list[float] = []
    for i in range(dim):
        b = digest[i % len(digest)]
        values.append((b / 127.5) - 1.0)
    norm = math.sqrt(sum(v * v for v in values)) or 1.0
    return [v / norm for v in values]
