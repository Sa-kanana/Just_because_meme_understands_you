# -*- coding: utf-8 -*-
"""冒烟：B站发现路径（不查百科）。"""
import asyncio
import sys
from pathlib import Path

# 中文说明：项目根目录用于从任意工作目录导入 MemeAgent 应用模块。
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.core.settings import get_settings
from app.crawl.service import crawl_hot_memes, extract_topics_from_markdown
from app.schemas.crawl import CrawlHotMemesRequest
from script_paths import get_output_path


# 中文说明：关键词抽取检查使用的示例正文。
SAMPLE_TEXT = '今天「电子榨菜」是什么梗？还有 #绝绝子# 又火了'
# 中文说明：关键词抽取最多返回的主题数量。
MAX_EXTRACTED_TOPICS = 5
# 中文说明：两种采集路径各自最多返回的候选数量。
MAX_CRAWL_CANDIDATES = 2
# 中文说明：关键词采集路径使用的固定查询词。
SMOKE_QUERY = "电子榨菜"


async def main() -> None:
    """检查关键词抽取和两条 B 站采集路径。"""
    s = get_settings()
    out = get_output_path("crawl-smoke.txt")
    lines = [
        f"key={bool(s.firecrawl_api_key)} enabled={s.firecrawl_enabled}",
        f"bilibili={s.firecrawl_bilibili_upload_url}",
    ]

    extracted = extract_topics_from_markdown(SAMPLE_TEXT, limit=MAX_EXTRACTED_TOPICS)
    lines.append(f"extract={extracted}")

    try:
        r = await crawl_hot_memes(
            CrawlHotMemesRequest(
                query=SMOKE_QUERY,
                limit=MAX_CRAWL_CANDIDATES,
                include_markdown=False,
            ),
            settings=s,
        )
        lines.append(f"topic_path n={len(r.candidates)} used={r.query_used}")
        for c in r.candidates:
            lines.append(f"- {c.title} | tags={c.tags} | {c.source_url[:100]}")
    except Exception as e:
        lines.append(f"ERR topic {type(e).__name__}: {e}")

    try:
        r2 = await crawl_hot_memes(
            CrawlHotMemesRequest(query=None, limit=MAX_CRAWL_CANDIDATES, include_markdown=False),
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
