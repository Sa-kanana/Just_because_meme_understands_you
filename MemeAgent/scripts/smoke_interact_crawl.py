# -*- coding: utf-8 -*-
"""冒烟：Firecrawl actions 交互抓投稿页前 5 条（不查百科）。"""
import asyncio
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.core.settings import get_settings
from app.crawl.firecrawl_client import firecrawl_scrape_with_interact
from app.crawl.service import crawl_hot_memes, extract_top_video_titles, topics_from_video_titles
from app.schemas.crawl import CrawlHotMemesRequest


async def main():
    s = get_settings()
    out = Path(__file__).with_name("crawl-interact-smoke.txt")
    lines = [
        f"upload={s.firecrawl_bilibili_upload_url}",
        f"top_videos={s.firecrawl_bilibili_top_videos} proxy={s.firecrawl_scrape_proxy}",
    ]
    try:
        doc = await firecrawl_scrape_with_interact(
            s, url=s.firecrawl_bilibili_upload_url, proxy=s.firecrawl_scrape_proxy or "auto"
        )
        md = doc.get("markdown") or doc.get("content") or ""
        lines.append(f"md_len={len(md)}")
        videos = extract_top_video_titles(md, limit=5)
        lines.append(f"videos={len(videos)}")
        for title, url in videos:
            lines.append(f"  - {title} | {url}")
        topics = topics_from_video_titles(videos, topic_limit=15)
        lines.append(f"topics={topics}")
        Path(__file__).with_name("bilibili-upload-interact.md").write_text(md[:12000], encoding="utf-8")
    except Exception as e:
        lines.append(f"ERR interact {type(e).__name__}: {e}")

    try:
        r = await crawl_hot_memes(
            CrawlHotMemesRequest(query=None, limit=5, include_markdown=False),
            settings=s,
        )
        lines.append(f"crawl n={len(r.candidates)} used={r.query_used}")
        for c in r.candidates:
            lines.append(f"- {c.title} | {c.source_url[:100]}")
    except Exception as e:
        lines.append(f"ERR crawl {type(e).__name__}: {e}")

    out.write_text("\n".join(lines), encoding="utf-8")
    print("\n".join(lines[:40]))
    print("wrote", out)


if __name__ == "__main__":
    asyncio.run(main())
