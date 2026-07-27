# -*- coding: utf-8 -*-
from app.crawl.summarize import fallback_intro, sanitize_intro, sanitize_source_text


def test_sanitize_source_text_strips_noise():
    raw = (
        "中国人能飞是什么梗【梗指南】\\\\ \\\\ 最新\\\\ \\\\ 85.3万\\\\ \\\\ 413\\\\ \\\\ 01:40"
        "；枪如人人如枪是什么梗【梗指南】"
    )
    cleaned = sanitize_source_text(raw)
    assert "\\" not in cleaned
    assert "【" not in cleaned
    assert "】" not in cleaned
    assert "中国人能飞" in cleaned


def test_sanitize_intro_ends_with_period():
    text = sanitize_intro("这是一句没有句号的介绍")
    assert text.endswith("。")
    assert "\\" not in text


def test_fallback_intro_clean():
    text = fallback_intro("旱厕蜗牛")
    assert "旱厕蜗牛" in text
    assert "\\" not in text
    assert "【" not in text


def test_host_block_fragments_not_in_sanitize():
    # 仅确认清洗不会引入百科噪声字符
    text = sanitize_source_text("某梗解释 baike 内容【标签】\\\\")
    assert "\\" not in text
    assert "【" not in text
