# -*- coding: utf-8 -*-
"""验证：B站采集 +（可选）打印候选，不写业务库。"""
from __future__ import annotations

import asyncio
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.core.settings import get_settings
from app.crawl.service import crawl_hot_memes
from app.schemas.crawl import CrawlHotMemesRequest


async def main() -> None:
    s = get_settings()
    out = Path(__file__).with_name("verify-crawl-e2e.json")
    print("firecrawl_enabled=", s.firecrawl_enabled, "key=", bool(s.firecrawl_api_key))
    print("upload=", s.firecrawl_bilibili_upload_url)
    print("has_baike=", hasattr(s, "firecrawl_baike_site"))
    print("has_moegirl=", hasattr(s, "firecrawl_moegirl_site"))

    r = await crawl_hot_memes(
        CrawlHotMemesRequest(query=None, limit=5, include_markdown=False),
        settings=s,
    )
    payload = {
        "query_used": r.query_used,
        "credits_hint": r.credits_hint,
        "n": len(r.candidates),
        "candidates": [
            {
                "title": c.title,
                "source_url": c.source_url,
                "tags": c.tags,
                "intro": (c.introduction or "")[:160],
                "is_bilibili": "bilibili.com" in (c.source_url or ""),
                "is_baike": "baike.baidu.com" in (c.source_url or ""),
                "is_moegirl": "moegirl" in (c.source_url or ""),
            }
            for c in r.candidates
        ],
    }
    out.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    bad = [c for c in payload["candidates"] if c["is_baike"] or c["is_moegirl"]]
    if not payload["candidates"]:
        raise SystemExit("FAIL: no candidates")
    if bad:
        raise SystemExit("FAIL: encyclopedia urls still present")
    if not all(c["is_bilibili"] for c in payload["candidates"]):
        raise SystemExit("FAIL: non-bilibili urls")
    print("OK: bilibili-only candidates")


if __name__ == "__main__":
    asyncio.run(main())
