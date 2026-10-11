# -*- coding: utf-8 -*-
"""验证：B站采集 +（可选）打印候选，不写业务库。"""
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


# 中文说明：验证来源过滤时最多检查的候选数量。
CRAWL_CANDIDATE_LIMIT = 5
# 中文说明：结果文件中简介摘要的最大字符数。
MAX_INTRODUCTION_CHARS = 160


async def main() -> None:
    """检查端到端采集结果来源，并保存机器可读摘要。"""
    s = get_settings()
    out = get_output_path("verify-crawl-e2e.json")
    print("firecrawl_enabled=", s.firecrawl_enabled, "key=", bool(s.firecrawl_api_key))
    print("upload=", s.firecrawl_bilibili_upload_url)
    print("has_baike=", hasattr(s, "firecrawl_baike_site"))
    print("has_moegirl=", hasattr(s, "firecrawl_moegirl_site"))

    r = await crawl_hot_memes(
        CrawlHotMemesRequest(query=None, limit=CRAWL_CANDIDATE_LIMIT, include_markdown=False),
        settings=s,
    )
    payload = {
        "query_used": r.query_used,  # 中文说明：本次实际使用的搜索查询。
        "credits_hint": r.credits_hint,  # 中文说明：采集链路的额度或来源提示。
        "n": len(r.candidates),  # 中文说明：返回候选总数。
        "candidates": [
            {
                "title": c.title,  # 中文说明：候选梗标题。
                "source_url": c.source_url,  # 中文说明：候选内容的来源链接。
                "tags": c.tags,  # 中文说明：候选内容的标签列表。
                "intro": (c.introduction or "")[:MAX_INTRODUCTION_CHARS],  # 中文说明：简介截断摘要。
                "is_bilibili": "bilibili.com" in (c.source_url or ""),  # 中文说明：来源是否为 B 站。
                "is_baike": "baike.baidu.com" in (c.source_url or ""),  # 中文说明：来源是否为百度百科。
                "is_moegirl": "moegirl" in (c.source_url or ""),  # 中文说明：来源是否为萌娘百科。
            }
            for c in r.candidates
        ],
    }
    out.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    bad = [c for c in payload["candidates"] if c["is_baike"] or c["is_moegirl"]]
    if not payload["candidates"]:
        raise SystemExit("FAIL: no candidates")
    if bad:
        raise SystemExit("FAIL: encyclopedia urls still present")
    if not all(c["is_bilibili"] for c in payload["candidates"]):
        raise SystemExit("FAIL: non-bilibili urls")
    print("OK: bilibili-only candidates")


if __name__ == "__main__":
    asyncio.run(main())
