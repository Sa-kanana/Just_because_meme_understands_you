# -*- coding: utf-8 -*-
"""端到端冒烟：B站理解 → Firecrawl 细节/图片 → 打印候选结构。"""
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
    settings = get_settings()
    # limit=1：完整跑通一条，避免时间过长
    req = CrawlHotMemesRequest(query=None, limit=1, include_markdown=True)
    print("start crawl limit=1 ...", flush=True)
    resp = await crawl_hot_memes(req, settings=settings)
    payload = {
        "query_used": resp.query_used,
        "credits_hint": resp.credits_hint,
        "n": len(resp.candidates),
        "candidates": [
            {
                "title": c.title,
                "introduction": c.introduction,
                "source_url": c.source_url,
                "image_url": c.image_url,
                "detail_url": c.detail_url,
                "tags": c.tags,
                "has_noise": ("\\" in (c.introduction or "")) or ("【" in (c.introduction or "")),
                "is_bilibili": "bilibili.com" in (c.source_url or ""),
            }
            for c in resp.candidates
        ],
    }
    out = Path(__file__).with_name("crawl-pipeline-smoke.json")
    out.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(payload, ensure_ascii=False, indent=2), flush=True)
    if not payload["candidates"]:
        raise SystemExit("FAIL: no candidates")
    c0 = payload["candidates"][0]
    if c0["has_noise"]:
        raise SystemExit("FAIL: intro still noisy")
    if not c0["is_bilibili"]:
        raise SystemExit("FAIL: source not bilibili")
    if not c0["introduction"] or len(c0["introduction"]) < 12:
        raise SystemExit("FAIL: intro too short")
    print("OK pipeline smoke", flush=True)


if __name__ == "__main__":
    asyncio.run(main())
