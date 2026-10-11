# -*- coding: utf-8 -*-
"""探测 Firecrawl 对 B 站空间页和站内搜索的响应。"""

import asyncio
import sys
from pathlib import Path

# 中文说明：项目根目录用于从任意工作目录导入 MemeAgent 应用模块。
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.core.settings import get_settings
from app.crawl.firecrawl_client import firecrawl_scrape, firecrawl_search
from script_paths import get_output_path


# 中文说明：单次页面抓取等待时间，单位为毫秒。
FIRECRAWL_WAIT_MS = 5000
# 中文说明：诊断输出保留的 Markdown 字符数上限。
MAX_MARKDOWN_CHARS = 2500
# 中文说明：站内搜索每个查询最多返回的结果数量。
MAX_SEARCH_RESULTS = 5
# 中文说明：控制台最多打印的诊断字符数，完整结果仍写入 output 文件。
MAX_CONSOLE_CHARS = 4500


async def main() -> None:
    """探测 Firecrawl 对 B 站空间页和站内搜索的响应。"""
    s = get_settings()
    chunks: list[str] = []

    # 中文说明：探测投稿列表与个人空间页，帮助排查 Firecrawl 页面抓取问题。
    probe_urls = (
        "https://space.bilibili.com/94510621/upload/video",
        "https://space.bilibili.com/94510621",
    )
    for url in probe_urls:
        try:
            doc = await firecrawl_scrape(s, url=url, wait_for_ms=FIRECRAWL_WAIT_MS)
            md = doc.get("markdown") or ""
            chunks.append(f"=== SCRAPE {url} len={len(md)} ===\n{md[:MAX_MARKDOWN_CHARS]}\n")
        except Exception as e:
            chunks.append(f"=== SCRAPE ERR {url} {type(e).__name__}: {e} ===\n")

    # 中文说明：固定查询用于检查 Firecrawl 的搜索结果和来源 URL。
    probe_queries = (
        "梗指南 site:bilibili.com",
        "梗指南 是什么梗",
        "site:bilibili.com/video 网络梗 梗指南",
    )
    for q in probe_queries:
        try:
            items = await firecrawl_search(
                s,
                query=q,
                limit=MAX_SEARCH_RESULTS,
                include_markdown=False,
            )
            lines = []
            for it in items:
                title = it.get("title")
                url = it.get("url")
                lines.append(f"- {title} | {url}")
            search_results = "\n".join(lines)
            chunks.append(f"=== SEARCH {q} n={len(items)} ===\n{search_results}\n")
        except Exception as e:
            chunks.append(f"=== SEARCH ERR {q} {type(e).__name__}: {e} ===\n")

    out = get_output_path("bilibili-probe.txt")
    text = "\n".join(chunks)
    out.write_text(text, encoding="utf-8")
    print(text[:MAX_CONSOLE_CHARS])
    print("wrote", out)


if __name__ == "__main__":
    asyncio.run(main())
