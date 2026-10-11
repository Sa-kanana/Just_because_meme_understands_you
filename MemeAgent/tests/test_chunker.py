from datetime import datetime, timezone

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


def test_long_document_chunks_repeat_title_and_tags_for_vector_retrieval():
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
    assert all("标签: 网络用语 评价" in chunk for chunk in chunks)


def test_openai():
    settings = get_settings()
    apply_langsmith_env(settings)

    llm = ChatOpenAI(
        model=settings.openai_chat_model,
        base_url=settings.openai_base_url,
        api_key=settings.openai_api_key,
    )
    res = llm.invoke("你好，可以跟我讲个故事吗")
    print(res)
    print(f"LangSmith project={settings.langchain_project}")
