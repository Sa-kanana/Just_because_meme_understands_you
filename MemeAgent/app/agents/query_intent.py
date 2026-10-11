"""轻量识别无需进入梗检索的纯社交问候。"""

import re
import unicodedata

# 仅匹配完整的问候或致谢，避免把“你好是什么梗”识别成闲聊。
_GREETING_PATTERN = re.compile(
    r"(?:你好(?:呀|啊|哇)?|您好(?:呀|啊)?|大家好|你好吗|嗨+|哈喽|hello|hi|"
    r"早上好|上午好|中午好|下午好|晚上好|晚安|谢谢(?:你|啦)?|多谢|辛苦了|在吗|再见)"
    r"[!！?？。.,，、~～]*",
    re.IGNORECASE,
)


def greeting_reply(query: str) -> str | None:
    """纯问候返回简短寒暄；包含实际问题的文本返回 None。"""
    normalized = unicodedata.normalize("NFKC", query or "").strip()
    if not normalized or _GREETING_PATTERN.fullmatch(normalized) is None:
        return None

    compact = re.sub(r"[!！?？。.,，、~～\s]+", "", normalized).casefold()
    if compact.startswith(("谢谢", "多谢", "辛苦了")):
        return "不客气！"
    if compact == "在吗":
        return "我在。"
    if compact == "再见":
        return "再见！"
    return "你好！"
