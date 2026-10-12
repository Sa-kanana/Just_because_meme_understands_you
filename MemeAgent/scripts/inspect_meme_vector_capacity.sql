-- 中文注释：只读检查 meme_vector_chunk 的行数、分块、表索引空间和 PostgreSQL 清理统计。
WITH per_meme AS (
    SELECT
        -- 中文注释：被统计梗的业务 ID。
        meme_id,
        -- 中文注释：当前梗在向量表中的片段数量。
        COUNT(*)::bigint AS chunk_count
    FROM meme_vector_chunk
    GROUP BY meme_id
),
capacity AS (
    SELECT
        -- meme_count：梗记录数量，按不同 meme_id 计数。
        (SELECT COUNT(DISTINCT meme_id)::bigint FROM meme_vector_chunk) AS meme_count,
        -- chunk_count：向量片段数量，即 meme_vector_chunk 当前总行数。
        (SELECT COUNT(*)::bigint FROM meme_vector_chunk) AS chunk_count,
        -- max_chunks_per_meme：单梗最大分块数，用于发现异常大文档。
        COALESCE((SELECT MAX(chunk_count) FROM per_meme), 0)::bigint AS max_chunks_per_meme,
        -- avg_chunks_per_meme：单梗平均分块数，保留两位小数。
        COALESCE((SELECT AVG(chunk_count)::numeric(12, 2) FROM per_meme), 0)::numeric(12, 2)
            AS avg_chunks_per_meme,
        -- table_bytes：向量表本体大小，不包含索引。
        pg_relation_size(stats.relid)::bigint AS table_bytes,
        -- index_bytes：向量表所有索引占用的总空间。
        pg_indexes_size(stats.relid)::bigint AS index_bytes,
        -- total_bytes：向量表、索引及 TOAST 等关联存储占用的总空间。
        pg_total_relation_size(stats.relid)::bigint AS total_bytes,
        -- hnsw_index_bytes：通过 PostgreSQL 索引目录识别的 HNSW 索引总空间。
        COALESCE((
            SELECT SUM(pg_relation_size(index_class.oid))::bigint
            FROM pg_index AS index_info
            JOIN pg_class AS index_class ON index_class.oid = index_info.indexrelid
            JOIN pg_am AS access_method ON access_method.oid = index_class.relam
            WHERE index_info.indrelid = stats.relid
              AND access_method.amname = 'hnsw'
        ), 0)::bigint AS hnsw_index_bytes,
        -- dead_tuple_estimate：PostgreSQL 统计估算的待清理死元组数量。
        stats.n_dead_tup::bigint AS dead_tuple_estimate,
        -- last_autovacuum：该表最近一次自动清理的时间；未执行时为空。
        stats.last_autovacuum AS last_autovacuum
    FROM pg_stat_user_tables AS stats
    WHERE stats.relid = to_regclass('meme_vector_chunk')
)
SELECT
    -- meme_count：梗记录数量，按不同 meme_id 计数。
    meme_count,
    -- chunk_count：向量片段数量，即 meme_vector_chunk 当前总行数。
    chunk_count,
    -- max_chunks_per_meme：单梗最大分块数，用于发现异常大文档。
    max_chunks_per_meme,
    -- avg_chunks_per_meme：单梗平均分块数，保留两位小数。
    avg_chunks_per_meme,
    -- table_bytes：向量表本体大小，不包含索引。
    table_bytes,
    -- index_bytes：向量表所有索引占用的总空间。
    index_bytes,
    -- total_bytes：向量表、索引及 TOAST 等关联存储占用的总空间。
    total_bytes,
    -- hnsw_index_bytes：通过 PostgreSQL 索引目录识别的 HNSW 索引总空间。
    hnsw_index_bytes,
    -- dead_tuple_estimate：PostgreSQL 统计估算的待清理死元组数量。
    dead_tuple_estimate,
    -- last_autovacuum：该表最近一次自动清理的时间；未执行时为空。
    last_autovacuum
FROM capacity;
