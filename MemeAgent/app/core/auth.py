import logging
import secrets

from fastapi import Header, HTTPException, status

from app.core.settings import get_settings


def configure_logging(level: str) -> None:
    logging.basicConfig(
        level=getattr(logging, level.upper(), logging.INFO),
        format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
    )


def _unauthorized(detail: str = "Invalid internal API key") -> None:
    raise HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail=detail,
    )


def _key_matches(provided: str | None, expected: str) -> bool:
    if not provided or not expected:
        return False
    try:
        return secrets.compare_digest(provided, expected)
    except (TypeError, ValueError):
        return False


def verify_stream_api_key(
    x_internal_api_key: str | None = Header(default=None, alias="X-Internal-Api-Key"),
) -> None:
    """只读能力：/stream（以及可选健康详情）。"""
    cfg = get_settings()
    if not _key_matches(x_internal_api_key, cfg.internal_api_key):
        _unauthorized("Invalid or missing X-Internal-Api-Key (stream)")


def verify_write_api_key(
    x_internal_api_key: str | None = Header(default=None, alias="X-Internal-Api-Key"),
) -> None:
    """
    写能力：/ingest、/crawl。
    若配置了 INTERNAL_API_KEY_WRITE，则必须使用写密钥；
    未单独配置时回退为与 stream 同一密钥（开发便利，生产建议分离）。
    """
    cfg = get_settings()
    write_key = (cfg.internal_api_key_write or "").strip()
    expected = write_key if write_key else cfg.internal_api_key
    if not _key_matches(x_internal_api_key, expected):
        _unauthorized("Invalid or missing X-Internal-Api-Key (write)")


# 兼容旧名
def verify_internal_api_key(
    x_internal_api_key: str | None = Header(default=None, alias="X-Internal-Api-Key"),
) -> None:
    verify_stream_api_key(x_internal_api_key)
