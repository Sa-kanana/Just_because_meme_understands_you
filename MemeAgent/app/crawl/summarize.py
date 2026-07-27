# -*- coding: utf-8 -*-
"""根据 B 站出处 + Firecrawl 细节，用 LLM 生成干净梗简介。"""

from __future__ import annotations

import logging
import re

from langchain_core.messages import HumanMessage, SystemMessage
from langchain_openai import ChatOpenAI

from app.core.settings import Settings

logger = logging.getLogger(__name__)

_INTRO_MAX = 240

_DRAFT_SYSTEM = (
    "你是网络流行梗研究员。先根据 B 站 UP 主视频材料，用一两句中文概括该梗在视频语境中的含义。"
    "只输出概括正文，不要标题、编号、markdown、反斜杠、【】或播放量噪音。"
    "材料不足时写：该梗出自B站梗指南相关视频，具体语境见原视频。"
    "不要使用「可能」「或许」等含糊措辞。控制在 30～80 个汉字。"
)

_FINAL_SYSTEM = (
    "你是网络流行梗编辑。综合「B站理解摘要」与「网页补充材料」，写一句面向用户的最终介绍。"
    "优先采信 B 站理解；网页材料用于补充出处、流行背景或用法，勿照搬网页原文与符号噪音。"
    "只输出介绍正文，不要标题、编号、引号、markdown、反斜杠、【】、《》、HTML。"
    "不要使用「可能」「或许」「大概」。控制在 40～120 个汉字。"
)


def sanitize_source_text(text: str, max_len: int = 1500) -> str:
    """去掉采集噪声，保留可读正文。"""
    raw = text or ""
    raw = raw.replace("\u0000", " ")
    raw = raw.replace("\\\\", " ").replace("\\", " ")
    raw = re.sub(r"```.*?```", " ", raw, flags=re.S)
    raw = re.sub(r"`[^`]*`", " ", raw)
    raw = re.sub(r"!?\[[^\]]*]\([^)]*\)", " ", raw)
    raw = re.sub(r"[#>*_|~]+", " ", raw)
    raw = re.sub(r"【[^】]*】", " ", raw)
    raw = re.sub(r"（[^）]*播放[^）]*）", " ", raw)
    raw = re.sub(r"\b\d+(\.\d+)?\s*万\b", " ", raw)
    raw = re.sub(r"\b\d{1,2}:\d{2}(:\d{2})?\b", " ", raw)
    raw = re.sub(r"(最新|合作|充电|稍后再看|点赞|投币|收藏|分享|关注)", " ", raw)
    raw = re.sub(r"\s+", " ", raw).strip()
    if len(raw) <= max_len:
        return raw
    return raw[: max_len - 1].rstrip() + "…"


def sanitize_intro(text: str, max_len: int = _INTRO_MAX) -> str:
    cleaned = sanitize_source_text(text, max_len=max_len * 2)
    cleaned = cleaned.strip(" \t\r\n\"'“”‘’。．.")
    cleaned = re.sub(r"^[：:\-—]+", "", cleaned).strip()
    if not cleaned:
        return ""
    if not cleaned.endswith(("。", "！", "？", "…")):
        cleaned += "。"
    if len(cleaned) <= max_len:
        return cleaned
    return cleaned[: max_len - 1].rstrip("，,、；; ") + "…"


def fallback_intro(topic: str) -> str:
    name = (topic or "").strip() or "该梗"
    return sanitize_intro(f"「{name}」是近期在 B 站梗指南相关内容中出现的网络流行梗，详见原视频。")


def _build_llm(settings: Settings, *, max_tokens: int = 160) -> ChatOpenAI | None:
    if not settings.openai_api_key:
        return None
    kwargs: dict = {
        "model": settings.openai_chat_model,
        "api_key": settings.openai_api_key,
        "max_tokens": max_tokens,
        "streaming": False,
        "temperature": 0.2,
    }
    if settings.openai_base_url:
        kwargs["base_url"] = settings.openai_base_url
    return ChatOpenAI(**kwargs)


def _message_text(resp) -> str:
    content = resp.content if hasattr(resp, "content") else str(resp)
    if isinstance(content, list):
        return "".join(
            str(part.get("text", "")) if isinstance(part, dict) else str(part)
            for part in content
        )
    return str(content or "")


async def _ainvoke_text(llm: ChatOpenAI, system: str, user: str) -> str:
    resp = await llm.ainvoke(
        [
            SystemMessage(content=system),
            HumanMessage(content=user),
        ]
    )
    return sanitize_intro(_message_text(resp))


async def summarize_from_bilibili(
    settings: Settings,
    *,
    topic: str,
    source_url: str = "",
    source_title: str = "",
    source_text: str = "",
) -> str:
    """阶段1：先根据 B 站 UP 视频材料理解并总结。"""
    topic = (topic or "").strip()
    if not topic:
        return fallback_intro("该梗")

    title = sanitize_source_text(source_title, 120)
    body = sanitize_source_text(source_text, 1200)
    llm = _build_llm(settings, max_tokens=120)
    if llm is None:
        return fallback_intro(topic)

    user_payload = (
        f"梗名：{topic}\n"
        f"B站视频标题：{title or '无'}\n"
        f"B站链接：{(source_url or '').strip() or '无'}\n"
        f"视频相关材料：\n{body or '（材料不足）'}"
    )
    try:
        draft = await _ainvoke_text(llm, _DRAFT_SYSTEM, user_payload)
        if len(draft) >= 12:
            return draft
    except Exception:
        logger.exception("Bilibili draft summarize failed topic=%s", topic)
    return fallback_intro(topic)


async def finalize_meme_intro(
    settings: Settings,
    *,
    topic: str,
    bilibili_summary: str = "",
    detail_text: str = "",
    source_url: str = "",
    detail_url: str = "",
) -> str:
    """阶段2后：综合 B 站理解与 Firecrawl 细节，输出最终简介。"""
    topic = (topic or "").strip()
    if not topic:
        return fallback_intro("该梗")

    draft = sanitize_intro(bilibili_summary) if bilibili_summary else ""
    details = sanitize_source_text(detail_text, 1200)
    if not details and draft:
        return draft
    if not details and not draft:
        return fallback_intro(topic)

    llm = _build_llm(settings, max_tokens=160)
    if llm is None:
        return draft or fallback_intro(topic)

    user_payload = (
        f"梗名：{topic}\n"
        f"B站理解摘要：{draft or '无'}\n"
        f"B站链接：{(source_url or '').strip() or '无'}\n"
        f"补充详情链接：{(detail_url or '').strip() or '无'}\n"
        f"网页补充材料：\n{details or '无'}"
    )
    try:
        final = await _ainvoke_text(llm, _FINAL_SYSTEM, user_payload)
        if len(final) >= 12:
            return final
    except Exception:
        logger.exception("Finalize meme intro failed topic=%s", topic)
    return draft or fallback_intro(topic)


async def summarize_meme_intro(
    settings: Settings,
    *,
    topic: str,
    source_url: str = "",
    source_title: str = "",
    source_text: str = "",
) -> str:
    """兼容旧调用：仅有一份材料时直接概括。"""
    return await summarize_from_bilibili(
        settings,
        topic=topic,
        source_url=source_url,
        source_title=source_title,
        source_text=source_text,
    )
