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
