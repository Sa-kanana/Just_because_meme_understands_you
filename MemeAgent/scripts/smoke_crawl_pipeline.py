# -*- coding: utf-8 -*-
"""端到端冒烟：B站理解 → Firecrawl 细节/图片 → 打印候选结构。"""
from __future__ import annotations

import asyncio
import json
import sys
from pathlib import Path

# 中文说明：项目根目录用于从任意工作目录导入 MemeAgent 应用模块。
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.core.settings import get_settings
from app.crawl.service import crawl_hot_memes
from app.schemas.crawl import CrawlHotMemesRequest
from script_paths import get_output_path


# 中文说明：完整采集链路只请求一条候选，控制调用耗时和外部服务用量。
CRAWL_CANDIDATE_LIMIT = 1
# 中文说明：简介至少需要的字符数，用于过滤不完整摘要。
MIN_INTRODUCTION_LENGTH = 12


async def main() -> None:
    """检查从热榜抓取到候选结果整理的完整采集链路。"""
    settings = get_settings()
    req = CrawlHotMemesRequest(query=None, limit=CRAWL_CANDIDATE_LIMIT, include_markdown=True)
    print(f"start crawl limit={CRAWL_CANDIDATE_LIMIT} ...", flush=True)
    resp = await crawl_hot_memes(req, settings=settings)
    payload = {
        "query_used": resp.query_used,  # 中文说明：本次实际使用的搜索查询。
        "credits_hint": resp.credits_hint,  # 中文说明：采集链路的额度或来源提示。
        "n": len(resp.candidates),  # 中文说明：返回候选总数。
        "candidates": [
            {
                "title": c.title,  # 中文说明：候选梗标题。
                "introduction": c.introduction,  # 中文说明：候选梗简介。
                "source_url": c.source_url,  # 中文说明：候选内容的主要来源。
                "image_url": c.image_url,  # 中文说明：候选内容的封面图片。
                "detail_url": c.detail_url,  # 中文说明：候选内容的详情页面。
                "tags": c.tags,  # 中文说明：候选内容的标签列表。
                "has_noise": (
                    "\\" in (c.introduction or "") or "【" in (c.introduction or "")
                ),  # 中文说明：简介是否含抓取噪声。
                "is_bilibili": "bilibili.com" in (c.source_url or ""),  # 中文说明：来源是否为 B 站。
            }
            for c in resp.candidates
        ],
    }
    out = get_output_path("crawl-pipeline-smoke.json")
    out.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(payload, ensure_ascii=False, indent=2), flush=True)
    if not payload["candidates"]:
        raise SystemExit("FAIL: no candidates")
    c0 = payload["candidates"][0]
    if c0["has_noise"]:
        raise SystemExit("FAIL: intro still noisy")
    if not c0["is_bilibili"]:
        raise SystemExit("FAIL: source not bilibili")
    if not c0["introduction"] or len(c0["introduction"]) < MIN_INTRODUCTION_LENGTH:
        raise SystemExit("FAIL: intro too short")
    print("OK pipeline smoke", flush=True)


if __name__ == "__main__":
    asyncio.run(main())
