"""管理员知识库向量写入、删除与已发布文档检索。"""

from dataclasses import dataclass
from datetime import timezone
from contextlib import asynccontextmanager
import asyncio
import hashlib
import logging
import math
import re
import time

from app.core.settings import Settings, get_settings
from app.retrieval.db import get_pool, vector_literal
from app.retrieval.embeddings import embed_text, embed_texts
from app.retrieval.knowledge_checkpoints import (
    build_embedding_fingerprint,
    load_knowledge_embedding_checkpoints,
    store_knowledge_embedding_checkpoints,
)
from app.schemas.knowledge import KnowledgeDocument

logger = logging.getLogger(__name__)
_KNOWLEDGE_EMBEDDING_BATCH_SIZE = 25


class KnowledgeEmbeddingError(RuntimeError):
    """不包含正文或供应商原始响应的知识向量入库错误与持久化进度。"""

    def __init__(
        self,
        completed_chunks: int,
        total_chunks: int,
        stage: str,
        safe_message: str,
    ) -> None:
        super().__init__(safe_message)
        self.completed_chunks = completed_chunks
        self.total_chunks = total_chunks
        self.stage = stage
        self.safe_message = safe_message


@dataclass
class RetrievedKnowledgeChunk:
    """带有文档、章节和页码来源的管理员知识检索片段。"""

    doc_id: str
    content: str
    score: float
    title: str | None = None
    section_path: str = ""
    page_number: int | None = None
    exact_match: bool = False
    # 中文注释：正文包含完整查询短语时为 true，用于精确词项召回。
    content_match: bool = False
    # 中文注释：向量排名与词项排名经 RRF 融合后的排序值。
    rank_score: float = 0.0
    # 中文注释：向量候选的 RRF 分量，供评估脚本按阈值重放线上排序。
    vector_rank_score: float = 0.0
    # 中文注释：标题、标签或正文候选的 RRF 分量，供评估脚本重放线上排序。
    lexical_rank_score: float = 0.0


def normalize_knowledge_body(content: str) -> str:
    """与 Java/SQL 一致地移除 BOM、统一换行并折叠空白用于去重。"""
    without_bom = (content or "").replace("\ufeff", "").replace("\r\n", "\n").replace("\r", "\n")
    return re.sub(r"\s+", " ", without_bom).strip()


def _knowledge_sha256(doc: KnowledgeDocument) -> str:
    """校验 Java 传入的正文 SHA-256，旧客户端未提供时本地计算。"""
    expected = hashlib.sha256(normalize_knowledge_body(doc.content).encode("utf-8")).hexdigest()
    if doc.content_sha256 and doc.content_sha256.lower() != expected:
        raise ValueError("content_sha256 与标准化知识正文不匹配")
    return expected


def _checkpoint_validation_issue(
    checkpoint: tuple[str, list[float]] | None,
    expected_hash: str,
    dimension: int,
) -> str | None:
    """返回检查点不匹配原因，供安全修复流程校验。"""
    if checkpoint is None:
        return "missing"
    saved_hash, vector = checkpoint
    if saved_hash != expected_hash:
        return "chunk_hash"
    if len(vector) != dimension:
        return "dimension"
    if any(not math.isfinite(value) for value in vector):
        return "non_finite"
    return None


async def _current_doc_state(doc_id: str):
    """读取文档版本状态，避免为已处理的重放事件重复调用 embedding。"""
    pool = get_pool()
    async with pool.acquire() as conn:
        return await conn.fetchrow(
            "SELECT revision, status, content_sha256 FROM knowledge_doc_state WHERE doc_id = $1",
            doc_id,
        )


async def _revision_fence_result(conn, doc_id: str, revision: int, content_sha256: str) -> str:
    """调用方持有文档 advisory lock 时检查版本墓碑和已发布版本。"""
    state = await conn.fetchrow(
        "SELECT revision, status, content_sha256 FROM knowledge_doc_state WHERE doc_id = $1 FOR UPDATE",
        doc_id,
    )
    if not state:
        return "continue"
    state_revision = int(state["revision"])
    if state_revision > revision:
        return "stale"
    if state_revision == revision:
        if state["status"] == "DELETED":
            return "stale"
        if state["status"] == "READY" and state["content_sha256"] == content_sha256:
            return "already_ready"
        if state["content_sha256"] is not None and state["content_sha256"] != content_sha256:
            raise ValueError("相同知识 revision 对应了不同的正文或状态")
    return "continue"


async def _store_checkpoint_batch(
    doc: KnowledgeDocument,
    content_sha256: str,
    embedding_fingerprint: str,
    chunk_indices: list[int],
    chunk_hashes: list[str],
    embeddings: list[list[float]],
) -> bool:
    """短事务内重新检查 revision，成功提交后才允许增加完成进度。"""
    pool = get_pool()
    async with pool.acquire() as conn:
        async with conn.transaction():
            await conn.fetchval("SELECT pg_advisory_xact_lock($1::bigint)", int(doc.doc_id))
            fence = await _revision_fence_result(
                conn, doc.doc_id, doc.revision, content_sha256
            )
            if fence != "continue":
                return False
            await store_knowledge_embedding_checkpoints(
                conn,
                doc.doc_id,
                content_sha256,
                embedding_fingerprint,
                chunk_indices,
                chunk_hashes,
                embeddings,
            )
    return True


@asynccontextmanager
async def _knowledge_publish_transaction(conn, completed_chunks: int, total_chunks: int):
    """将正式发布事务错误转成带完整检查点进度的安全失败。"""
    try:
        async with conn.transaction():
            yield
    except KnowledgeEmbeddingError:
        raise
    except ValueError:
        raise
    except Exception as exc:
        raise KnowledgeEmbeddingError(
            completed_chunks,
            total_chunks,
            "publish",
            "向量已完成但正式索引发布失败",
        ) from exc


async def _embed_and_store_checkpoint_batch(
    doc: KnowledgeDocument,
    content_sha256: str,
    embedding_fingerprint: str,
    chunk_indices: list[int],
    chunk_hashes: list[str],
    raw_chunks: list[str],
    dimension: int,
    settings: Settings,
) -> tuple[list[list[float]], bool]:
    """生成并校验单批向量，再独立提交该批检查点。"""
    started_at = time.perf_counter()
    batch_texts = [raw_chunks[index] for index in chunk_indices]
    embedding_started_at = time.perf_counter()
    try:
        batch_embeddings = await embed_texts(batch_texts, settings)
    except Exception as exc:
        raise KnowledgeEmbeddingError(
            0, len(raw_chunks), "embedding", "向量生成失败"
        ) from exc
    embedding_elapsed_ms = int((time.perf_counter() - embedding_started_at) * 1000)
    if len(batch_embeddings) != len(chunk_indices):
        raise KnowledgeEmbeddingError(
            0, len(raw_chunks), "validation", "向量服务返回数量不匹配"
        )
    try:
        normalized_embeddings = [
            [float(value) for value in vector] for vector in batch_embeddings
        ]
    except (TypeError, ValueError, OverflowError) as exc:
        raise KnowledgeEmbeddingError(
            0, len(raw_chunks), "validation", "向量服务返回了无效数值"
        ) from exc
    if any(
        len(vector) != dimension or any(not math.isfinite(value) for value in vector)
        for vector in normalized_embeddings
    ):
        raise KnowledgeEmbeddingError(
            0, len(raw_chunks), "validation", "向量维度或数值不符合配置"
        )

    checkpoint_started_at = time.perf_counter()
    try:
        stored = await _store_checkpoint_batch(
            doc,
            content_sha256,
            embedding_fingerprint,
            chunk_indices,
            [chunk_hashes[index] for index in chunk_indices],
            normalized_embeddings,
        )
    except ValueError:
        raise
    except Exception as exc:
        raise KnowledgeEmbeddingError(
            0, len(raw_chunks), "checkpoint_persist", "向量已生成但进度保存失败"
        ) from exc
    checkpoint_elapsed_ms = int((time.perf_counter() - checkpoint_started_at) * 1000)
    logger.info(
        "Knowledge embedding batch completed doc_id=%s revision=%s chunks=%s stored=%s embedding_ms=%s checkpoint_ms=%s elapsed_ms=%s",
        doc.doc_id,
        doc.revision,
        len(chunk_indices),
        stored,
        embedding_elapsed_ms,
        checkpoint_elapsed_ms,
        int((time.perf_counter() - started_at) * 1000),
    )
    return normalized_embeddings, stored


async def upsert_knowledge_document(doc: KnowledgeDocument, chunks: list, *, expected_dim: int | None = None) -> bool:
    """按 revision 原子替换文档片段；返回是否应用了新版本。"""
    started_at = time.perf_counter()
    if doc.status == "deleted":
        raise ValueError("删除知识文档必须调用版本化删除操作")

    content_sha256 = _knowledge_sha256(doc)
    raw_chunks = [item.content if hasattr(item, "content") else str(item) for item in chunks]
    chunk_hashes = [hashlib.sha256(text.encode("utf-8")).hexdigest() for text in raw_chunks]
    total_chunks = len(raw_chunks)
    current = await _current_doc_state(doc.doc_id)
    if current and int(current["revision"]) >= doc.revision:
        if int(current["revision"]) > doc.revision:
            return False
        if current["status"] == "READY" and current["content_sha256"] == content_sha256:
            logger.info("Skip idempotent knowledge ingest doc_id=%s revision=%s", doc.doc_id, doc.revision)
            return False
        if current["status"] == "DELETED":
            return False
        if current["content_sha256"] is not None:
            raise ValueError("相同知识 revision 对应了不同的正文或状态")

    settings = get_settings()
    dimension = expected_dim or settings.vector_embedding_dim
    fingerprint_settings = (
        settings
        if settings.vector_embedding_dim == dimension
        else settings.model_copy(update={"vector_embedding_dim": dimension})
    )
    embedding_fingerprint = build_embedding_fingerprint(fingerprint_settings)
    checkpoints: dict[int, tuple[str, list[float]]] = {}
    if raw_chunks:
        checkpoint_load_started_at = time.perf_counter()
        pool = get_pool()
        try:
            async with pool.acquire() as conn:
                checkpoints = await load_knowledge_embedding_checkpoints(
                    conn, doc.doc_id, content_sha256, embedding_fingerprint
                )
        except Exception as exc:
            raise KnowledgeEmbeddingError(
                0, total_chunks, "checkpoint_load", "无法读取已保存的向量进度"
            ) from exc
        logger.info(
            "Knowledge checkpoint load completed doc_id=%s revision=%s found=%s elapsed_ms=%s",
            doc.doc_id,
            doc.revision,
            len(checkpoints),
            int((time.perf_counter() - checkpoint_load_started_at) * 1000),
        )

    # 只有序号、片段哈希、维度和数值均匹配的检查点才可复用。
    completed: dict[int, list[float]] = {}
    missing_indices: list[int] = []
    for index, expected_hash in enumerate(chunk_hashes):
        checkpoint = checkpoints.get(index)
        if checkpoint is None:
            missing_indices.append(index)
            continue
        saved_hash, vector = checkpoint
        if (
            saved_hash != expected_hash
            or len(vector) != dimension
            or any(not math.isfinite(value) for value in vector)
        ):
            missing_indices.append(index)
            continue
        completed[index] = vector
    reused_checkpoint_count = len(completed)

    # 每次 API 调用与 checkpoint 提交分离；数据库锁只覆盖进度写入，不覆盖网络等待。
    missing_batches = [
        missing_indices[start : start + _KNOWLEDGE_EMBEDDING_BATCH_SIZE]
        for start in range(0, len(missing_indices), _KNOWLEDGE_EMBEDDING_BATCH_SIZE)
    ]
    batch_concurrency = settings.knowledge_embedding_batch_concurrency
    for wave_start in range(0, len(missing_batches), batch_concurrency):
        batch_wave = missing_batches[wave_start : wave_start + batch_concurrency]
        results = await asyncio.gather(
            *(
                _embed_and_store_checkpoint_batch(
                    doc,
                    content_sha256,
                    embedding_fingerprint,
                    batch_indices,
                    chunk_hashes,
                    raw_chunks,
                    dimension,
                    settings,
                )
                for batch_indices in batch_wave
            ),
            return_exceptions=True,
        )
        first_embedding_error: KnowledgeEmbeddingError | None = None
        first_value_error: ValueError | None = None
        stale_revision = False
        for batch_indices, result in zip(batch_wave, results, strict=True):
            if isinstance(result, asyncio.CancelledError):
                raise result
            if isinstance(result, KnowledgeEmbeddingError):
                first_embedding_error = first_embedding_error or result
                continue
            if isinstance(result, ValueError):
                first_value_error = first_value_error or result
                continue
            if isinstance(result, BaseException):
                first_embedding_error = first_embedding_error or KnowledgeEmbeddingError(
                    len(completed), total_chunks, "embedding", "向量批次处理失败"
                )
                continue
            batch_vectors, stored = result
            if not stored:
                stale_revision = True
                continue
            completed.update(zip(batch_indices, batch_vectors, strict=True))

        if first_value_error is not None:
            raise first_value_error
        if first_embedding_error is not None:
            raise KnowledgeEmbeddingError(
                len(completed),
                total_chunks,
                first_embedding_error.stage,
                first_embedding_error.safe_message,
            ) from first_embedding_error
        if stale_revision:
            # 中文注释：更高 revision、删除墓碑或并发完成的同版本已阻止该任务继续写入。
            return False

    if len(completed) != total_chunks:
        raise KnowledgeEmbeddingError(
            len(completed), total_chunks, "checkpoint_validation", "已保存的向量进度不完整"
        )

    updated_at = doc.updated_at.astimezone(timezone.utc)
    title = doc.title.strip()
    category = (doc.category or "").strip() or None
    tags = [tag.strip() for tag in doc.tags if tag.strip()]
    pool = get_pool()
    publish_started_at = time.perf_counter()
    async with pool.acquire() as conn:
        async with _knowledge_publish_transaction(conn, len(completed), total_chunks):
            # 中文注释：Java 雪花 ID 可安全转换为 PostgreSQL bigint，事务级锁串行化首次建档。
            await conn.fetchval("SELECT pg_advisory_xact_lock($1::bigint)", int(doc.doc_id))
            fence = await _revision_fence_result(conn, doc.doc_id, doc.revision, content_sha256)
            if fence != "continue":
                return False
            published_vectors: dict[int, tuple[str, list[float]]] = {}
            if raw_chunks:
                try:
                    published_vectors = await load_knowledge_embedding_checkpoints(
                        conn, doc.doc_id, content_sha256, embedding_fingerprint
                    )
                except Exception as exc:
                    raise KnowledgeEmbeddingError(
                        len(completed), total_chunks, "checkpoint_load", "无法校验已保存的向量进度"
                    ) from exc
                invalid_checkpoints: list[tuple[int, str]] = []
                verified_checkpoint_count = 0
                for idx, expected_hash in enumerate(chunk_hashes):
                    checkpoint = published_vectors.get(idx)
                    issue = _checkpoint_validation_issue(checkpoint, expected_hash, dimension)
                    if issue:
                        invalid_checkpoints.append((idx, issue))
                    else:
                        verified_checkpoint_count += 1
                if invalid_checkpoints:
                    # 中文注释：内存中的 completed 只包含已校验或本次刚生成并校验的向量，
                    # 在同一发布事务内修复丢失/损坏的临时行，避免校验失败留下旧索引并反复重试。
                    issue_counts: dict[str, int] = {}
                    for _, issue in invalid_checkpoints:
                        issue_counts[issue] = issue_counts.get(issue, 0) + 1
                    logger.warning(
                        "Repairing knowledge embedding checkpoints doc_id=%s revision=%s issues=%s",
                        doc.doc_id,
                        doc.revision,
                        issue_counts,
                    )
                    repair_indices = [index for index, _ in invalid_checkpoints]
                    try:
                        await store_knowledge_embedding_checkpoints(
                            conn,
                            doc.doc_id,
                            content_sha256,
                            embedding_fingerprint,
                            repair_indices,
                            [chunk_hashes[index] for index in repair_indices],
                            [completed[index] for index in repair_indices],
                        )
                        published_vectors = await load_knowledge_embedding_checkpoints(
                            conn, doc.doc_id, content_sha256, embedding_fingerprint
                        )
                    except Exception as exc:
                        raise KnowledgeEmbeddingError(
                            len(completed),
                            total_chunks,
                            "checkpoint_persist",
                            "无法修复已保存的向量进度",
                        ) from exc

                    verified_checkpoint_count = 0
                    for idx, expected_hash in enumerate(chunk_hashes):
                        issue = _checkpoint_validation_issue(
                            published_vectors.get(idx), expected_hash, dimension
                        )
                        if issue:
                            raise KnowledgeEmbeddingError(
                                verified_checkpoint_count,
                                total_chunks,
                                "checkpoint_validation",
                                "已保存的向量进度修复后仍未通过校验",
                            )
                        verified_checkpoint_count += 1
            await conn.execute("DELETE FROM knowledge_vector_chunk WHERE doc_id = $1", doc.doc_id)
            await conn.executemany(
                """
                INSERT INTO knowledge_vector_chunk
                    (doc_id, chunk_index, content, content_hash, title, category, tags,
                     section_path, page_number, embedding, updated_at)
                VALUES ($1, $2, $3, $4, $5, $6, $7::text[], $8, $9, $10::vector, $11)
                """,
                [
                    (
                        doc.doc_id,
                        idx,
                        text,
                        content_sha256,
                        title,
                        category,
                        tags,
                        getattr(chunks[idx], "section_path", ""),
                        getattr(chunks[idx], "page_number", None),
                        vector_literal(published_vectors[idx][1]),
                        updated_at,
                    )
                    for idx, text in enumerate(raw_chunks)
                ],
            )
            await conn.execute(
                """
                INSERT INTO knowledge_doc_state (doc_id, revision, status, content_sha256, updated_at)
                VALUES ($1, $2, 'READY', $3, $4)
                ON CONFLICT (doc_id) DO UPDATE
                SET revision = EXCLUDED.revision,
                    status = EXCLUDED.status,
                    content_sha256 = EXCLUDED.content_sha256,
                    updated_at = EXCLUDED.updated_at
                """,
                doc.doc_id,
                doc.revision,
                content_sha256,
                updated_at,
            )
            if raw_chunks:
                await conn.execute(
                    """
                    DELETE FROM knowledge_embedding_checkpoint
                    WHERE doc_id = $1 AND content_sha256 = $2 AND embedding_fingerprint = $3
                    """,
                    doc.doc_id,
                    content_sha256,
                    embedding_fingerprint,
                )
            else:
                # 中文注释：空文档不生成检查点，成功发布后顺带清除该文档的旧临时向量。
                await conn.execute(
                    "DELETE FROM knowledge_embedding_checkpoint WHERE doc_id = $1", doc.doc_id
                )
    logger.info(
        "Knowledge vector publish completed doc_id=%s revision=%s chunks=%s reused_checkpoints=%s publish_ms=%s total_ms=%s",
        doc.doc_id,
        doc.revision,
        total_chunks,
        reused_checkpoint_count,
        int((time.perf_counter() - publish_started_at) * 1000),
        int((time.perf_counter() - started_at) * 1000),
    )
    return True


async def delete_knowledge_document(doc_id: str, revision: int) -> tuple[bool, int]:
    """写入版本墓碑并在同一事务中删除所有向量；旧版本不能越过墓碑。"""
    pool = get_pool()
    async with pool.acquire() as conn:
        async with conn.transaction():
            await conn.fetchval("SELECT pg_advisory_xact_lock($1::bigint)", int(doc_id))
            state = await conn.fetchrow(
                "SELECT revision, status, content_sha256 FROM knowledge_doc_state WHERE doc_id = $1 FOR UPDATE",
                doc_id,
            )
            if state and int(state["revision"]) > revision:
                return False, 0
            if state and int(state["revision"]) == revision:
                if state["status"] == "DELETED":
                    return False, 0
                raise ValueError("相同知识 revision 已用于其他索引操作")

            result = await conn.execute(
                "DELETE FROM knowledge_vector_chunk WHERE doc_id = $1", doc_id
            )
            try:
                deleted_count = int(result.split()[-1])
            except (ValueError, IndexError):
                deleted_count = 0
            # 中文注释：删除墓碑与全部临时检查点同事务提交，迟到的旧任务无法复活检查点。
            await conn.execute(
                "DELETE FROM knowledge_embedding_checkpoint WHERE doc_id = $1", doc_id
            )
            content_sha256 = state["content_sha256"] if state else "0" * 64
            await conn.execute(
                """
                INSERT INTO knowledge_doc_state (doc_id, revision, status, content_sha256, updated_at)
                VALUES ($1, $2, 'DELETED', $3, NOW())
                ON CONFLICT (doc_id) DO UPDATE
                SET revision = EXCLUDED.revision,
                    status = EXCLUDED.status,
                    content_sha256 = EXCLUDED.content_sha256,
                    updated_at = EXCLUDED.updated_at
                """,
                doc_id,
                revision,
                content_sha256,
            )
    return True, deleted_count


async def delete_knowledge_by_ids(doc_ids: list[str], revision: int) -> int:
    """兼容批量删除入口；每个删除版本必须来自 Java 权威文档状态。"""
    if revision < 1:
        raise ValueError("知识删除 revision 必须大于 0")
    deleted = 0
    for doc_id in dict.fromkeys(doc_ids):
        _applied, count = await delete_knowledge_document(doc_id, revision)
        deleted += count
    return deleted


async def similarity_search_knowledge(
    query: str, top_k: int, min_similarity: float | None = None
) -> list[RetrievedKnowledgeChunk]:
    """融合 READY 文档的向量候选与标题、标签、正文短语候选。"""
    query_embedding = await embed_text(query)
    pool = get_pool()
    settings = get_settings()
    escaped_query = _escape_like_fragment(query.strip().casefold())
    async with pool.acquire(timeout=settings.vector_pool_acquire_timeout_seconds) as conn:
        rows = await conn.fetch(
            """
            WITH ready_chunks AS NOT MATERIALIZED (
                SELECT chunk.doc_id, chunk.content, chunk.title, chunk.section_path, chunk.page_number,
                       chunk.chunk_index,
                       chunk.embedding <=> $1::vector AS distance,
                       (COALESCE(position(lower(chunk.title) in lower($3)) > 0, FALSE)
                        OR EXISTS (SELECT 1 FROM unnest(chunk.tags) AS tag_value(value)
                                   WHERE length(tag_value.value) > 0
                                     AND position(lower(tag_value.value) in lower($3)) > 0)) AS exact_match,
                       (char_length($5) >= 3
                        AND lower(chunk.content) LIKE '%' || $5 || '%' ESCAPE E'\\\\') AS content_match
                FROM knowledge_vector_chunk AS chunk
                JOIN knowledge_doc_state AS state ON state.doc_id = chunk.doc_id
                WHERE state.status = 'READY'
                  AND chunk.embedding IS NOT NULL
            ), vector_candidates AS (
                SELECT ready_chunks.*,
                       ROW_NUMBER() OVER (ORDER BY distance ASC, chunk_index ASC) AS vector_rank
                FROM ready_chunks
                WHERE $4::float IS NOT NULL AND 1 - distance >= $4
                ORDER BY distance ASC, chunk_index ASC
                LIMIT $6
            ), lexical_candidates AS (
                SELECT ready_chunks.*,
                       ROW_NUMBER() OVER (
                           ORDER BY exact_match DESC, content_match DESC, distance ASC, chunk_index ASC
                       ) AS lexical_rank
                FROM ready_chunks
                WHERE exact_match OR content_match
                ORDER BY exact_match DESC, content_match DESC, distance ASC, chunk_index ASC
                LIMIT $6
            ), candidate_ranks AS (
                SELECT doc_id, content, title, section_path, page_number, chunk_index,
                       distance, exact_match, content_match,
                       vector_rank, NULL::bigint AS lexical_rank
                FROM vector_candidates
                UNION ALL
                SELECT doc_id, content, title, section_path, page_number, chunk_index,
                       distance, exact_match, content_match,
                       NULL::bigint AS vector_rank, lexical_rank
                FROM lexical_candidates
            ), fused_candidates AS (
                SELECT doc_id, content, title, section_path, page_number, chunk_index,
                       MIN(distance) AS distance,
                       BOOL_OR(exact_match) AS exact_match,
                       BOOL_OR(content_match) AS content_match,
                       COALESCE(MAX(1.0 / (60 + vector_rank)), 0) AS vector_rank_score,
                       COALESCE(MAX(1.0 / (60 + lexical_rank)), 0) AS lexical_rank_score
                FROM candidate_ranks
                GROUP BY doc_id, content, title, section_path, page_number, chunk_index
            ), section_ranked AS (
                SELECT fused_candidates.*,
                       vector_rank_score + lexical_rank_score AS rank_score,
                       ROW_NUMBER() OVER (
                           PARTITION BY doc_id,
                               COALESCE(NULLIF(section_path, ''),
                                        CASE WHEN page_number IS NOT NULL
                                             THEN 'PDF 第 ' || page_number::text || ' 页' END,
                                        '文档')
                            ORDER BY exact_match DESC,
                                     (vector_rank_score + lexical_rank_score) DESC,
                                     distance ASC,
                                     chunk_index ASC
                       ) AS section_rank
                FROM fused_candidates
            )
            SELECT doc_id, content, title, section_path, page_number,
                   1 - distance AS score,
                   rank_score,
                   vector_rank_score,
                   lexical_rank_score,
                   exact_match,
                   content_match
            FROM section_ranked
            WHERE section_rank = 1
            ORDER BY exact_match DESC, rank_score DESC, distance ASC
            LIMIT $2
            """,
            vector_literal(query_embedding),
            top_k,
            query.strip(),
            min_similarity,
            escaped_query,
            max(20, top_k * 4),
        )

    results = [
        RetrievedKnowledgeChunk(
            doc_id=row["doc_id"],
            content=row["content"],
            score=float(row["score"] or 0),
            title=row["title"],
            section_path=row["section_path"] or "",
            page_number=row["page_number"],
            exact_match=bool(row["exact_match"]),
            content_match=bool(row["content_match"]),
            rank_score=float(row["rank_score"] or 0),
            vector_rank_score=float(row["vector_rank_score"] or 0),
            lexical_rank_score=float(row["lexical_rank_score"] or 0),
        )
        for row in rows
    ]
    return results


def _escape_like_fragment(value: str) -> str:
    """把用户查询中的 LIKE 通配符转义为普通字符。"""
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
