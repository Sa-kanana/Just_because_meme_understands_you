import hashlib
import logging
import math
import re

from langchain_openai import OpenAIEmbeddings

from app.core.settings import Settings, get_settings

logger = logging.getLogger(__name__)

_embeddings: OpenAIEmbeddings | None = None


def _get_embeddings(settings: Settings | None = None) -> OpenAIEmbeddings:
    global _embeddings
    cfg = settings or get_settings()
    if _embeddings is None:
        kwargs: dict = {
            "model": cfg.openai_embedding_model,
            "api_key": cfg.openai_api_key or "not-set",
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
    embeddings = _get_embeddings(cfg)
    return await embeddings.aembed_query(normalized)


def _deterministic_embedding(text: str, dim: int) -> list[float]:
    """无 API Key 时的本地 fallback，便于开发联调。"""
    digest = hashlib.sha256(text.encode("utf-8")).digest()
    values: list[float] = []
    for i in range(dim):
        b = digest[i % len(digest)]
        values.append((b / 127.5) - 1.0)
    norm = math.sqrt(sum(v * v for v in values)) or 1.0
    return [v / norm for v in values]
