# -*- coding: utf-8 -*-
"""回填已入库脏简介：根据 meme_resource 出处重新概括。仅本地运维脚本。"""
from __future__ import annotations

import asyncio
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.core.settings import get_settings
from app.crawl.firecrawl_client import firecrawl_scrape
from app.crawl.summarize import sanitize_source_text, summarize_meme_intro


ROWS = [
    {"id": 75, "name": "中国人能飞", "url": "https://www.bilibili.com/video/BV14x3u6iEvc"},
    {"id": 76, "name": "枪如人人如枪", "url": "https://www.bilibili.com/video/BV1RogD6aEvN"},
    {"id": 77, "name": "初音未来监护权杯", "url": "https://www.bilibili.com/video/BV1DiKr6nEcS"},
    {"id": 78, "name": "宗主第二招", "url": "https://www.bilibili.com/video/BV19oKc6yEpR"},
    {"id": 79, "name": "旱厕蜗牛", "url": "https://www.bilibili.com/video/BV1tHNi6aEsv"},
]


async def _source_text(settings, url: str) -> str:
    try:
        doc = await firecrawl_scrape(settings, url=url, wait_for_ms=2500)
    except Exception as e:
        return f"(scrape failed: {e})"
    md = doc.get("markdown") if isinstance(doc.get("markdown"), str) else ""
    if not md and isinstance(doc.get("content"), str):
        md = doc["content"]
    title = doc.get("title") if isinstance(doc.get("title"), str) else ""
    desc = doc.get("description") if isinstance(doc.get("description"), str) else ""
    return sanitize_source_text("\n".join(x for x in (title, desc, md) if x), 1400)


async def main() -> None:
    settings = get_settings()
    out = []
    for row in ROWS:
        text = await _source_text(settings, row["url"])
        intro = await summarize_meme_intro(
            settings,
            topic=row["name"],
            source_url=row["url"],
            source_title=row["name"],
            source_text=text,
        )
        item = {"id": row["id"], "name": row["name"], "introduction": intro}
        out.append(item)
        print(json.dumps(item, ensure_ascii=False))
    Path(__file__).with_name("backfill-intros.json").write_text(
        json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8"
    )


if __name__ == "__main__":
    asyncio.run(main())
