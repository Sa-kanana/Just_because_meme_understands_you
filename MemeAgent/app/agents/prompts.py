SYSTEM_PROMPT = """你是「只因梗懂你」站内 AI 搜索助手。
你只能基于检索工具返回的片段与业务上下文回答，不要编造站内内容。
若信息不足，明确说明不知道，并建议用户换关键词或浏览首页。

对用户可见的回答要求：
- 只输出自然语言正文（中文、简洁、专业）
- 禁止在回答中出现任何内部标识或技术字段，包括但不限于：meme_id、request_id、session_id、score、chunk、embedding、hint_meme_ids
- 提到梗时只用标题/俗称，不要括号附加 id
- 相关梗的跳转由系统通过独立引用事件提供，你无需在正文中输出 id"""


def build_retrieval_block(chunks: list[tuple[str, str, float]]) -> str:
    lines = ["<retrieved>"]
    for meme_id, content, score in chunks:
        lines.append(f"[meme_id={meme_id} score={score:.3f}]")
        lines.append(content)
        lines.append("---")
    lines.append("</retrieved>")
    return "\n".join(lines)


def build_context_block(
    locale: str,
    hint_meme_ids: list[str],
    snippets: list[dict] | None = None,
) -> str:
    hints = ", ".join(hint_meme_ids) if hint_meme_ids else "无"
    lines = [
        "<context>",
        f"locale={locale}",
        f"hint_meme_ids={hints}",
    ]
    if snippets:
        lines.append("mysql_snippets:")
        for item in snippets:
            meme_id = item.get("meme_id") or item.get("memeId") or ""
            title = item.get("title") or ""
            intro = item.get("introduction") or ""
            tags = item.get("tags") or []
            tag_text = ", ".join(str(t) for t in tags) if isinstance(tags, list) else str(tags)
            lines.append(f"- meme_id={meme_id} title={title} tags={tag_text}")
            if intro:
                lines.append(f"  intro={intro}")
    lines.append("</context>")
    return "\n".join(lines)
