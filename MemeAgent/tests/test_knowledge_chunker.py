from datetime import datetime, timezone

from app.ingest.knowledge_chunker import build_knowledge_chunks
from app.schemas.knowledge import KnowledgeDocument


def _document(content: str) -> KnowledgeDocument:
    return KnowledgeDocument(
        doc_id="123",
        title="新人 FAQ",
        content=content,
        category="玩法",
        tags=["新手", "规则"],
        updated_at=datetime.now(timezone.utc),
        content_hash="legacy-hash-123",
    )


def test_chunks_keep_markdown_section_path_and_do_not_emit_header_only_chunk():
    chunks = build_knowledge_chunks(
        _document("# 登录\n\n使用手机号登录。\n\n## 问题：忘记密码怎么办？\n\n通过短信重置密码。"),
        chunk_size=180,
    )

    assert len(chunks) == 2
    assert "标题: 新人 FAQ" in chunks[0].content
    assert "登录" in chunks[0].section_path
    assert "FAQ: 忘记密码怎么办？" in chunks[1].section_path
    assert "使用手机号登录" in chunks[0].content
    assert "短信重置密码" in chunks[1].content
    assert all("章节:" in chunk.content for chunk in chunks)


def test_pdf_form_feed_preserves_page_number_on_chunks():
    chunks = build_knowledge_chunks(_document("第一页说明。\f第二页说明。"))

    assert [chunk.page_number for chunk in chunks] == [1, 2]
    assert "页码: 1" in chunks[0].content
    assert "页码: 2" in chunks[1].content


def test_repeated_identical_chunks_are_deduplicated():
    chunks = build_knowledge_chunks(_document("重复内容。\n\n重复内容。"))

    assert len(chunks) == 1
    assert chunks[0].content.count("重复内容") == 1
