"""Firecrawl SDK 封装（同步 API 放到线程池，避免阻塞事件循环）。"""

from __future__ import annotations

import asyncio
import logging
from typing import Any

from app.core.settings import Settings

logger = logging.getLogger(__name__)

# B 站投稿页 SPA：等待 + 多次下滚再抓取（反爬较强时需 stealth）
BILIBILI_UPLOAD_INTERACT_ACTIONS: list[dict[str, Any]] = [
    {"type": "wait", "milliseconds": 5000},
    {"type": "scroll", "direction": "down"},
    {"type": "wait", "milliseconds": 2500},
    {"type": "scroll", "direction": "down"},
    {"type": "wait", "milliseconds": 2500},
    {"type": "scroll", "direction": "down"},
    {"type": "wait", "milliseconds": 3000},
    {"type": "scroll", "direction": "down"},
    {"type": "wait", "milliseconds": 2000},
]


def _to_dict(raw: Any) -> Any:
    if raw is None:
        return None
    if hasattr(raw, "model_dump"):
        return raw.model_dump()
    if hasattr(raw, "dict"):
        try:
            return raw.dict()  # type: ignore[call-arg]
        except Exception:
            pass
    return raw


def _extract_web_items(raw: Any) -> list[dict[str, Any]]:
    """兼容 Firecrawl search 多种返回形态。"""
    data = _to_dict(raw)
    if data is None:
        return []
    if not isinstance(data, dict):
        if isinstance(data, list):
            return [x for x in data if isinstance(x, dict)]
        return []

    inner = data.get("data", data)
    if isinstance(inner, dict):
        web = inner.get("web") or inner.get("news") or []
        if isinstance(web, list):
            return [x for x in web if isinstance(x, dict)]
    if isinstance(inner, list):
        return [x for x in inner if isinstance(x, dict)]
    return []


def _extract_scrape_doc(raw: Any) -> dict[str, Any]:
    """兼容 scrape / scrape_url 返回。"""
    data = _to_dict(raw)
    if data is None:
        return {}
    if isinstance(data, dict):
        inner = data.get("data")
        if isinstance(inner, dict):
            return inner
        return data
    return {}


def _build_client(api_key: str | None):
    try:
        from firecrawl import Firecrawl  # type: ignore

        kwargs: dict[str, Any] = {}
        if api_key:
            kwargs["api_key"] = api_key
        return Firecrawl(**kwargs), "v2"
    except ImportError:
        pass

    from firecrawl import FirecrawlApp

    kwargs = {}
    if api_key:
        kwargs["api_key"] = api_key
    return FirecrawlApp(**kwargs), "v1"


def _sync_search(
    *,
    api_key: str | None,
    query: str,
    limit: int,
    include_markdown: bool,
) -> list[dict[str, Any]]:
    try:
        client, version = _build_client(api_key)
    except ImportError as exc:  # pragma: no cover
        raise RuntimeError(
            "未安装 firecrawl SDK，请在 conda memeagent 中执行: pip install firecrawl-py"
        ) from exc

    if version == "v2":
        scrape_options = {"formats": ["markdown"]} if include_markdown else None
        call_kwargs: dict[str, Any] = {"limit": limit}
        if scrape_options:
            call_kwargs["scrape_options"] = scrape_options
        try:
            raw = client.search(query, sources=["web", "news"], **call_kwargs)
        except TypeError:
            raw = client.search(query, **call_kwargs)
        return _extract_web_items(raw)

    call_kwargs: dict[str, Any] = {"limit": limit}
    if include_markdown:
        try:
            from firecrawl import ScrapeOptions

            call_kwargs["scrape_options"] = ScrapeOptions(formats=["markdown"])
        except Exception:
            call_kwargs["scrape_options"] = {"formats": ["markdown"]}
    raw = client.search(query, **call_kwargs)
    return _extract_web_items(raw)


def _sync_scrape(
    *,
    api_key: str | None,
    url: str,
    wait_for_ms: int = 3000,
    actions: list[dict[str, Any]] | None = None,
    proxy: str | None = None,
    only_main_content: bool = True,
    timeout_ms: int = 90000,
) -> dict[str, Any]:
    try:
        client, version = _build_client(api_key)
    except ImportError as exc:  # pragma: no cover
        raise RuntimeError(
            "未安装 firecrawl SDK，请在 conda memeagent 中执行: pip install firecrawl-py"
        ) from exc

    call_kwargs: dict[str, Any] = {
        "formats": ["markdown", "links"],
        "only_main_content": only_main_content,
        "wait_for": wait_for_ms,
        "timeout": timeout_ms,
    }
    if actions:
        call_kwargs["actions"] = actions
    if proxy:
        call_kwargs["proxy"] = proxy

    if version == "v2":
        try:
            raw = client.scrape(url, **call_kwargs)
        except TypeError:
            for drop in (("timeout",), ("proxy",), ("actions", "proxy", "timeout")):
                slim = {k: v for k, v in call_kwargs.items() if k not in drop}
                try:
                    raw = client.scrape(url, **slim)
                    break
                except TypeError:
                    continue
            else:
                raw = client.scrape(url, formats=["markdown"])
        return _extract_scrape_doc(raw)

    try:
        raw = client.scrape_url(url, **call_kwargs)
    except (TypeError, ValueError):
        try:
            slim2: dict[str, Any] = {
                "formats": ["markdown", "links"],
                "wait_for": wait_for_ms,
            }
            if actions:
                slim2["actions"] = actions
            if proxy:
                slim2["proxy"] = proxy
            raw = client.scrape_url(url, **slim2)
        except (TypeError, ValueError):
            raw = client.scrape_url(url, formats=["markdown"])
    return _extract_scrape_doc(raw)


def _sync_interact(
    *,
    api_key: str | None,
    scrape_id: str,
    prompt: str,
) -> dict[str, Any]:
    """Firecrawl interact（自然语言页面操作），部分账号/版本可用。"""
    try:
        client, _version = _build_client(api_key)
    except ImportError as exc:  # pragma: no cover
        raise RuntimeError(
            "未安装 firecrawl SDK，请在 conda memeagent 中执行: pip install firecrawl-py"
        ) from exc

    if not hasattr(client, "interact"):
        raise RuntimeError("当前 firecrawl SDK 不支持 interact()")

    try:
        raw = client.interact(scrape_id, {"prompt": prompt})
    except TypeError:
        raw = client.interact(scrape_id, prompt=prompt)
    data = _to_dict(raw)
    return data if isinstance(data, dict) else {"raw": data}


async def firecrawl_search(
    settings: Settings,
    *,
    query: str,
    limit: int = 5,
    include_markdown: bool = True,
) -> list[dict[str, Any]]:
    if not settings.firecrawl_enabled:
        logger.info("Firecrawl disabled, skip search query=%s", query[:80])
        return []
    return await asyncio.to_thread(
        _sync_search,
        api_key=settings.firecrawl_api_key or None,
        query=query,
        limit=limit,
        include_markdown=include_markdown,
    )


async def firecrawl_scrape(
    settings: Settings,
    *,
    url: str,
    wait_for_ms: int = 3000,
    actions: list[dict[str, Any]] | None = None,
    proxy: str | None = None,
    only_main_content: bool = True,
    timeout_ms: int = 90000,
) -> dict[str, Any]:
    if not settings.firecrawl_enabled:
        logger.info("Firecrawl disabled, skip scrape url=%s", url[:120])
        return {}
    return await asyncio.to_thread(
        _sync_scrape,
        api_key=settings.firecrawl_api_key or None,
        url=url,
        wait_for_ms=wait_for_ms,
        actions=actions,
        proxy=proxy,
        only_main_content=only_main_content,
        timeout_ms=timeout_ms,
    )


async def firecrawl_scrape_with_interact(
    settings: Settings,
    *,
    url: str,
    actions: list[dict[str, Any]] | None = None,
    proxy: str | None = "auto",
    wait_for_ms: int = 5000,
) -> dict[str, Any]:
    """带浏览器 actions 的抓取（滚动/等待等），用于 SPA 投稿列表。"""
    acts = actions if actions is not None else list(BILIBILI_UPLOAD_INTERACT_ACTIONS)
    logger.info(
        "Firecrawl interact-scrape url=%s actions=%s proxy=%s",
        url[:120],
        len(acts),
        proxy,
    )
    return await firecrawl_scrape(
        settings,
        url=url,
        wait_for_ms=wait_for_ms,
        actions=acts,
        proxy=proxy,
        only_main_content=False,
        timeout_ms=120000,
    )


async def firecrawl_interact(
    settings: Settings,
    *,
    scrape_id: str,
    prompt: str,
) -> dict[str, Any]:
    if not settings.firecrawl_enabled:
        return {}
    return await asyncio.to_thread(
        _sync_interact,
        api_key=settings.firecrawl_api_key or None,
        scrape_id=scrape_id,
        prompt=prompt,
    )
