from datetime import datetime, timezone

import pytest
from langchain_openai import ChatOpenAI

from app.core.settings import apply_langsmith_env, get_settings
from app.ingest.chunker import build_chunks
from app.schemas.ingest import IngestDocument


def test_build_chunks_includes_title():
    doc = IngestDocument(
        meme_id="42",
        title="只因你太美",
        introduction="来自某综艺的流行梗",
        tags=["音乐", "流行"],
        updated_at=datetime.now(timezone.utc),
        content_hash="hashhash12",
    )
    chunks = build_chunks(doc)
    assert len(chunks) >= 1
    assert "只因你太美" in chunks[0]
    assert "音乐" in chunks[0]


def test_long_document_chunks_repeat_title_without_repeating_tags():
    doc = IngestDocument(
        meme_id="43",
        title="不明觉厉",
        introduction="这是一段用于测试的梗介绍。" * 30,
        tags=["网络用语", "评价"],
        updated_at=datetime.now(timezone.utc),
        content_hash="hashhash13",
    )

    chunks = build_chunks(doc, max_chunk_chars=80)

    assert len(chunks) > 1
    assert all("标题: 不明觉厉" in chunk for chunk in chunks)
    assert "标签: 网络用语 评价" in chunks[0]
    assert all("标签:" not in chunk for chunk in chunks[1:])


def test_build_chunks_respects_global_limit_with_maximum_fields():
    doc = IngestDocument(
        meme_id="44",
        title="题" * 200,
        introduction="正文" * 2500,
        tags=["标" * 12 for _ in range(20)],
        updated_at=datetime.now(timezone.utc),
        content_hash="hashhash14",
    )

    chunks = build_chunks(doc)

    assert 1 <= len(chunks) <= 6
    assert all(len(chunk) <= 1200 for chunk in chunks)
    assert "标签:" in chunks[0]
    assert all("标签:" not in chunk for chunk in chunks[1:])


def test_chunker_rejects_more_than_six_chunks():
    doc = IngestDocument(
        meme_id="45",
        title="不明觉厉",
        introduction="正文" * 500,
        tags=[],
        updated_at=datetime.now(timezone.utc),
        content_hash="hashhash15",
    )

    with pytest.raises(ValueError, match="6"):
        build_chunks(doc, max_chunk_chars=80)


@pytest.mark.parametrize("chunk_size", [0, 1201])
def test_chunk_size_must_be_within_global_bounds(chunk_size):
    doc = IngestDocument(
        meme_id="46",
        title="梗",
        introduction="正文",
        updated_at=datetime.now(timezone.utc),
        content_hash="hashhash16",
    )

    with pytest.raises(ValueError):
        build_chunks(doc, max_chunk_chars=chunk_size)


def test_openai():
    settings = get_settings()
    if not settings.openai_api_key:
        pytest.skip("需要 OPENAI_API_KEY 才能运行真实 OpenAI 调用")

    apply_langsmith_env(settings)

    llm = ChatOpenAI(
        model=settings.openai_chat_model,
        base_url=settings.openai_base_url,
        api_key=settings.openai_api_key,
    )
    res = llm.invoke("你好，可以跟我讲个故事吗")
    print(res)
    print(f"LangSmith project={settings.langchain_project}")
