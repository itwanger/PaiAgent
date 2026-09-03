package com.paiagent.service.rag;

import com.paiagent.entity.KnowledgeBase;
import com.paiagent.entity.KnowledgeDocument;
import com.paiagent.entity.KnowledgeIndexTask;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class RagRepository {
    private final JdbcTemplate jdbc;

    public RagRepository(@Qualifier("ragJdbcTemplate") JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private final RowMapper<KnowledgeBase> baseMapper = (rs, n) -> {
        KnowledgeBase v = new KnowledgeBase();
        v.setId(rs.getLong("id")); v.setName(rs.getString("name")); v.setDescription(rs.getString("description"));
        v.setConfigId((Long) rs.getObject("config_id")); v.setEmbeddingModel(rs.getString("embedding_model"));
        v.setChunkSize(rs.getInt("chunk_size")); v.setChunkOverlap(rs.getInt("chunk_overlap"));
        v.setStatus(rs.getString("status")); v.setDocumentCount(rs.getInt("document_count"));
        v.setChunkCount(rs.getInt("chunk_count")); v.setCharCount(rs.getLong("char_count"));
        v.setCreatedAt(local(rs.getTimestamp("created_at"))); v.setUpdatedAt(local(rs.getTimestamp("updated_at")));
        return v;
    };
    private final RowMapper<KnowledgeDocument> documentMapper = (rs, n) -> {
        KnowledgeDocument v = new KnowledgeDocument();
        v.setId(rs.getLong("id")); v.setKnowledgeBaseId(rs.getLong("knowledge_base_id"));
        v.setTitle(rs.getString("title")); v.setSourceType(rs.getString("source_type"));
        v.setSourceUrl(rs.getString("source_url")); v.setFileName(rs.getString("file_name"));
        v.setRawText(rs.getString("cleaned_text")); v.setTags(rs.getString("tags"));
        v.setStatus(rs.getString("status")); v.setCharCount(rs.getLong("char_count"));
        v.setErrorMessage(rs.getString("error_message")); v.setCreatedAt(local(rs.getTimestamp("created_at")));
        v.setUpdatedAt(local(rs.getTimestamp("updated_at"))); return v;
    };
    private final RowMapper<KnowledgeIndexTask> taskMapper = (rs, n) -> {
        KnowledgeIndexTask v = new KnowledgeIndexTask();
        v.setId(rs.getLong("id")); v.setKnowledgeBaseId(rs.getLong("knowledge_base_id"));
        v.setDocumentId(rs.getLong("document_id")); v.setStatus(rs.getString("status"));
        v.setProgress(rs.getInt("progress")); v.setTotalChunks(rs.getInt("total_chunks"));
        v.setFinishedChunks(rs.getInt("finished_chunks")); v.setErrorMessage(rs.getString("error_message"));
        v.setCreatedAt(local(rs.getTimestamp("created_at"))); v.setUpdatedAt(local(rs.getTimestamp("updated_at")));
        return v;
    };

    public List<KnowledgeBase> listBases() { return jdbc.query("SELECT * FROM rag_knowledge_base ORDER BY updated_at DESC", baseMapper); }
    public KnowledgeBase findBase(long id) { return one("SELECT * FROM rag_knowledge_base WHERE id=?", baseMapper, id); }
    public KnowledgeBase findBaseByName(String name) { return one("SELECT * FROM rag_knowledge_base WHERE name=? LIMIT 1", baseMapper, name); }
    public long insertBase(KnowledgeBase v) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(c -> { PreparedStatement ps = c.prepareStatement("""
                INSERT INTO rag_knowledge_base(name,description,config_id,embedding_model,chunk_size,chunk_overlap,status)
                VALUES (?,?,?,?,?,?,?)
                """, Statement.RETURN_GENERATED_KEYS); ps.setString(1,v.getName()); ps.setString(2,v.getDescription());
            if(v.getConfigId()==null) ps.setNull(3,java.sql.Types.BIGINT); else ps.setLong(3,v.getConfigId());
            ps.setString(4,v.getEmbeddingModel()); ps.setInt(5,v.getChunkSize()); ps.setInt(6,v.getChunkOverlap());
            ps.setString(7,v.getStatus()); return ps; }, key);
        return key.getKey().longValue();
    }
    public void deleteBase(long id) { jdbc.update("DELETE FROM rag_knowledge_base WHERE id=?", id); }

    public List<KnowledgeDocument> listDocuments(long baseId) { return jdbc.query("SELECT * FROM rag_document WHERE knowledge_base_id=? ORDER BY updated_at DESC", documentMapper, baseId); }
    public KnowledgeDocument findDocument(long id) { return one("SELECT * FROM rag_document WHERE id=?", documentMapper, id); }
    public KnowledgeDocument findDocumentByHash(long baseId, String hash) { return one("SELECT * FROM rag_document WHERE knowledge_base_id=? AND file_hash=?", documentMapper, baseId, hash); }
    public long insertDocument(long baseId, String title, String sourceType, String sourceUrl, String fileName,
                               String storageKey, String mediaType, String hash, String text, String tags) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(c -> { PreparedStatement ps = c.prepareStatement("""
                INSERT INTO rag_document(knowledge_base_id,title,source_type,source_url,file_name,storage_key,
                  detected_media_type,file_hash,cleaned_text,tags,status,char_count)
                VALUES (?,?,?,?,?,?,?,?,?,?,'IMPORTED',?)
                """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1,baseId); ps.setString(2,title); ps.setString(3,sourceType); ps.setString(4,sourceUrl);
            ps.setString(5,fileName); ps.setString(6,storageKey); ps.setString(7,mediaType); ps.setString(8,hash);
            ps.setString(9,text); ps.setString(10,tags); ps.setLong(11,text.length()); return ps; }, key);
        return key.getKey().longValue();
    }
    public void updateDocumentStatus(long id, String status, String error) {
        jdbc.update("UPDATE rag_document SET status=?,error_message=?,updated_at=now() WHERE id=?", status, trimError(error), id);
    }

    public long createTask(long baseId, long documentId, String version) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(c -> { PreparedStatement ps=c.prepareStatement("""
                INSERT INTO rag_index_task(knowledge_base_id,document_id,index_version,status)
                VALUES (?,?,?,'QUEUED')
                """, Statement.RETURN_GENERATED_KEYS); ps.setLong(1,baseId); ps.setLong(2,documentId);
            ps.setString(3,version); return ps; }, key); return key.getKey().longValue();
    }
    public KnowledgeIndexTask findTask(long id) { return one("SELECT * FROM rag_index_task WHERE id=?", taskMapper, id); }
    public String taskVersion(long id) { return jdbc.queryForObject("SELECT index_version FROM rag_index_task WHERE id=?", String.class, id); }
    public int taskRetryCount(long id) { Integer value=jdbc.queryForObject("SELECT retry_count FROM rag_index_task WHERE id=?",Integer.class,id);return value==null?0:value; }
    public List<KnowledgeIndexTask> recentTasks(long baseId) { return jdbc.query("SELECT * FROM rag_index_task WHERE knowledge_base_id=? ORDER BY updated_at DESC LIMIT 10", taskMapper, baseId); }
    public boolean claimTask(long id) { return jdbc.update("""
            UPDATE rag_index_task SET status='RUNNING',lease_until=now()+interval '10 minutes',updated_at=now()
            WHERE id=? AND (status='QUEUED' OR (status='RUNNING' AND lease_until<now()))
            """, id) == 1; }
    public void updateTaskProgress(long id, int total, int finished) { jdbc.update("""
            UPDATE rag_index_task SET total_chunks=?,finished_chunks=?,progress=?,lease_until=now()+interval '10 minutes',updated_at=now() WHERE id=?
            """, total, finished, total==0?100:(int)Math.round(finished*100.0/total), id); }
    public void completeTask(long id, long documentId, String version) {
        jdbc.update("UPDATE rag_index_task SET status='SUCCESS',progress=100,lease_until=NULL,error_message=NULL,updated_at=now() WHERE id=?",id);
        jdbc.update("UPDATE rag_document SET status='READY',active_index_version=?,error_message=NULL,updated_at=now() WHERE id=?",version,documentId);
    }
    public void failTask(long id, long documentId, String error) {
        jdbc.update("UPDATE rag_index_task SET status='FAILED',lease_until=NULL,error_message=?,updated_at=now() WHERE id=?",trimError(error),id);
        jdbc.update("UPDATE rag_document SET status=CASE WHEN active_index_version IS NULL THEN 'FAILED' ELSE 'READY' END,error_message=?,updated_at=now() WHERE id=?",trimError(error),documentId);
    }
    public void retryTask(long id,long documentId,String error) {
        jdbc.update("UPDATE rag_index_task SET status='QUEUED',retry_count=retry_count+1,lease_until=NULL,error_message=?,updated_at=now() WHERE id=?",trimError(error),id);
        jdbc.update("UPDATE rag_document SET status='QUEUED',error_message=?,updated_at=now() WHERE id=?",trimError(error),documentId);
        addOutbox(id);
    }

    public void addOutbox(long taskId) { jdbc.update("""
            INSERT INTO rag_outbox(event_type,aggregate_id,payload) VALUES ('INDEX_DOCUMENT',?,jsonb_build_object('taskId',?))
            """,taskId,taskId); }
    public List<Long> pendingOutboxIds() { return jdbc.queryForList("SELECT id FROM rag_outbox WHERE status='PENDING' AND next_attempt_at<=now() ORDER BY id LIMIT 20",Long.class); }
    public long outboxTaskId(long outboxId) { return jdbc.queryForObject("SELECT aggregate_id FROM rag_outbox WHERE id=?",Long.class,outboxId); }
    public void markOutboxPublished(long id) {
        jdbc.update("UPDATE rag_outbox SET status='PUBLISHED',published_at=now() WHERE id=?",id);
        jdbc.update("UPDATE rag_index_task SET updated_at=now() WHERE id=(SELECT aggregate_id FROM rag_outbox WHERE id=?)",id);
    }
    public void markOutboxFailed(long id,String error) { jdbc.update("""
            UPDATE rag_outbox SET attempts=attempts+1,last_error=?,next_attempt_at=now()+interval '10 seconds' WHERE id=?
            """,trimError(error),id); }
    public void recoverStalledTasks() {
        List<Long> expired=jdbc.queryForList("""
                UPDATE rag_index_task SET status='QUEUED',lease_until=NULL,updated_at=now()
                WHERE status='RUNNING' AND lease_until<now() RETURNING id
                """,Long.class);
        List<Long> stalled=jdbc.queryForList("""
                SELECT id FROM rag_index_task t WHERE status='QUEUED' AND updated_at<now()-interval '30 seconds'
                  AND NOT EXISTS(SELECT 1 FROM rag_outbox o WHERE o.aggregate_id=t.id AND o.status='PENDING')
                LIMIT 20
                """,Long.class);
        for(Long id:java.util.stream.Stream.concat(expired.stream(),stalled.stream()).distinct().toList()) {
            addOutbox(id); jdbc.update("UPDATE rag_index_task SET updated_at=now() WHERE id=?",id);
        }
    }

    public void refreshBaseStats(long baseId) { jdbc.update("""
            UPDATE rag_knowledge_base b SET
              document_count=(SELECT count(*) FROM rag_document d WHERE d.knowledge_base_id=b.id),
              chunk_count=(SELECT count(*) FROM vector_store v WHERE v.metadata->>'knowledgeBaseId'=b.id::text AND v.metadata->>'active'='true'),
              char_count=(SELECT coalesce(sum(d.char_count),0) FROM rag_document d WHERE d.knowledge_base_id=b.id),
              status=CASE WHEN EXISTS(SELECT 1 FROM rag_document d WHERE d.knowledge_base_id=b.id AND d.status='READY') THEN 'READY' ELSE b.status END,
              updated_at=now() WHERE b.id=?
            """,baseId); }

    private <T> T one(String sql, RowMapper<T> mapper, Object... args) { List<T> values=jdbc.query(sql,mapper,args); return values.isEmpty()?null:values.getFirst(); }
    private LocalDateTime local(Timestamp value) { return value == null ? null : value.toLocalDateTime(); }
    private String trimError(String value) { return value==null?null:value.substring(0,Math.min(1000,value.length())); }
}
