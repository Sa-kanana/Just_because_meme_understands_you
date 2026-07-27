# -*- coding: utf-8 -*-
"""extract_topics_from_markdown unit tests (no Firecrawl)."""

from app.crawl.service import extract_topics_from_markdown


def test_extract_quoted_and_hashtag():
    text = (
        "\u4eca\u5929\u300c\u7535\u5b50\u69a8\u83dc\u300d\u662f\u4ec0\u4e48\u6897\uff1f"
        "\u8fd8\u6709 #\u7edd\u7edd\u5b50# \u53c8\u706b\u4e86"
    )
    topics = extract_topics_from_markdown(text, limit=10)
    assert "\u7535\u5b50\u69a8\u83dc" in topics  # 电子榨菜
    assert "\u7edd\u7edd\u5b50" in topics  # 绝绝子


def test_extract_guide_video_titles():
    text = (
        "[![完颜慧德、拱出去、笑拥了是什么梗【梗指南】]"
        "(https://i0.hdslb.com/cover.jpg)](https://www.bilibili.com/video/BVxxx)\n"
        "[佛山电翰是什么梗【梗指南】](https://www.bilibili.com/video/BVyyy)\n"
        "关注数116\n粉丝数484.5万\n"
    )
    from app.crawl.service import extract_top_video_titles, topics_from_video_titles

    videos = extract_top_video_titles(text, limit=5)
    assert len(videos) >= 2
    assert videos[0][0].startswith("完颜慧德")
    topics = topics_from_video_titles(videos, topic_limit=10)
    assert "完颜慧德" in topics
    assert "拱出去" in topics
    assert "笑拥了" in topics
    assert "佛山电翰" in topics
    assert not any(t.startswith("![") for t in topics)
    assert "关注数116" not in topics


def test_extract_filters_noise():
    text = "\u70b9\u8d5e \u8f6c\u53d1 \u5173\u6ce8 bilibili \u9a8c\u8bc1\u7801"
    topics = extract_topics_from_markdown(text, limit=10)
    assert "\u70b9\u8d5e" not in topics
    assert "\u9a8c\u8bc1\u7801" not in topics
