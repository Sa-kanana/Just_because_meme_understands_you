"""梗向量容量诊断脚本的只读性与输出字段契约。"""

import re
from pathlib import Path


def test_capacity_sql_is_read_only_and_documents_every_output_field() -> None:
    """中文注释：确认容量 SQL 只读取系统目录和向量表，并解释所有输出项。"""
    script_path = Path(__file__).parents[1] / "scripts" / "inspect_meme_vector_capacity.sql"
    sql = script_path.read_text(encoding="utf-8")

    required_fragments = (
        "COUNT(DISTINCT meme_id)",
        "COUNT(*)",
        "MAX(chunk_count)",
        "AVG(chunk_count)",
        "pg_relation_size",
        "pg_indexes_size",
        "pg_total_relation_size",
        "pg_stat_user_tables",
        "n_dead_tup",
        "last_autovacuum",
        "pg_am",
        "hnsw",
    )
    normalized_sql = sql.lower()
    for fragment in required_fragments:
        assert fragment.lower() in normalized_sql, f"容量脚本缺少指标或 catalog 查询：{fragment}"

    documented_fields = {
        "meme_count": "梗记录数量",
        "chunk_count": "向量片段数量",
        "max_chunks_per_meme": "单梗最大分块数",
        "avg_chunks_per_meme": "单梗平均分块数",
        "table_bytes": "向量表本体大小",
        "index_bytes": "向量表所有索引占用的总空间",
        "total_bytes": "向量表、索引及 TOAST 等关联存储占用的总空间",
        "hnsw_index_bytes": "通过 PostgreSQL 索引目录识别的 HNSW 索引总空间",
        "dead_tuple_estimate": "PostgreSQL 统计估算的待清理死元组数量",
        "last_autovacuum": "该表最近一次自动清理的时间",
    }
    for field, explanation in documented_fields.items():
        assert f"-- {field}：{explanation}" in sql, f"输出字段缺少中文说明：{field}"
        assert re.search(rf"\bAS\s+{field}\b", sql, flags=re.IGNORECASE), f"输出字段不存在：{field}"

    mutating_statement = re.compile(
        r"\b(?:INSERT|UPDATE|DELETE|ALTER|DROP|TRUNCATE|CREATE|VACUUM|REINDEX)\b",
        flags=re.IGNORECASE,
    )
    assert mutating_statement.search(sql) is None, "容量诊断脚本必须保持只读"
