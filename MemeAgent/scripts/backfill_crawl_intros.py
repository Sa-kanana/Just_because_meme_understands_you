# -*- coding: utf-8 -*-
"""回填已入库脏简介：根据 meme_resource 出处重新概括。仅本地运维脚本。"""
from __future__ import annotations

import asyncio
import json
import sys
from dataclasses import dataclass
from pathlib import Path

# 中文说明：项目根目录用于从任意工作目录导入 MemeAgent 应用模块。
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.core.settings import get_settings
from app.crawl.firecrawl_client import firecrawl_scrape
from app.crawl.summarize import sanitize_source_text, summarize_meme_intro
from script_paths import get_output_path


@dataclass(frozen=True)
class BackfillTarget:
    """需要重新生成简介的梗条目。"""

    meme_id: int  # 中文说明：业务梗 ID，用于识别待回填的条目。
    name: str  # 中文说明：梗名称，作为简介生成时的主题。
    source_url: str  # 中文说明：原始出处链接，用于抓取可信来源内容。


# 中文说明：本次回填的固定梗列表；新增条目时请同时提供 ID、名称和出处链接。
BACKFILL_TARGETS = (
    BackfillTarget(75, "中国人能飞", "https://www.bilibili.com/video/BV14x3u6iEvc"),
    BackfillTarget(76, "枪如人人如枪", "https://www.bilibili.com/video/BV1RogD6aEvN"),
    BackfillTarget(77, "初音未来监护权杯", "https://www.bilibili.com/video/BV1DiKr6nEcS"),
    BackfillTarget(78, "宗主第二招", "https://www.bilibili.com/video/BV19oKc6yEpR"),
    BackfillTarget(79, "旱厕蜗牛", "https://www.bilibili.com/video/BV1tHNi6aEsv"),
)
# 中文说明：单次 Firecrawl 抓取等待时间，单位为毫秒。
FIRECRAWL_WAIT_MS = 2500
# 中文说明：送入简介生成器的来源正文最大字符数。
MAX_SOURCE_CHARS = 1400


async def _source_text(settings, url: str) -> str:
    try:
        doc = await firecrawl_scrape(settings, url=url, wait_for_ms=FIRECRAWL_WAIT_MS)
    except Exception as e:
        return f"(scrape failed: {e})"
    md = doc.get("markdown") if isinstance(doc.get("markdown"), str) else ""
    if not md and isinstance(doc.get("content"), str):
        md = doc["content"]
    title = doc.get("title") if isinstance(doc.get("title"), str) else ""
    desc = doc.get("description") if isinstance(doc.get("description"), str) else ""
    return sanitize_source_text("\n".join(x for x in (title, desc, md) if x), MAX_SOURCE_CHARS)


async def main() -> None:
    """抓取指定出处并生成可人工复核的简介回填文件。"""
    settings = get_settings()
    out = []
    for target in BACKFILL_TARGETS:
        text = await _source_text(settings, target.source_url)
        intro = await summarize_meme_intro(
            settings,
            topic=target.name,
            source_url=target.source_url,
            source_title=target.name,
            source_text=text,
        )
        item = {
            "id": target.meme_id,  # 中文说明：待回填梗的业务 ID。
            "name": target.name,  # 中文说明：梗名称，供人工核对。
            "introduction": intro,  # 中文说明：根据可信出处重新生成的简介。
        }
        out.append(item)
        print(json.dumps(item, ensure_ascii=False))
    get_output_path("backfill-intros.json").write_text(
        json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8"
    )


if __name__ == "__main__":
    asyncio.run(main())
