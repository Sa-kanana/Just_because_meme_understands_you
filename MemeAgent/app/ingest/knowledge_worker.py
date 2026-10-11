import logging
import time

from app.core.settings import get_settings
from app.ingest.knowledge_chunker import build_knowledge_chunks
from app.retrieval.knowledge_repository import delete_knowledge_document, upsert_knowledge_document
from app.schemas.knowledge import KnowledgeDocument, KnowledgeIngestRequest

logger = logging.getLogger(__name__)


async def process_knowledge_ingest(request: KnowledgeIngestRequest) -> tuple[int, int]:
    """完成向量事务后再应答；Java MySQL outbox 承担持久化异步与重试。"""
    settings = get_settings()
    task_count = 0
    skipped = 0
    for doc in request.documents:
        document_started_at = time.perf_counter()
        if doc.status == "deleted":
            applied, deleted_count = await delete_knowledge_document(
                doc.doc_id, doc.revision
            )
            logger.info(
                "Applied knowledge delete doc_id=%s revision=%s applied=%s vectors=%s",
                doc.doc_id,
                doc.revision,
                applied,
                deleted_count,
            )
        else:
            chunking_started_at = time.perf_counter()
            chunks = build_knowledge_chunks(
                doc,
                chunk_size=settings.knowledge_chunk_size,
                chunk_overlap=settings.knowledge_chunk_overlap,
            )
            chunking_elapsed_ms = int((time.perf_counter() - chunking_started_at) * 1000)
            logger.info(
                "Knowledge document chunked doc_id=%s revision=%s chars=%s chunks=%s chunking_ms=%s",
                doc.doc_id,
                doc.revision,
                len(doc.content),
                len(chunks),
                chunking_elapsed_ms,
            )
            applied = await upsert_knowledge_document(doc, chunks)
            logger.info(
                "Applied knowledge upsert doc_id=%s revision=%s chunks=%s applied=%s",
                doc.doc_id,
                doc.revision,
                len(chunks),
                applied,
            )
            logger.info(
                "Knowledge document ingest finished doc_id=%s revision=%s applied=%s elapsed_ms=%s",
                doc.doc_id,
                doc.revision,
                applied,
                int((time.perf_counter() - document_started_at) * 1000),
            )
        skipped += 0 if applied else 1
        task_count += 1
    return task_count, skipped
