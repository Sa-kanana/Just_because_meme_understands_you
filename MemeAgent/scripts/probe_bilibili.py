# -*- coding: utf-8 -*-
import asyncio
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.core.settings import get_settings
from app.crawl.firecrawl_client import firecrawl_scrape, firecrawl_search


async def main():
    s = get_settings()
    chunks: list[str] = []

    for url in [
        "https://space.bilibili.com/94510621/upload/video",
        "https://space.bilibili.com/94510621",
    ]:
        try:
            doc = await firecrawl_scrape(s, url=url, wait_for_ms=5000)
            md = doc.get("markdown") or ""
            chunks.append(f"=== SCRAPE {url} len={len(md)} ===\n{md[:2500]}\n")
        except Exception as e:
            chunks.append(f"=== SCRAPE ERR {url} {type(e).__name__}: {e} ===\n")

    for q in [
        "梗指南 site:bilibili.com",
        "梗指南 是什么梗",
        "site:bilibili.com/video 网络梗 梗指南",
    ]:
        try:
            items = await firecrawl_search(s, query=q, limit=5, include_markdown=False)
            lines = []
            for it in items:
                title = it.get("title")
                url = it.get("url")
                lines.append(f"- {title} | {url}")
            chunks.append(f"=== SEARCH {q} n={len(items)} ===\n" + "\n".join(lines) + "\n")
        except Exception as e:
            chunks.append(f"=== SEARCH ERR {q} {type(e).__name__}: {e} ===\n")

    out = Path(__file__).with_name("bilibili-probe.txt")
    text = "\n".join(chunks)
    out.write_text(text, encoding="utf-8")
    print(text[:4500])
    print("wrote", out)


if __name__ == "__main__":
    asyncio.run(main())
