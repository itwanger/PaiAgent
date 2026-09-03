CREATE EXTENSION IF NOT EXISTS vector;

CREATE SEQUENCE IF NOT EXISTS rag_knowledge_base_id_seq;
CREATE SEQUENCE IF NOT EXISTS rag_document_id_seq;
CREATE SEQUENCE IF NOT EXISTS rag_index_task_id_seq;
CREATE SEQUENCE IF NOT EXISTS rag_chunk_id_seq;
CREATE SEQUENCE IF NOT EXISTS rag_outbox_id_seq;

CREATE TABLE IF NOT EXISTS rag_knowledge_base (
    id BIGINT PRIMARY KEY DEFAULT nextval('rag_knowledge_base_id_seq'),
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    config_id BIGINT,
    embedding_model VARCHAR(100) NOT NULL,
    chunk_size INTEGER NOT NULL DEFAULT 800,
    chunk_overlap INTEGER NOT NULL DEFAULT 100,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    document_count INTEGER NOT NULL DEFAULT 0,
    chunk_count INTEGER NOT NULL DEFAULT 0,
    char_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rag_document (
    id BIGINT PRIMARY KEY DEFAULT nextval('rag_document_id_seq'),
    knowledge_base_id BIGINT NOT NULL REFERENCES rag_knowledge_base(id) ON DELETE CASCADE,
    title VARCHAR(255), source_type VARCHAR(30) NOT NULL DEFAULT 'TEXT',
    source_url VARCHAR(1024), file_name VARCHAR(255), storage_key VARCHAR(512),
    detected_media_type VARCHAR(255), file_hash CHAR(64), cleaned_text TEXT NOT NULL,
    tags TEXT, status VARCHAR(30) NOT NULL DEFAULT 'IMPORTED',
    active_index_version VARCHAR(64), char_count BIGINT NOT NULL DEFAULT 0,
    error_message TEXT, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (knowledge_base_id, file_hash)
);
CREATE INDEX IF NOT EXISTS idx_rag_document_base ON rag_document(knowledge_base_id);
CREATE INDEX IF NOT EXISTS idx_rag_document_status ON rag_document(status);

CREATE TABLE IF NOT EXISTS rag_index_task (
    id BIGINT PRIMARY KEY DEFAULT nextval('rag_index_task_id_seq'),
    knowledge_base_id BIGINT NOT NULL REFERENCES rag_knowledge_base(id) ON DELETE CASCADE,
    document_id BIGINT NOT NULL REFERENCES rag_document(id) ON DELETE CASCADE,
    index_version VARCHAR(64) NOT NULL, status VARCHAR(30) NOT NULL DEFAULT 'QUEUED',
    progress INTEGER NOT NULL DEFAULT 0, total_chunks INTEGER NOT NULL DEFAULT 0,
    finished_chunks INTEGER NOT NULL DEFAULT 0, retry_count INTEGER NOT NULL DEFAULT 0,
    lease_until TIMESTAMPTZ, error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(document_id, index_version)
);
CREATE INDEX IF NOT EXISTS idx_rag_task_base ON rag_index_task(knowledge_base_id);
CREATE INDEX IF NOT EXISTS idx_rag_task_status ON rag_index_task(status, updated_at);

CREATE TABLE IF NOT EXISTS vector_store (
    id BIGINT PRIMARY KEY DEFAULT nextval('rag_chunk_id_seq'),
    content TEXT NOT NULL, metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    embedding vector(1024) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_vector_store_base_active
    ON vector_store ((metadata->>'knowledgeBaseId'), (metadata->>'active'));
CREATE INDEX IF NOT EXISTS idx_vector_store_document ON vector_store ((metadata->>'documentId'));
CREATE INDEX IF NOT EXISTS idx_vector_store_version ON vector_store ((metadata->>'indexVersion'));
CREATE INDEX IF NOT EXISTS idx_vector_store_hnsw ON vector_store USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

CREATE TABLE IF NOT EXISTS rag_outbox (
    id BIGINT PRIMARY KEY DEFAULT nextval('rag_outbox_id_seq'),
    event_type VARCHAR(50) NOT NULL, aggregate_id BIGINT NOT NULL, payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ, last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_rag_outbox_pending ON rag_outbox(status, next_attempt_at);

CREATE TABLE IF NOT EXISTS rag_migration_history (
    version VARCHAR(100) PRIMARY KEY,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    details JSONB NOT NULL DEFAULT '{}'::jsonb
);
