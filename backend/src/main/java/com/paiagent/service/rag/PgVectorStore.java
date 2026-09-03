package com.paiagent.service.rag;

import com.alibaba.fastjson2.JSON;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class PgVectorStore implements VectorStore {
    private final JdbcTemplate jdbc;
    private final EmbeddingModel embeddingModel;

    public PgVectorStore(@Qualifier("ragJdbcTemplate") JdbcTemplate jdbc, RagEmbeddingModel embeddingModel) {
        this.jdbc = jdbc;
        this.embeddingModel = embeddingModel;
    }

    public long nextChunkId() {
        Long value = jdbc.queryForObject("SELECT nextval('rag_chunk_id_seq')", Long.class);
        if (value == null) throw new IllegalStateException("无法生成 Chunk ID");
        return value;
    }

    @Override
    public void add(List<Document> documents) {
        if (documents == null || documents.isEmpty()) return;
        List<float[]> vectors = embeddingModel.embed(documents.stream().map(Document::getText).toList());
        if (vectors.size() != documents.size()) throw new IllegalStateException("Embedding 数量不匹配");
        List<Object[]> args = new ArrayList<>(documents.size());
        for (int i = 0; i < documents.size(); i++) {
            Document document = documents.get(i);
            args.add(new Object[]{Long.parseLong(document.getId()), document.getText(),
                    JSON.toJSONString(document.getMetadata()), vectorLiteral(vectors.get(i))});
        }
        jdbc.batchUpdate("""
                INSERT INTO vector_store(id, content, metadata, embedding)
                VALUES (?, ?, ?::jsonb, ?::vector)
                ON CONFLICT (id) DO UPDATE SET content=EXCLUDED.content,
                    metadata=EXCLUDED.metadata, embedding=EXCLUDED.embedding
                """, args);
    }

    @Override
    public Optional<Boolean> delete(List<String> ids) {
        if (ids == null || ids.isEmpty()) return Optional.of(true);
        int changed = jdbc.update("DELETE FROM vector_store WHERE id = ANY (?::bigint[])",
                "{" + String.join(",", ids) + "}");
        return Optional.of(changed >= 0);
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        throw new IllegalArgumentException("RAG 检索必须显式指定 knowledgeBaseId");
    }

    public List<Document> similaritySearch(long knowledgeBaseId, SearchRequest request) {
        float[] queryVector = embeddingModel.embed(request.getQuery());
        return jdbc.query("""
                SELECT id, content, metadata::text,
                       1 - (embedding <=> ?::vector) AS score
                FROM vector_store
                WHERE metadata->>'knowledgeBaseId' = ?
                  AND metadata->>'active' = 'true'
                  AND 1 - (embedding <=> ?::vector) >= ?
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """, (rs, rowNum) -> Document.builder()
                        .id(String.valueOf(rs.getLong("id")))
                        .text(rs.getString("content"))
                        .metadata(JSON.parseObject(rs.getString("metadata"), Map.class))
                        .score(rs.getDouble("score"))
                        .build(), vectorLiteral(queryVector), String.valueOf(knowledgeBaseId),
                vectorLiteral(queryVector), request.getSimilarityThreshold(),
                vectorLiteral(queryVector), request.getTopK());
    }

    public void activateVersion(long documentId, String indexVersion) {
        jdbc.update("DELETE FROM vector_store WHERE metadata->>'documentId' = ? AND metadata->>'indexVersion' <> ?",
                String.valueOf(documentId), indexVersion);
        jdbc.update("""
                UPDATE vector_store
                SET metadata = jsonb_set(metadata, '{active}', 'true'::jsonb)
                WHERE metadata->>'documentId' = ? AND metadata->>'indexVersion' = ?
                """, String.valueOf(documentId), indexVersion);
    }

    public void deleteVersion(long documentId, String indexVersion) {
        jdbc.update("DELETE FROM vector_store WHERE metadata->>'documentId' = ? AND metadata->>'indexVersion' = ?",
                String.valueOf(documentId), indexVersion);
    }

    public void deleteKnowledgeBase(long knowledgeBaseId) {
        jdbc.update("DELETE FROM vector_store WHERE metadata->>'knowledgeBaseId' = ?", String.valueOf(knowledgeBaseId));
    }

    private String vectorLiteral(float[] vector) {
        StringBuilder text = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) text.append(',');
            text.append(Float.toString(vector[i]));
        }
        return text.append(']').toString();
    }
}
