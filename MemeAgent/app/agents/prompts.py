"""Load the canonical managed prompt and serialize the request's untrusted JSON envelope."""

from __future__ import annotations

import json
from importlib.resources import files
from typing import Any


# 提示词版本用于日志标记；修改提示词后同步升级以便追踪行为变化。
PROMPT_VERSION = "meme-search-v8"

# 可直接编辑的 Markdown 提示词资源；随 app.agents 包一起发布。
SYSTEM_PROMPT_PATH = files("app.agents").joinpath("prompt_files").joinpath("search_system_zh.md")
AGENT_SYSTEM_PROMPT = SYSTEM_PROMPT_PATH.read_text(encoding="utf-8").strip()


def build_user_payload(query: str, context: dict[str, Any]) -> str:
    """Serialize current user and business data as one JSON value, never a prompt template."""
    return json.dumps(
        {"query": query, "context": context},
        ensure_ascii=False,
        separators=(",", ":"),
    )
