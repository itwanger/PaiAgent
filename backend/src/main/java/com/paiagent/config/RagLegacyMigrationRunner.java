package com.paiagent.config;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One-time, idempotent MySQL -> PostgreSQL RAG metadata migration. Embeddings are deliberately rebuilt. */
@Slf4j
@Component
@Order(2)
public class RagLegacyMigrationRunner implements ApplicationRunner {
    private static final String VERSION="mysql-rag-to-postgres-v1";
    private final JdbcTemplate mysql;
    private final JdbcTemplate postgres;
    private final RagProperties properties;

    public RagLegacyMigrationRunner(@Qualifier("jdbcTemplate") JdbcTemplate mysql,
                                    @Qualifier("ragJdbcTemplate") JdbcTemplate postgres,
                                    RagProperties properties) {
        this.mysql=mysql;this.postgres=postgres;this.properties=properties;
    }

    @Override public void run(ApplicationArguments args) {
        if(!properties.isMigrationEnabled())return;
        Integer applied=postgres.queryForObject("SELECT count(*) FROM rag_migration_history WHERE version=?",Integer.class,VERSION);
        if(applied!=null&&applied>0)return;
        Integer table=mysql.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema=database() AND table_name='knowledge_base'",Integer.class);
        if(table==null||table==0)return;
        List<Map<String,Object>> bases=mysql.queryForList("SELECT * FROM knowledge_base WHERE deleted=0 ORDER BY id");
        for(Map<String,Object> b:bases) postgres.update("""
                INSERT INTO rag_knowledge_base(id,name,description,config_id,embedding_model,chunk_size,chunk_overlap,status,
                  document_count,chunk_count,char_count,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,0,0,0,?,?) ON CONFLICT(id) DO NOTHING
                """,b.get("id"),b.get("name"),b.get("description"),b.get("config_id"),properties.getEmbedding().getModel(),
                value(b.get("chunk_size"),800),value(b.get("chunk_overlap"),100),"MIGRATING",b.get("created_at"),b.get("updated_at"));
        List<Map<String,Object>> docs=mysql.queryForList("""
                SELECT d.* FROM knowledge_document d JOIN knowledge_base b ON b.id=d.knowledge_base_id
                WHERE d.deleted=0 AND b.deleted=0 ORDER BY d.id
                """);
        int queued=0;
        for(Map<String,Object> d:docs){String text=String.valueOf(d.get("raw_text") == null ? "" : d.get("raw_text"));
            if(text.isBlank()||text.length()>properties.getLimits().getMaxTextChars()){log.warn("Skip legacy RAG document {} because text is empty or exceeds limit",d.get("id"));continue;}
            postgres.update("""
                    INSERT INTO rag_document(id,knowledge_base_id,title,source_type,source_url,file_name,detected_media_type,
                      cleaned_text,tags,status,char_count,error_message,created_at,updated_at)
                    VALUES (?,?,?,?,?,?,?, ?,?,'IMPORTED',?,?,?,?) ON CONFLICT(id) DO NOTHING
                    """,d.get("id"),d.get("knowledge_base_id"),d.get("title"),d.get("source_type"),d.get("source_url"),d.get("file_name"),
                    "text/plain",text,d.get("tags"),text.length(),null,d.get("created_at"),d.get("updated_at"));
            Long taskId=postgres.query("SELECT id FROM rag_index_task WHERE document_id=? ORDER BY id DESC LIMIT 1",
                    rs->rs.next()?rs.getLong(1):null,d.get("id"));
            if(taskId==null){String version=UUID.randomUUID().toString();taskId=postgres.queryForObject("""
                    INSERT INTO rag_index_task(knowledge_base_id,document_id,index_version,status) VALUES (?,?,?,'QUEUED') RETURNING id
                    """,Long.class,d.get("knowledge_base_id"),d.get("id"),version);
            }
            Integer outbox=postgres.queryForObject("SELECT count(*) FROM rag_outbox WHERE aggregate_id=? AND event_type='INDEX_DOCUMENT'",Integer.class,taskId);
            if(outbox!=null&&outbox==0){postgres.update("INSERT INTO rag_outbox(event_type,aggregate_id,payload) VALUES ('INDEX_DOCUMENT',?,jsonb_build_object('taskId',?))",taskId,taskId);queued++;}
        }
        postgres.execute("SELECT setval('rag_knowledge_base_id_seq',GREATEST((SELECT coalesce(max(id),0) FROM rag_knowledge_base),1),true)");
        postgres.execute("SELECT setval('rag_document_id_seq',GREATEST((SELECT coalesce(max(id),0) FROM rag_document),1),true)");
        for(Map<String,Object> b:bases) postgres.update("""
                UPDATE rag_knowledge_base SET document_count=(SELECT count(*) FROM rag_document WHERE knowledge_base_id=?),
                  char_count=(SELECT coalesce(sum(char_count),0) FROM rag_document WHERE knowledge_base_id=?),updated_at=now() WHERE id=?
                """,b.get("id"),b.get("id"),b.get("id"));
        postgres.update("INSERT INTO rag_migration_history(version,details) VALUES (?,?::jsonb)",VERSION,
                JSON.toJSONString(Map.of("bases",bases.size(),"documents",docs.size(),"queued",queued)));
        log.info("Legacy RAG metadata migrated to PostgreSQL: bases={}, documents={}, queued={}",bases.size(),docs.size(),queued);
    }
    private int value(Object value,int fallback){return value instanceof Number n?n.intValue():fallback;}
}
