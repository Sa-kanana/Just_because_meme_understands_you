# -*- coding: utf-8 -*-
"""冒烟：Firecrawl actions 交互抓投稿页前 5 条（不查百科）。"""
import asyncio
import sys
from pathlib import Path

# 中文说明：项目根目录用于从任意工作目录导入 MemeAgent 应用模块。
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.core.settings import get_settings
from app.crawl.firecrawl_client import firecrawl_scrape_with_interact
from app.crawl.service import crawl_hot_memes, extract_top_video_titles, topics_from_video_titles
from app.schemas.crawl import CrawlHotMemesRequest
from script_paths import get_output_path


# 中文说明：标题分析最多保留的主题数量。
MAX_TOPICS = 15
# 中文说明：本地交互抓取快照最多保留的字符数。
MAX_MARKDOWN_CHARS = 12000
# 中文说明：端到端采集最多返回的候选数量。
MAX_CRAWL_CANDIDATES = 5
# 中文说明：投稿页诊断固定检查前五个视频标题。
MAX_VIDEO_TITLES = 5
# 中文说明：未配置抓取代理时使用 Firecrawl 的自动代理模式。
DEFAULT_SCRAPE_PROXY = "auto"


async def main() -> None:
    """检查投稿页交互抓取和后续梗检索路径。"""
    s = get_settings()
    out = get_output_path("crawl-interact-smoke.txt")
    lines = [
        f"upload={s.firecrawl_bilibili_upload_url}",
        f"top_videos={s.firecrawl_bilibili_top_videos} proxy={s.firecrawl_scrape_proxy}",
    ]
    try:
        doc = await firecrawl_scrape_with_interact(
            s,
            url=s.firecrawl_bilibili_upload_url,
            proxy=s.firecrawl_scrape_proxy or DEFAULT_SCRAPE_PROXY,
        )
        md = doc.get("markdown") or doc.get("content") or ""
        lines.append(f"md_len={len(md)}")
        videos = extract_top_video_titles(md, limit=MAX_VIDEO_TITLES)
        lines.append(f"videos={len(videos)}")
        for title, url in videos:
            lines.append(f"  - {title} | {url}")
        topics = topics_from_video_titles(videos, topic_limit=MAX_TOPICS)
        lines.append(f"topics={topics}")
        get_output_path("bilibili-upload-interact.md").write_text(
            md[:MAX_MARKDOWN_CHARS], encoding="utf-8"
        )
    except Exception as e:
        lines.append(f"ERR interact {type(e).__name__}: {e}")

    try:
        r = await crawl_hot_memes(
            CrawlHotMemesRequest(query=None, limit=MAX_CRAWL_CANDIDATES, include_markdown=False),
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
