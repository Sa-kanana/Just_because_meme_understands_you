"""B 站 UP 视频理解 → Firecrawl 搜细节/图片 → 候选供 Java 入库。"""

from __future__ import annotations

import hashlib
import logging
import re
from urllib.parse import quote, urlparse

from app.core.settings import Settings, get_settings
from app.crawl.firecrawl_client import (
    firecrawl_scrape,
    firecrawl_scrape_with_interact,
    firecrawl_search,
)
from app.crawl.summarize import (
    fallback_intro,
    finalize_meme_intro,
    sanitize_source_text,
    summarize_from_bilibili,
)
from app.schemas.crawl import CrawlHotMemesRequest, CrawlHotMemesResponse, CrawlMemeCandidate

logger = logging.getLogger(__name__)

_TAG_STOP = {"http", "https", "www", "com", "cn", "html", "bilibili"}

_NOISE_TOPICS = {
    "动态",
    "转发",
    "点赞",
    "评论",
    "关注",
    "投稿",
    "哔哩哔哩",
    "bilibili",
    "首页",
    "热门",
    "推荐",
    "验证码",
    "登录",
    "注册",
    "网络流行语",
    "是什么梗",
    "什么梗",
    "什么意思",
    "是什么意思",
}


def _clean_text(value: str | None, max_len: int) -> str:
    text = re.sub(r"\s+", " ", (value or "").strip())
    if len(text) <= max_len:
        return text
    return text[: max_len - 1].rstrip() + "…"


def _guess_tags(title: str, url: str, extra: list[str] | None = None) -> list[str]:
    tags = ["实时", "热搜"]
    if extra:
        for t in extra:
            t = (t or "").strip()
            if t and t not in tags:
                tags.append(t[:20])
    host = urlparse(url).netloc.lower().removeprefix("www.")
    if host and host.split(".")[0] not in _TAG_STOP:
        tags.append(host.split(".")[0][:20])
    for token in re.findall(r"[\u4e00-\u9fff]{2,8}|[A-Za-z]{3,12}", title):
        if token.lower() in _TAG_STOP:
            continue
        if token not in tags:
            tags.append(token)
        if len(tags) >= 6:
            break
    return tags[:6]


def _intro_from_item(item: dict) -> str:
    for key in ("description", "snippet", "summary", "markdown"):
        raw = item.get(key)
        if isinstance(raw, str) and raw.strip():
            text = raw.strip()
            if key == "markdown":
                text = re.sub(r"[#>*`]", "", text)
                text = re.sub(r"\s+", " ", text)
            return _clean_text(text, 1200)
    return ""


def _title_from_item(item: dict) -> str:
    for key in ("title", "name"):
        raw = item.get(key)
        if isinstance(raw, str) and raw.strip():
            return _clean_text(raw, 120)
    url = str(item.get("url") or item.get("sourceURL") or "").strip()
    if url:
        path = urlparse(url).path.strip("/") or url
        return _clean_text(path.replace("-", " ").replace("_", " "), 80) or "未命名热梗"
    return "未命名热梗"


def _url_from_item(item: dict) -> str:
    for key in ("url", "sourceURL", "link"):
        raw = item.get(key)
        if isinstance(raw, str) and raw.strip().startswith("http"):
            return raw.strip()[:1000]
    return ""


_UI_NOISE_RE = re.compile(
    r"(关注数|粉丝数|投稿|合集和系列|充电|登录|注册|稍后再看|个人资料|直播间|"
    r"Cross Origin|正在玩命|什么都没有|空间主人|立即上传|代表作|播放全部)"
)


def _normalize_topic(raw: str) -> str | None:
    topic = re.sub(r"\s+", "", (raw or "").strip(" \t\r\n·.-_#「」『』\"“”'【】"))
    topic = re.sub(r"^(关于|求助|请问|这个|那个|今天|最新|热门|又)", "", topic)
    topic = re.sub(r"(【梗指南】|【伪梗指南】)$", "", topic)
    if len(topic) < 2 or len(topic) > 24:
        return None
    if topic.lower() in _NOISE_TOPICS or topic in _NOISE_TOPICS:
        return None
    if _UI_NOISE_RE.search(topic):
        return None
    if re.fullmatch(r"\d+", topic):
        return None
    # 纯导航/统计
    if re.search(r"\d+[万+]|\d+\.\d+万", topic):
        return None
    return topic


def _split_title_topics(title: str) -> list[str]:
    """从「A、B、C是什么梗【梗指南】」拆出多个梗词。"""
    text = (title or "").strip()
    text = re.sub(r"【[^】]*指南】", "", text)
    text = re.sub(r"[（(][^）)]*[)）]", "", text)
    m = re.search(r"^(.+?)是什么梗", text)
    if m:
        text = m.group(1)
    parts = re.split(r"[、，,/｜|]\s*", text)
    out: list[str] = []
    for p in parts:
        t = _normalize_topic(p)
        if t:
            out.append(t)
    return out


def extract_top_video_titles(markdown: str, *, limit: int = 5) -> list[tuple[str, str]]:
    """从投稿列表 markdown 提取前 N 条视频 (title, url)，按出现顺序。"""
    text = markdown or ""
    text = re.sub(r"\[!\[([^\]]*)\]\([^)]+\)\]\(([^)]+)\)", r"[\1](\2)", text)
    text = re.sub(r"!\[([^\]]*)\]\([^)]+\)", r"\1", text)

    seen: set[str] = set()
    out: list[tuple[str, str]] = []
    for m in re.finditer(
        r"\[([^\]]{2,100})\]\((https://www\.bilibili\.com/video/BV[\w]+)[^)]*\)",
        text,
    ):
        title = m.group(1).strip().lstrip("!")
        url = m.group(2).strip()
        if not title or url in seen:
            continue
        # 过滤导航噪音
        if any(x in title for x in ("首页", "动态", "投稿", "合集和系列", "稍后再看")):
            continue
        if not ("是什么梗" in title or "梗指南" in title or "热梗" in title or "梗" in title):
            # 投稿页其它视频也收，但优先梗向标题；无梗字则跳过纯导航
            if len(title) < 4:
                continue
        seen.add(url)
        out.append((title, url))
        if len(out) >= limit:
            break
    return out


def topics_from_video_titles(videos: list[tuple[str, str]], *, topic_limit: int = 20) -> list[str]:
    """从前 N 条视频标题总结梗词（一条视频可拆多个梗）。"""
    seen: set[str] = set()
    topics: list[str] = []
    for title, _url in videos:
        parts = _split_title_topics(title)
        if not parts:
            t = _normalize_topic(re.sub(r"【[^】]*】", "", title))
            parts = [t] if t else []
        for t in parts:
            key = t.lower()
            if key in seen:
                continue
            seen.add(key)
            topics.append(t)
            if len(topics) >= topic_limit:
                return topics
    return topics


def extract_topics_from_markdown(markdown: str, *, limit: int = 20) -> list[str]:
    """兼容旧逻辑：先取视频标题再拆梗；否则启发式抽取。"""
    videos = extract_top_video_titles(markdown, limit=max(limit, 5))
    if videos:
        return topics_from_video_titles(videos, topic_limit=limit)

    text = markdown or ""
    text = re.sub(r"\[!\[([^\]]*)\]\([^)]+\)\]\(([^)]+)\)", r"[\1](\2)", text)
    text = re.sub(r"!\[([^\]]*)\]\([^)]+\)", r"\1", text)

    seen: set[str] = set()
    topics: list[str] = []

    def _push(candidate: str | None) -> None:
        topic = _normalize_topic(candidate or "")
        if not topic:
            return
        key = topic.lower()
        if key in seen:
            return
        seen.add(key)
        topics.append(topic)

    def _push_title(title: str) -> None:
        for t in _split_title_topics(title):
            _push(t)
            if len(topics) >= limit:
                return

    for m in re.finditer(r"\[([^\]]{2,80})\]\([^)]+\)", text):
        label = m.group(1).strip().lstrip("!")
        if "是什么梗" in label or "梗指南" in label or "热梗" in label:
            _push_title(label)
        if len(topics) >= limit:
            return topics

    for m in re.finditer(r"([^\n\[\]]{2,60}是什么梗[^\n]{0,20})", text):
        _push_title(m.group(1))
        if len(topics) >= limit:
            return topics

    for pat in (
        r"[「『\"“]([^」』\"”]{2,24})[」』\"”]",
        r"#([\u4e00-\u9fffA-Za-z0-9_]{2,20})#",
        r"#([\u4e00-\u9fffA-Za-z0-9_]{2,20})(?!\w)",
        r"([\u4e00-\u9fffA-Za-z0-9]{2,16}(?:梗|流行语))",
    ):
        for match in re.finditer(pat, text):
            _push(match.group(1))
            if len(topics) >= limit:
                return topics

    return topics[:limit]


def item_to_candidate(item: dict, *, score: float, tags_extra: list[str] | None = None) -> CrawlMemeCandidate | None:
    url = _url_from_item(item)
    if not url:
        return None
    title = _title_from_item(item)
    intro = _intro_from_item(item)
    if not intro:
        intro = f"「{title}」来自网络热搜采集，详情见原文链接。"
    image = None
    for key in ("imageUrl", "image", "ogImage"):
        raw = item.get(key)
        if isinstance(raw, str) and raw.strip().startswith("http"):
            image = raw.strip()[:1000]
            break
    return CrawlMemeCandidate(
        title=title,
        introduction=intro,
        source_url=url,
        image_url=image,
        tags=_guess_tags(title, url, tags_extra),
        score=score,
    )


def _bilibili_search_url(topic: str) -> str:
    return f"https://search.bilibili.com/all?keyword={quote(topic)}"


def _is_empty_bilibili_video_list(markdown: str) -> bool:
    text = markdown or ""
    if "还没投过视频" in text or "什么都没有" in text or "好像没有东西" in text:
        return True
    return len(extract_top_video_titles(text, limit=1)) == 0


async def _discover_topics_from_bilibili(
    settings: Settings,
    *,
    limit: int,
) -> tuple[list[str], list[str], dict[str, str], dict[str, str]]:
    """投稿页交互抓取前 N 条视频 → 总结梗词。

    返回 (topics, query_used, topic_sources, topic_titles)。
    """
    video_limit = max(1, min(limit, settings.firecrawl_bilibili_top_videos))
    upload_url = (settings.firecrawl_bilibili_upload_url or "").strip()
    query_used: list[str] = []
    topics: list[str] = []
    seen: set[str] = set()
    video_titles: list[str] = []
    topic_sources: dict[str, str] = {}
    topic_titles: dict[str, str] = {}
    proxy = (settings.firecrawl_scrape_proxy or "stealth").strip() or "stealth"

    def _absorb_markdown(markdown: str) -> None:
        nonlocal topics, video_titles
        videos = extract_top_video_titles(markdown, limit=video_limit)
        for title, url in videos:
            video_titles.append(title)
            parts = _split_title_topics(title)
            if not parts:
                t = _normalize_topic(re.sub(r"【[^】]*】", "", title))
                parts = [t] if t else []
            for t in parts:
                key = t.lower()
                if key in seen:
                    continue
                seen.add(key)
                topics.append(t)
                if url:
                    topic_sources[t] = url
                topic_titles[t] = title
        if not topics:
            for t in extract_topics_from_markdown(markdown, limit=max(limit * 3, 15)):
                key = t.lower()
                if key in seen:
                    continue
                seen.add(key)
                topics.append(t)

    async def _interact_absorb(url: str, label: str) -> str:
        query_used.append(f"{label}:{url}")
        doc = await firecrawl_scrape_with_interact(
            settings,
            url=url,
            proxy=proxy,
        )
        md = ""
        if isinstance(doc.get("markdown"), str):
            md = doc["markdown"]
        elif isinstance(doc.get("content"), str):
            md = doc["content"]
        if md:
            _absorb_markdown(md)
        return md

    # 1) 投稿视频页 + Firecrawl actions
    upload_md = ""
    if upload_url:
        try:
            upload_md = await _interact_absorb(upload_url, "interact-scrape")
        except Exception:
            logger.exception("Firecrawl interact scrape upload page failed url=%s", upload_url)

    # 2) 投稿页空壳 → 主页交互抓「代表作/视频卡」
    home = (settings.firecrawl_bilibili_dynamic_url or "").strip()
    if (len(topics) < limit or _is_empty_bilibili_video_list(upload_md)) and home:
        try:
            await _interact_absorb(home, "interact-scrape-home")
        except Exception:
            logger.exception("Firecrawl interact scrape home failed url=%s", home)

    # 3) 额外 URL 普通 scrape 兜底
    if len(topics) < limit:
        for u in settings.firecrawl_bilibili_extra_urls or []:
            u = (u or "").strip()
            if not u or u in {upload_url, home}:
                continue
            query_used.append(f"scrape:{u}")
            try:
                doc = await firecrawl_scrape(settings, url=u, wait_for_ms=4000)
            except Exception:
                logger.exception("Firecrawl scrape bilibili failed url=%s", u)
                continue
            md = doc.get("markdown") if isinstance(doc.get("markdown"), str) else ""
            if not md and isinstance(doc.get("content"), str):
                md = doc["content"]
            if md:
                _absorb_markdown(md)
            if len(topics) >= limit:
                break

    if topics:
        return topics[: max(limit * 2, video_limit)], query_used, topic_sources, topic_titles

    # 4) 搜索兜底（仍限 B 站，不查百科）
    fallback_q = "梗指南 site:bilibili.com/video"
    query_used.append(f"search:{fallback_q}")
    try:
        items = await firecrawl_search(
            settings, query=fallback_q, limit=min(video_limit, 8), include_markdown=True
        )
    except Exception:
        logger.exception("Firecrawl bilibili search fallback failed")
        return [], query_used, topic_sources, topic_titles

    for item in items:
        title = _title_from_item(item)
        url = _url_from_item(item)
        video_titles.append(title)
        for t in extract_topics_from_markdown(title + "\n" + _intro_from_item(item), limit=8):
            key = t.lower()
            if key in seen:
                continue
            seen.add(key)
            topics.append(t)
            if url:
                topic_sources[t] = url
            topic_titles[t] = title
        if len(topics) >= limit:
            break
    return topics[: max(limit * 2, video_limit)], query_used, topic_sources, topic_titles


_BLOCKED_HOST_FRAGMENTS = (
    "baike.baidu.com",
    "moegirl.org",
    "wikipedia.org",
)

_IMAGE_EXT_RE = re.compile(r"\.(?:jpg|jpeg|png|webp|gif)(?:\?|$)", re.I)


def _host_blocked(url: str) -> bool:
    host = urlparse(url).netloc.lower()
    return any(frag in host for frag in _BLOCKED_HOST_FRAGMENTS)


def _image_from_item(item: dict) -> str | None:
    for key in ("imageUrl", "image", "ogImage", "thumbnail", "thumbnailUrl"):
        raw = item.get(key)
        if isinstance(raw, str) and raw.strip().startswith("http"):
            url = raw.strip()
            if _IMAGE_EXT_RE.search(url) or "hdslb.com" in url or "biliimg" in url:
                return url[:1000]
            if url.startswith("http"):
                return url[:1000]
    metadata = item.get("metadata")
    if isinstance(metadata, dict):
        for key in ("og:image", "ogImage", "image", "twitter:image"):
            raw = metadata.get(key)
            if isinstance(raw, str) and raw.strip().startswith("http"):
                return raw.strip()[:1000]
    return None


def _images_from_markdown(markdown: str) -> list[str]:
    urls: list[str] = []
    seen: set[str] = set()
    for m in re.finditer(r"!\[[^\]]*]\((https?://[^)\s]+)\)", markdown or ""):
        url = m.group(1).strip()
        if not url or url in seen:
            continue
        if any(x in url.lower() for x in ("avatar", "face", "icon", "logo", "emoji")):
            continue
        if not _IMAGE_EXT_RE.search(url) and "hdslb.com" not in url and "biliimg" not in url:
            continue
        seen.add(url)
        urls.append(url[:1000])
        if len(urls) >= 5:
            break
    return urls


async def _fetch_source_context(settings: Settings, source_url: str) -> str:
    url = (source_url or "").strip()
    if not url.startswith("http"):
        return ""
    try:
        doc = await firecrawl_scrape(settings, url=url, wait_for_ms=2500)
    except Exception:
        logger.exception("Scrape source context failed url=%s", url)
        return ""
    md = ""
    if isinstance(doc.get("markdown"), str):
        md = doc["markdown"]
    elif isinstance(doc.get("content"), str):
        md = doc["content"]
    desc = doc.get("description") if isinstance(doc.get("description"), str) else ""
    title = doc.get("title") if isinstance(doc.get("title"), str) else ""
    merged = "\n".join(x for x in (title, desc, md) if x)
    return sanitize_source_text(merged, 1400)


async def _search_meme_details(
    settings: Settings,
    *,
    topic: str,
    include_markdown: bool = True,
) -> tuple[str, str | None, str | None, list[str]]:
    """阶段2：Firecrawl 搜索相关梗细节与图片（排除百科站）。

    返回 (detail_text, detail_url, image_url, query_used_extra)。
    """
    queries = [
        f"{topic} 是什么梗",
        f"{topic} 梗 含义",
    ]
    query_used: list[str] = []
    texts: list[str] = []
    detail_url: str | None = None
    image_url: str | None = None

    for q in queries:
        query_used.append(f"detail-search:{q}")
        try:
            items = await firecrawl_search(
                settings, query=q, limit=4, include_markdown=include_markdown
            )
        except Exception:
            logger.exception("Firecrawl detail search failed query=%s", q)
            continue
        for item in items:
            url = _url_from_item(item)
            if url and _host_blocked(url):
                continue
            title = _title_from_item(item)
            snippet = _intro_from_item(item)
            if title or snippet:
                texts.append(sanitize_source_text(f"{title}\n{snippet}", 500))
            if not detail_url and url and url.startswith("http"):
                detail_url = url
            if not image_url:
                image_url = _image_from_item(item)
        if detail_url and (texts or image_url):
            break

    # 抓取详情页补正文与配图
    if detail_url:
        try:
            doc = await firecrawl_scrape(settings, url=detail_url, wait_for_ms=2500)
            md = doc.get("markdown") if isinstance(doc.get("markdown"), str) else ""
            if not md and isinstance(doc.get("content"), str):
                md = doc["content"]
            desc = doc.get("description") if isinstance(doc.get("description"), str) else ""
            title = doc.get("title") if isinstance(doc.get("title"), str) else ""
            page_text = sanitize_source_text("\n".join(x for x in (title, desc, md) if x), 1200)
            if page_text:
                texts.append(page_text)
            if not image_url and md:
                imgs = _images_from_markdown(md)
                if imgs:
                    image_url = imgs[0]
            if not image_url:
                meta = doc.get("metadata") if isinstance(doc.get("metadata"), dict) else {}
                for key in ("og:image", "ogImage", "image"):
                    raw = meta.get(key)
                    if isinstance(raw, str) and raw.startswith("http"):
                        image_url = raw.strip()[:1000]
                        break
        except Exception:
            logger.exception("Scrape detail page failed url=%s", detail_url)

    detail_text = sanitize_source_text("\n".join(texts), 1600)
    return detail_text, detail_url, image_url, query_used


async def _enrich_topic(
    settings: Settings,
    *,
    topic: str,
    score: float,
    source_url: str | None = None,
    source_title: str | None = None,
    include_markdown: bool = True,
) -> tuple[CrawlMemeCandidate, list[str]]:
    """三阶段 enrichment：
    1) 读 B 站 UP 视频并总结
    2) Firecrawl 搜索细节与图片
    3) 合并为最终候选（由 Java 入库）
    """
    bilibili_url = (source_url or "").strip() or _bilibili_search_url(topic)
    title = (source_title or "").strip() or topic
    query_extra: list[str] = []

    # 1) B 站理解
    bilibili_text = ""
    if "bilibili.com/video" in bilibili_url:
        query_extra.append(f"bilibili-scrape:{bilibili_url}")
        bilibili_text = await _fetch_source_context(settings, bilibili_url)
    if not bilibili_text:
        bilibili_text = sanitize_source_text(title, 200)
    bilibili_summary = await summarize_from_bilibili(
        settings,
        topic=topic,
        source_url=bilibili_url,
        source_title=title,
        source_text=bilibili_text,
    )

    # 2) Firecrawl 细节 + 图片
    detail_text, detail_url, image_url, search_queries = await _search_meme_details(
        settings, topic=topic, include_markdown=include_markdown
    )
    query_extra.extend(search_queries)

    # 若 B 站视频页本身有封面图，可作兜底
    if not image_url and "bilibili.com/video" in bilibili_url:
        try:
            doc = await firecrawl_scrape(settings, url=bilibili_url, wait_for_ms=1500)
            md = doc.get("markdown") if isinstance(doc.get("markdown"), str) else ""
            imgs = _images_from_markdown(md or "")
            if imgs:
                image_url = imgs[0]
            meta = doc.get("metadata") if isinstance(doc.get("metadata"), dict) else {}
            if not image_url:
                for key in ("og:image", "ogImage", "image"):
                    raw = meta.get(key)
                    if isinstance(raw, str) and raw.startswith("http"):
                        image_url = raw.strip()[:1000]
                        break
        except Exception:
            logger.debug("Optional bilibili cover scrape failed url=%s", bilibili_url)

    # 3) 合并最终简介
    try:
        intro = await finalize_meme_intro(
            settings,
            topic=topic,
            bilibili_summary=bilibili_summary,
            detail_text=detail_text,
            source_url=bilibili_url,
            detail_url=detail_url or "",
        )
    except Exception:
        logger.exception("Finalize intro failed topic=%s", topic)
        intro = bilibili_summary or fallback_intro(topic)

    if detail_url and detail_url == bilibili_url:
        detail_url = None

    cand = CrawlMemeCandidate(
        title=topic,
        introduction=intro[:255],
        source_url=bilibili_url[:1000],
        image_url=image_url,
        detail_url=(detail_url[:1000] if detail_url else None),
        tags=_guess_tags(topic, bilibili_url, ["B站", "实时"]),
        score=score,
    )
    return cand, query_extra


async def crawl_hot_memes(
    request: CrawlHotMemesRequest,
    settings: Settings | None = None,
) -> CrawlHotMemesResponse:
    cfg = settings or get_settings()
    query_used: list[str] = []
    topics: list[str] = []
    topic_sources: dict[str, str] = {}
    topic_titles: dict[str, str] = {}

    if request.query and request.query.strip():
        topic = _normalize_topic(request.query.strip()) or request.query.strip()[:24]
        topics = [topic]
        query_used = [f"topic:{topic}"]
        topic_sources[topic] = _bilibili_search_url(topic)
        topic_titles[topic] = topic
    else:
        topics, query_used, topic_sources, topic_titles = await _discover_topics_from_bilibili(
            cfg, limit=request.limit
        )
        if not topics:
            # 兜底：B 站站内关键词搜索（不查百科）
            fallback_queries = [
                q.strip()
                for q in (cfg.firecrawl_default_queries or [])
                if q and q.strip()
            ] or ["网络流行语", "网络梗"]
            for q in fallback_queries[:2]:
                sq = f"{q} site:bilibili.com/video"
                query_used.append(f"search:{sq}")
                try:
                    items = await firecrawl_search(
                        cfg, query=sq, limit=3, include_markdown=request.include_markdown
                    )
                except Exception:
                    logger.exception("Fallback bilibili search failed")
                    continue
                for item in items:
                    title = _title_from_item(item)
                    url = _url_from_item(item)
                    for t in extract_topics_from_markdown(
                        title + "\n" + _intro_from_item(item), limit=3
                    ):
                        if t not in topics:
                            topics.append(t)
                            if url:
                                topic_sources[t] = url
                            topic_titles[t] = title

    seen_topics: set[str] = set()
    seen_urls: set[str] = set()
    candidates: list[CrawlMemeCandidate] = []
    for i, topic in enumerate(topics):
        if len(candidates) >= request.limit:
            break
        key = topic.lower()
        if key in seen_topics:
            continue
        seen_topics.add(key)
        try:
            cand, extra_queries = await _enrich_topic(
                cfg,
                topic=topic,
                score=100.0 - i,
                source_url=topic_sources.get(topic),
                source_title=topic_titles.get(topic),
                include_markdown=request.include_markdown,
            )
            query_used.extend(extra_queries)
        except Exception:
            logger.exception("Enrich topic failed topic=%s", topic)
            continue
        url_key = hashlib.sha1(cand.source_url.encode("utf-8")).hexdigest()
        if url_key in seen_urls:
            continue
        seen_urls.add(url_key)
        candidates.append(cand)

    candidates.sort(key=lambda c: c.score, reverse=True)
    return CrawlHotMemesResponse(
        query_used=query_used,
        candidates=candidates[: request.limit],
        credits_hint="bilibili_then_firecrawl",
    )
