-- MemeAgent 向量库初始化（独立 PostgreSQL，非业务 MySQL）
-- 中文注释：手动执行前应将维度设为 VECTOR_EMBEDDING_DIM；可使用 psql -v vector_embedding_dim=1024 覆盖默认值。
\if :{?vector_embedding_dim}
\else
\set vector_embedding_dim 1024
\endif

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE IF NOT EXISTS meme_vector_chunk (
    id              BIGSERIAL PRIMARY KEY,
    meme_id         VARCHAR(64) NOT NULL,
    chunk_index     INT NOT NULL DEFAULT 0,
    content         TEXT NOT NULL,
    content_hash    VARCHAR(64) NOT NULL,
    embedding       vector(:vector_embedding_dim),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_meme_vector_chunk UNIQUE (meme_id, chunk_index)
);

-- 为每个梗向量表字段补充中文说明，便于数据库管理工具查看结构。
COMMENT ON COLUMN meme_vector_chunk.id IS '向量片段主键';
COMMENT ON COLUMN meme_vector_chunk.meme_id IS '对应业务梗 ID';
COMMENT ON COLUMN meme_vector_chunk.chunk_index IS '同一梗内片段序号';
COMMENT ON COLUMN meme_vector_chunk.content IS '用于检索和引用的文本片段';
COMMENT ON COLUMN meme_vector_chunk.content_hash IS '梗文档内容哈希，用于跳过未变更内容';
COMMENT ON COLUMN meme_vector_chunk.embedding IS '文本片段的向量表示';
COMMENT ON COLUMN meme_vector_chunk.updated_at IS '向量片段最后更新时间';

CREATE INDEX IF NOT EXISTS idx_meme_vector_chunk_meme_id ON meme_vector_chunk (meme_id);
CREATE INDEX IF NOT EXISTS idx_meme_vector_chunk_hash ON meme_vector_chunk (meme_id, content_hash);
-- 使用余弦距离 HNSW 加速梗内容近似向量检索。
-- m=16 控制图连接数；ef_construction=64 控制建图候选范围与索引构建成本。
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_meme_vector_chunk_embedding_hnsw
    ON meme_vector_chunk USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- 管理员知识库向量（与梗向量分离，避免污染 meme cite）
CREATE TABLE IF NOT EXISTS knowledge_vector_chunk (
    id              BIGSERIAL PRIMARY KEY,
    doc_id          VARCHAR(64) NOT NULL,
    chunk_index     INT NOT NULL DEFAULT 0,
    content         TEXT NOT NULL,
    content_hash    VARCHAR(64) NOT NULL,
    title           VARCHAR(200),
    category        VARCHAR(64),
    embedding       vector(:vector_embedding_dim),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_knowledge_vector_chunk UNIQUE (doc_id, chunk_index)
);

-- 为每个运营知识向量字段补充中文说明。
COMMENT ON COLUMN knowledge_vector_chunk.id IS '知识向量片段主键';
COMMENT ON COLUMN knowledge_vector_chunk.doc_id IS '运营知识文档 ID';
COMMENT ON COLUMN knowledge_vector_chunk.chunk_index IS '同一文档内片段序号';
COMMENT ON COLUMN knowledge_vector_chunk.content IS '用于检索和引用的知识片段';
COMMENT ON COLUMN knowledge_vector_chunk.content_hash IS '知识文档内容哈希，用于跳过未变更内容';
COMMENT ON COLUMN knowledge_vector_chunk.title IS '知识文档标题';
COMMENT ON COLUMN knowledge_vector_chunk.category IS '知识文档分类';
COMMENT ON COLUMN knowledge_vector_chunk.embedding IS '知识片段的向量表示';
COMMENT ON COLUMN knowledge_vector_chunk.updated_at IS '知识向量片段最后更新时间';

CREATE INDEX IF NOT EXISTS idx_knowledge_vector_chunk_doc_id ON knowledge_vector_chunk (doc_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_vector_chunk_hash ON knowledge_vector_chunk (doc_id, content_hash);
-- 使用 trigram GIN 索引加速知识正文的完整短语候选检索。
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_knowledge_vector_chunk_content_trgm
    ON knowledge_vector_chunk USING gin (lower(content) gin_trgm_ops);

-- 管理员知识 embedding 分批检查点；只保存未发布到正式知识索引的临时向量。
CREATE TABLE IF NOT EXISTS knowledge_embedding_checkpoint (
    doc_id                  VARCHAR(64) NOT NULL,
    content_sha256          CHAR(64) NOT NULL,
    embedding_fingerprint   CHAR(64) NOT NULL,
    chunk_index             INT NOT NULL,
    chunk_sha256            CHAR(64) NOT NULL,
    embedding               vector(:vector_embedding_dim) NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_knowledge_embedding_checkpoint PRIMARY KEY
        (doc_id, content_sha256, embedding_fingerprint, chunk_index)
);

COMMENT ON COLUMN knowledge_embedding_checkpoint.doc_id IS '管理员知识文档 ID';
COMMENT ON COLUMN knowledge_embedding_checkpoint.content_sha256 IS '标准化正文 SHA-256 指纹';
COMMENT ON COLUMN knowledge_embedding_checkpoint.embedding_fingerprint IS 'embedding endpoint、模型和维度指纹';
COMMENT ON COLUMN knowledge_embedding_checkpoint.chunk_index IS '文档内片段序号';
COMMENT ON COLUMN knowledge_embedding_checkpoint.chunk_sha256 IS '片段文本 SHA-256 指纹';
COMMENT ON COLUMN knowledge_embedding_checkpoint.embedding IS '已成功生成并保存的片段向量检查点';
COMMENT ON COLUMN knowledge_embedding_checkpoint.created_at IS '检查点首次保存时间，用于过期清理';
COMMENT ON COLUMN knowledge_embedding_checkpoint.updated_at IS '检查点最后确认时间';

CREATE INDEX IF NOT EXISTS idx_knowledge_embedding_checkpoint_created_at
    ON knowledge_embedding_checkpoint (created_at);
-- 使用余弦距离 HNSW 加速运营知识库近似向量检索。
-- m=16 控制图连接数；ef_construction=64 控制建图候选范围与索引构建成本。
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_knowledge_vector_chunk_embedding_hnsw
    ON knowledge_vector_chunk USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
