package com.paiagent.service.rag.benchmark;

import com.paiagent.config.RagProperties;
import com.paiagent.service.rag.KnowledgeBaseVectorService;
import com.paiagent.service.rag.PgVectorStore;
import com.paiagent.service.rag.RagEmbeddingModel;
import com.paiagent.service.rag.RagRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/** PostgreSQL integration tests for the indexVersion + active invariants. */
class RagIndexReliabilityIT {
    private Fixture fixture;

    @BeforeEach
    void setUp() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("rag.it.enabled"),
                "Set -Drag.it.enabled=true to run PostgreSQL fault-injection tests");
        fixture = new Fixture();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (fixture != null) fixture.close();
    }

    @Test
    void normalBuildAtomicallyReplacesV1WithV2() {
        fixture.seedBuild("v2");
        fixture.stubSuccessfulEmbedding();

        fixture.service.processTask(1L);

        assertEquals(0, fixture.countVersion("v1"));
        assertEquals(100, fixture.countActiveVersion("v2"));
        assertEquals("v2", fixture.scalar("SELECT active_index_version FROM rag_document WHERE id=1", String.class));
        assertEquals("SUCCESS", fixture.scalar("SELECT status FROM rag_index_task WHERE id=1", String.class));
        assertEquals(Set.of("v2"), fixture.visibleVersions());
    }

    @Test
    void embeddingFailureAtChunk51KeepsV1Visible() {
        fixture.seedBuild("v2");
        AtomicInteger calls = new AtomicInteger();
        when(fixture.embedding.embed(anyList())).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 6) throw new IllegalStateException("permanent embedding failure at chunk 51");
            return vectors(invocation.getArgument(0, List.class).size());
        });

        fixture.service.processTask(1L);

        fixture.assertOnlyV1Visible();
        assertEquals(0, fixture.countVersion("v2"));
        assertEquals("FAILED", fixture.scalar("SELECT status FROM rag_index_task WHERE id=1", String.class));
        assertEquals("READY", fixture.scalar("SELECT status FROM rag_document WHERE id=1", String.class));
    }

    @Test
    void databaseFailureDuringBatchWriteKeepsV1Visible() {
        fixture.seedBuild("v2");
        fixture.stubSuccessfulEmbedding();
        fixture.jdbc.execute("""
                CREATE FUNCTION fail_chunk_51() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                  IF (NEW.metadata->>'chunkIndex')::int = 50 THEN
                    RAISE EXCEPTION 'injected write failure at chunk 51';
                  END IF;
                  RETURN NEW;
                END $$
                """);
        fixture.jdbc.execute("CREATE TRIGGER fail_chunk_51 BEFORE INSERT ON vector_store FOR EACH ROW EXECUTE FUNCTION fail_chunk_51()");

        fixture.service.processTask(1L);

        fixture.assertOnlyV1Visible();
        assertEquals(0, fixture.countVersion("v2"));
        assertEquals("FAILED", fixture.scalar("SELECT status FROM rag_index_task WHERE id=1", String.class));
    }

    @Test
    void activationFailureRollsBackDeletionOfV1() {
        fixture.seedBuild("v2");
        fixture.stubSuccessfulEmbedding();
        fixture.jdbc.execute("""
                CREATE FUNCTION fail_activation() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                  IF NEW.metadata->>'active' = 'true' AND OLD.metadata->>'active' = 'false' THEN
                    RAISE EXCEPTION 'injected activation failure';
                  END IF;
                  RETURN NEW;
                END $$
                """);
        fixture.jdbc.execute("CREATE TRIGGER fail_activation BEFORE UPDATE ON vector_store FOR EACH ROW EXECUTE FUNCTION fail_activation()");

        fixture.service.processTask(1L);

        fixture.assertOnlyV1Visible();
        assertEquals(0, fixture.countVersion("v2"));
        assertEquals("v1", fixture.scalar("SELECT active_index_version FROM rag_document WHERE id=1", String.class));
    }

    @Test
    void automaticRetryReusesVersionAndActivatesOnlyAfterSuccess() {
        fixture.seedBuild("v2");
        AtomicBoolean failOnce = new AtomicBoolean(true);
        when(fixture.embedding.embed(anyList())).thenAnswer(invocation -> {
            if (failOnce.getAndSet(false)) throw new IllegalStateException("timeout from injected embedding service");
            return vectors(invocation.getArgument(0, List.class).size());
        });

        fixture.service.processTask(1L);
        fixture.assertOnlyV1Visible();
        assertEquals("QUEUED", fixture.scalar("SELECT status FROM rag_index_task WHERE id=1", String.class));
        assertEquals("v2", fixture.scalar("SELECT index_version FROM rag_index_task WHERE id=1", String.class));

        fixture.service.processTask(1L);
        assertEquals(Set.of("v2"), fixture.visibleVersions());
        assertEquals("SUCCESS", fixture.scalar("SELECT status FROM rag_index_task WHERE id=1", String.class));
        assertEquals(1, fixture.scalar("SELECT retry_count FROM rag_index_task WHERE id=1", Integer.class));
    }

    @Test
    void concurrentQueriesObserveOneCompleteCommittedVersion() throws Exception {
        fixture.seedDocumentAndV1();
        fixture.insertVersion("v2", false, 10);
        fixture.stubSuccessfulEmbedding();
        ConcurrentLinkedQueue<Set<String>> observations = new ConcurrentLinkedQueue<>();
        AtomicBoolean running = new AtomicBoolean(true);
        CountDownLatch started = new CountDownLatch(1);

        Thread reader = Thread.ofPlatform().start(() -> {
            started.countDown();
            while (running.get()) observations.add(fixture.visibleVersions());
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));
        Thread.sleep(30);
        new TransactionTemplate(fixture.transactionManager).executeWithoutResult(status -> {
            fixture.store.activateVersion(1L, "v2");
            fixture.jdbc.update("UPDATE rag_document SET active_index_version='v2' WHERE id=1");
        });
        Thread.sleep(30);
        running.set(false);
        reader.join(5_000);

        assertFalse(observations.isEmpty());
        assertTrue(observations.stream().allMatch(value -> value.equals(Set.of("v1")) || value.equals(Set.of("v2"))),
                () -> "A query observed a mixed or empty version: " + observations);
        assertTrue(observations.contains(Set.of("v1")));
        assertTrue(observations.contains(Set.of("v2")));
    }

    @Test
    void concurrentBuildsMustNotLeaveDocumentPointerDifferentFromVisibleVersion() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("rag.concurrent-diagnostic.enabled"),
                "Known-gap diagnostic; enable explicitly to reproduce the concurrent rebuild race");
        fixture.seedBuild("v2");
        fixture.jdbc.update("INSERT INTO rag_index_task(id,knowledge_base_id,document_id,index_version,status) VALUES(2,1,1,'v3','QUEUED')");
        fixture.stubSuccessfulEmbedding();
        CountDownLatch bothAtCutover = new CountDownLatch(2);
        doAnswer(invocation -> {
            bothAtCutover.countDown();
            if (!bothAtCutover.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("second build did not reach cutover");
            return invocation.callRealMethod();
        }).when(fixture.store).activateVersion(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.anyString());

        Thread v2 = Thread.ofPlatform().start(() -> fixture.service.processTask(1L));
        Thread v3 = Thread.ofPlatform().start(() -> fixture.service.processTask(2L));
        v2.join(20_000);
        v3.join(20_000);
        assertFalse(v2.isAlive() || v3.isAlive(), "concurrent builds did not finish");

        Set<String> visible = fixture.visibleVersions();
        String pointer = fixture.scalar("SELECT active_index_version FROM rag_document WHERE id=1", String.class);
        assertEquals(1, visible.size(), () -> "expected exactly one visible version, got " + visible);
        assertEquals(Set.of(pointer), visible,
                () -> "document pointer and visible vector version diverged; tasks="
                        + fixture.jdbc.queryForList("SELECT id,index_version,status FROM rag_index_task ORDER BY id"));
    }

    private static List<float[]> vectors(int count) {
        List<float[]> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) values.add(new float[]{1f, 0f, 0f});
        return values;
    }

    private static final class Fixture implements AutoCloseable {
        private final String baseUrl = System.getProperty("rag.pg.url", "jdbc:postgresql://localhost:5432/paiagent_vector");
        private final String user = System.getProperty("rag.pg.user", "paiagent");
        private final String password = BenchmarkCredentials.require("rag.pg.password", "RAG_POSTGRES_PASSWORD");
        private final String schema = "rag_it_" + UUID.randomUUID().toString().replace("-", "");
        private final DataSource dataSource;
        private final JdbcTemplate jdbc;
        private final DataSourceTransactionManager transactionManager;
        private final RagEmbeddingModel embedding = mock(RagEmbeddingModel.class);
        private final PgVectorStore store;
        private final RagRepository repository;
        private final KnowledgeBaseVectorService service;

        private Fixture() throws Exception {
            Class.forName("org.postgresql.Driver");
            try (Connection connection = DriverManager.getConnection(baseUrl, user, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA " + schema);
            }
            DriverManagerDataSource ds = new DriverManagerDataSource(withSchema(baseUrl, schema), user, password);
            this.dataSource = ds;
            this.jdbc = new JdbcTemplate(ds);
            this.transactionManager = new DataSourceTransactionManager(ds);
            createSchema();
            this.repository = new RagRepository(jdbc);
            this.store = spy(new PgVectorStore(jdbc, embedding));
            RagProperties properties = new RagProperties();
            properties.getEmbedding().setBatchSize(10);
            properties.getLimits().setMaxChunks(500);
            KnowledgeBaseVectorService real = new KnowledgeBaseVectorService(repository, store, properties, transactionManager);
            this.service = spy(real);
            List<Document> chunks = new ArrayList<>();
            for (int i = 0; i < 100; i++) chunks.add(new Document("chunk " + i));
            doReturn(chunks).when(service).split(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
            when(embedding.embed(org.mockito.ArgumentMatchers.anyString())).thenReturn(new float[]{1f, 0f, 0f});
        }

        private void createSchema() {
            jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
            jdbc.execute("""
                    CREATE TABLE rag_knowledge_base(
                      id BIGINT PRIMARY KEY,name VARCHAR(100),description VARCHAR(500),config_id BIGINT,
                      embedding_model VARCHAR(100),chunk_size INTEGER,chunk_overlap INTEGER,status VARCHAR(30),
                      document_count INTEGER DEFAULT 0,chunk_count INTEGER DEFAULT 0,char_count BIGINT DEFAULT 0,
                      created_at TIMESTAMPTZ DEFAULT now(),updated_at TIMESTAMPTZ DEFAULT now())
                    """);
            jdbc.execute("""
                    CREATE TABLE rag_document(
                      id BIGINT PRIMARY KEY,knowledge_base_id BIGINT,title VARCHAR(255),source_type VARCHAR(30),
                      source_url VARCHAR(1024),file_name VARCHAR(255),storage_key VARCHAR(512),detected_media_type VARCHAR(255),
                      file_hash CHAR(64),cleaned_text TEXT,tags TEXT,status VARCHAR(30),active_index_version VARCHAR(64),
                      char_count BIGINT,error_message TEXT,created_at TIMESTAMPTZ DEFAULT now(),updated_at TIMESTAMPTZ DEFAULT now())
                    """);
            jdbc.execute("""
                    CREATE TABLE rag_index_task(
                      id BIGINT PRIMARY KEY,knowledge_base_id BIGINT,document_id BIGINT,index_version VARCHAR(64),
                      status VARCHAR(30),progress INTEGER DEFAULT 0,total_chunks INTEGER DEFAULT 0,finished_chunks INTEGER DEFAULT 0,
                      retry_count INTEGER DEFAULT 0,lease_until TIMESTAMPTZ,error_message TEXT,
                      created_at TIMESTAMPTZ DEFAULT now(),updated_at TIMESTAMPTZ DEFAULT now())
                    """);
            jdbc.execute("CREATE SEQUENCE rag_chunk_id_seq START 1000");
            jdbc.execute("CREATE TABLE vector_store(id BIGINT PRIMARY KEY,content TEXT,metadata JSONB,embedding vector(3),created_at TIMESTAMPTZ DEFAULT now())");
            jdbc.execute("""
                    CREATE TABLE rag_outbox(id BIGSERIAL PRIMARY KEY,event_type VARCHAR(50),aggregate_id BIGINT,payload JSONB,
                    status VARCHAR(20) DEFAULT 'PENDING',attempts INTEGER DEFAULT 0,next_attempt_at TIMESTAMPTZ DEFAULT now(),
                    published_at TIMESTAMPTZ,last_error TEXT,created_at TIMESTAMPTZ DEFAULT now())
                    """);
        }

        private void seedBuild(String version) {
            seedDocumentAndV1();
            jdbc.update("INSERT INTO rag_index_task(id,knowledge_base_id,document_id,index_version,status) VALUES(1,1,1,?,'QUEUED')", version);
        }

        private void seedDocumentAndV1() {
            jdbc.update("INSERT INTO rag_knowledge_base(id,name,embedding_model,chunk_size,chunk_overlap,status) VALUES(1,'kb','model',800,100,'READY')");
            jdbc.update("INSERT INTO rag_document(id,knowledge_base_id,title,source_type,cleaned_text,status,active_index_version,char_count) VALUES(1,1,'doc','TEXT','content','READY','v1',7)");
            insertVersion("v1", true, 10);
        }

        private void insertVersion(String version, boolean active, int chunks) {
            for (int i = 0; i < chunks; i++) jdbc.update("""
                    INSERT INTO vector_store(id,content,metadata,embedding)
                    VALUES(nextval('rag_chunk_id_seq'),?,jsonb_build_object(
                      'knowledgeBaseId','1','documentId','1','chunkIndex',?,'indexVersion',?,'active',?), '[1,0,0]'::vector)
                    """, version + "-chunk-" + i, i, version, active);
        }

        private void stubSuccessfulEmbedding() {
            when(embedding.embed(anyList())).thenAnswer(invocation -> vectors(invocation.getArgument(0, List.class).size()));
        }

        private Set<String> visibleVersions() {
            return Set.copyOf(jdbc.queryForList("SELECT DISTINCT metadata->>'indexVersion' FROM vector_store WHERE metadata->>'active'='true'", String.class));
        }

        private int countVersion(String version) {
            return jdbc.queryForObject("SELECT count(*) FROM vector_store WHERE metadata->>'indexVersion'=?", Integer.class, version);
        }

        private int countActiveVersion(String version) {
            return jdbc.queryForObject("SELECT count(*) FROM vector_store WHERE metadata->>'indexVersion'=? AND metadata->>'active'='true'", Integer.class, version);
        }

        private void assertOnlyV1Visible() {
            assertEquals(Set.of("v1"), visibleVersions());
            assertEquals("v1", scalar("SELECT active_index_version FROM rag_document WHERE id=1", String.class));
        }

        private <T> T scalar(String sql, Class<T> type) { return jdbc.queryForObject(sql, type); }

        private static String withSchema(String url, String schema) {
            return url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema + ",public";
        }

        @Override
        public void close() throws Exception {
            try (Connection connection = DriverManager.getConnection(baseUrl, user, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }
}
