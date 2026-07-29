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

-- 管理员知识库向量（与梗向量分离，避免污染 meme cite）
CREATE TABLE IF NOT EXISTS knowledge_vector_chunk (
    id              BIGSERIAL PRIMARY KEY,
    doc_id          VARCHAR(64) NOT NULL,
    chunk_index     INT NOT NULL DEFAULT 0,
    content         TEXT NOT NULL,
    content_hash    VARCHAR(64) NOT NULL,
    title           VARCHAR(200),
    category        VARCHAR(64),
    embedding       vector(1536),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_knowledge_vector_chunk UNIQUE (doc_id, chunk_index)
);

CREATE INDEX IF NOT EXISTS idx_knowledge_vector_chunk_doc_id ON knowledge_vector_chunk (doc_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_vector_chunk_hash ON knowledge_vector_chunk (doc_id, content_hash);

-- 数据量增大后可建 ANN 索引，例如：
-- CREATE INDEX idx_meme_vector_chunk_embedding_hnsw
--   ON meme_vector_chunk USING hnsw (embedding vector_cosine_ops);
