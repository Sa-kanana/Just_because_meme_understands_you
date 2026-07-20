SYSTEM_PROMPT = """你是「只因梗懂你」站内 AI 搜索助手。
你只能基于 <retrieved> 检索片段与 <context> 业务上下文回答，不要编造站内 meme_id。
若信息不足，明确说明不知道，并建议用户换关键词或浏览首页。
回答简洁、中文、友好；引用相关梗时请提及 meme_id。"""


def build_retrieval_block(chunks: list[tuple[str, str, float]]) -> str:
    lines = ["<retrieved>"]
    for meme_id, content, score in chunks:
        lines.append(f"[meme_id={meme_id} score={score:.3f}]")
        lines.append(content)
        lines.append("---")
    lines.append("</retrieved>")
    return "\n".join(lines)


def build_context_block(locale: str, hint_meme_ids: list[str]) -> str:
    hints = ", ".join(hint_meme_ids) if hint_meme_ids else "无"
    return f"<context>\nlocale={locale}\nhint_meme_ids={hints}\n</context>"
