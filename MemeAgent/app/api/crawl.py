"""POST /crawl/hot-memes — Firecrawl 热梗采集（仅内网）。"""

from __future__ import annotations

from fastapi import APIRouter

from app.api.deps import WriteAuth
from app.crawl.service import crawl_hot_memes
from app.schemas.crawl import CrawlHotMemesRequest, CrawlHotMemesResponse

router = APIRouter(tags=["crawl"])


@router.post("/crawl/hot-memes", response_model=CrawlHotMemesResponse)
async def crawl_hot_memes_api(
    body: CrawlHotMemesRequest,
    _: WriteAuth,
) -> CrawlHotMemesResponse:
    return await crawl_hot_memes(body)
