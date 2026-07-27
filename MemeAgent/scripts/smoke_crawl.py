# -*- coding: utf-8 -*-
"""冒烟：B站发现路径（不查百科）。"""
import asyncio
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.core.settings import get_settings
from app.crawl.service import crawl_hot_memes, extract_topics_from_markdown
from app.schemas.crawl import CrawlHotMemesRequest


async def main():
    s = get_settings()
    out = Path(__file__).with_name("crawl-smoke.txt")
    lines = [
        f"key={bool(s.firecrawl_api_key)} enabled={s.firecrawl_enabled}",
        f"bilibili={s.firecrawl_bilibili_upload_url}",
    ]

    sample = '今天「电子榨菜」是什么梗？还有 #绝绝子# 又火了'
    extracted = extract_topics_from_markdown(sample, limit=5)
    lines.append(f"extract={extracted}")

    try:
        r = await crawl_hot_memes(
            CrawlHotMemesRequest(query="电子榨菜", limit=2, include_markdown=False),
            settings=s,
        )
        lines.append(f"topic_path n={len(r.candidates)} used={r.query_used}")
        for c in r.candidates:
            lines.append(f"- {c.title} | tags={c.tags} | {c.source_url[:100]}")
    except Exception as e:
        lines.append(f"ERR topic {type(e).__name__}: {e}")

    try:
        r2 = await crawl_hot_memes(
            CrawlHotMemesRequest(query=None, limit=2, include_markdown=False),
            settings=s,
        )
        lines.append(f"bilibili_path n={len(r2.candidates)} used={r2.query_used}")
        for c in r2.candidates:
            lines.append(f"- {c.title} | {c.source_url[:100]}")
    except Exception as e:
        lines.append(f"ERR bilibili {type(e).__name__}: {e}")

    out.write_text("\n".join(lines), encoding="utf-8")
    print("wrote", out)
    print("\n".join(lines[:20]))


if __name__ == "__main__":
    asyncio.run(main())
