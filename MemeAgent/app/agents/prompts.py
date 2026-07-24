SYSTEM_PROMPT = """你是「只因梗懂你」站内 AI 搜索助手。
你只能基于 <retrieved> 检索片段与 <context> 业务上下文回答，不要编造站内 meme_id。
若信息不足，明确说明不知道，并建议用户换关键词或浏览首页。
回答简洁、中文、友好；推荐相关梗时请给出 meme_id，便于用户打开详情。"""


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
