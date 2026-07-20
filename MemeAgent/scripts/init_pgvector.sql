-- MemeAgent 向量库初始化（独立 PostgreSQL，非业务 MySQL）
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS meme_vector_chunk (
    id              BIGSERIAL PRIMARY KEY,
    meme_id         VARCHAR(64) NOT NULL,
    chunk_index     INT NOT NULL DEFAULT 0,
    content         TEXT NOT NULL,
    content_hash    VARCHAR(64) NOT NULL,
    embedding       vector(1536),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_meme_vector_chunk UNIQUE (meme_id, chunk_index)
);

CREATE INDEX IF NOT EXISTS idx_meme_vector_chunk_meme_id ON meme_vector_chunk (meme_id);
CREATE INDEX IF NOT EXISTS idx_meme_vector_chunk_hash ON meme_vector_chunk (meme_id, content_hash);

-- 数据量增大后可建 ANN 索引，例如：
-- CREATE INDEX idx_meme_vector_chunk_embedding_hnsw
--   ON meme_vector_chunk USING hnsw (embedding vector_cosine_ops);
