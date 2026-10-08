"""Input normalization and telemetry for untrusted MemeAgent request data."""

from __future__ import annotations

import re
from typing import Any

# Telemetry only: actual prompt safety comes from fixed roles, JSON data, and the tool allowlist.
_INJECTION_MARKERS = re.compile(
    r"(ignore\s+(all\s+)?(previous|above|prior)\s+instructions"
    r"|system\s*prompt"
    r"|you\s+are\s+now"
    r"|developer\s+mode"
    r"|jailbreak"
    r"|忽略(以上|之前|全部)?(指令|提示|规则)"
    r"|你现在是"
    r"|跳出角色"
    r"|泄露(系统|提示词|密钥|密码)"
    r"|执行\s*sql"
    r"|drop\s+table"
    r"|union\s+select)",
    re.IGNORECASE,
)

_CONTROL_CHARS = re.compile(r"[\x00-\x08\x0b\x0c\x0e-\x1f]")


def sanitize_user_text(text: str, *, max_len: int = 2000) -> str:
    """清洗用户可见文本：去控制字符、截断。"""
    raw = _CONTROL_CHARS.sub("", (text or "").strip())
    if len(raw) > max_len:
        raw = raw[:max_len].rstrip() + "…"
    return raw


def looks_like_prompt_injection(text: str) -> bool:
    return bool(_INJECTION_MARKERS.search(text or ""))


def sanitize_chat_messages(messages: list[Any]) -> list[Any]:
    """
    只保留 user/assistant；丢弃客户端传入的 system（防提权）。
    messages 元素需有 role/content 属性（Pydantic ChatMessage）。
    """
    out = []
    for msg in messages or []:
        role = getattr(msg, "role", None)
        content = getattr(msg, "content", "") or ""
        if role not in ("user", "assistant"):
            continue
        cleaned = sanitize_user_text(str(content), max_len=4000)
        if not cleaned:
            continue
        # 就地拷贝：构造同类型太重，search_agent 只用 content/role
        out.append(type(msg)(role=role, content=cleaned))
    return out


def sanitize_business_extra(extra: dict[str, Any] | None) -> dict[str, Any]:
    """只放行 Java 约定的公开检索辅助字段，剥离邮箱/手机/token 等。"""
    if not isinstance(extra, dict):
        return {}
    allowed_root = {"snippets"}
    cleaned: dict[str, Any] = {}
    for key, value in extra.items():
        if key not in allowed_root:
            continue
        if key == "snippets" and isinstance(value, list):
            snippets = []
            for item in value[:20]:
                if not isinstance(item, dict):
                    continue
                raw_tags = item.get("tags")
                snippets.append(
                    {
                        "meme_id": str(item.get("meme_id") or item.get("memeId") or "")[:64],
                        "title": sanitize_user_text(str(item.get("title") or ""), max_len=200),
                        "introduction": sanitize_user_text(
                            str(item.get("introduction") or ""), max_len=800
                        ),
                        "tags": [
                            sanitize_user_text(str(t), max_len=40)
                            for t in (raw_tags[:10] if isinstance(raw_tags, list) else [])
                            if str(t).strip()
                        ],
                    }
                )
            cleaned["snippets"] = snippets
    return cleaned


def sanitize_hint_meme_ids(ids: list[str] | None, *, limit: int = 16) -> list[str]:
    out: list[str] = []
    for raw in ids or []:
        s = str(raw).strip()
        if not s or len(s) > 64:
            continue
        if not re.fullmatch(r"[A-Za-z0-9_\-]+", s):
            continue
        out.append(s)
        if len(out) >= limit:
            break
    return out
