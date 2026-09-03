package com.paiagent.service.rag.benchmark;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in HNSW control-variable experiment.
 *
 * <p>The dataset, query vectors, SQL, Top-K and hardware stay fixed. The experiment changes only
 * {@code ef_search}, {@code m} and {@code ef_construction}. All measured searches are forced onto
 * HNSW for causal diagnosis; a separate natural EXPLAIN records whether PostgreSQL would choose
 * that plan in production.</p>
 */
class HnswRecallControlExperimentIT {
    private static final int DIMENSION = Integer.getInteger("rag.hnsw.dimension", 1024);
    private static final int VECTOR_COUNT = Integer.getInteger("rag.hnsw.vector-count", 50_000);
    private static final int QUERY_COUNT = Integer.getInteger("rag.hnsw.query-count", 100);
    private static final int TOP_K = Integer.getInteger("rag.hnsw.top-k", 10);
    private static final int WARMUP = Integer.getInteger("rag.hnsw.warmup", 50);
    private static final int MEASUREMENTS = Integer.getInteger("rag.hnsw.measurements", 100);
    private static final boolean CREATE_FILTER_INDEX = Boolean.parseBoolean(
            System.getProperty("rag.hnsw.create-filter-index", "true"));

    @Test
    void isolateHnswRecallAndLatencyParameters() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("rag.hnsw.control.enabled"),
                "Set -Drag.hnsw.control.enabled=true to run the HNSW control experiment");
        Class.forName("org.postgresql.Driver");

        Path output = Path.of(System.getProperty("rag.hnsw.output", "../benchmark/rag/results/hnsw-control"));
        Files.createDirectories(output);
        List<String> summary = new ArrayList<>();
        summary.add("vectorCount,dimension,queries,topK,m,efConstruction,efSearch,indexBuildMs," +
                "p50Ms,p95Ms,p99Ms,avgMs,recallAt5,recallAt10,naturalHnswUsed,forcedHnswUsed");

        try (Connection postgres = DriverManager.getConnection(property("rag.pg.url",
                     "jdbc:postgresql://localhost:5432/paiagent_vector"),
                     property("rag.pg.user", "paiagent"),
                     BenchmarkCredentials.require("rag.pg.password", "RAG_POSTGRES_PASSWORD"))) {
            postgres.setAutoCommit(true);
            configureBuild(postgres);
            Files.writeString(output.resolve("experiment-environment.txt"), environment(postgres),
                    StandardCharsets.UTF_8);
            createTable(postgres);
            seed(postgres);
            Map<Integer, List<Long>> exact = exactGroundTruth(postgres);
            assertEquals(QUERY_COUNT, exact.size());

            for (IndexConfig config : indexConfigs()) {
                long indexBuildMs = rebuildIndex(postgres, config);
                Files.writeString(output.resolve("index-m" + config.m() + "-efc" + config.efConstruction() + ".txt"),
                        indexDefinition(postgres), StandardCharsets.UTF_8);

                for (int efSearch : efSearchValues()) {
                    set(postgres, "hnsw.ef_search", efSearch);
                    String naturalExplain = explain(postgres, false);
                    String forcedExplain = explain(postgres, true);
                    boolean naturalHnsw = usesHnsw(naturalExplain);
                    boolean forcedHnsw = usesHnsw(forcedExplain);
                    assertTrue(forcedHnsw, "Forced plan did not use HNSW for " + config + ", ef=" + efSearch);

                    forceHnswPlanner(postgres);
                    try {
                        for (int i = 0; i < WARMUP; i++) query(postgres, i % QUERY_COUNT, TOP_K);
                        List<Long> samples = new ArrayList<>(MEASUREMENTS);
                        for (int i = 0; i < MEASUREMENTS; i++) {
                            long started = System.nanoTime();
                            query(postgres, i % QUERY_COUNT, TOP_K);
                            samples.add(System.nanoTime() - started);
                        }
                        double recall5 = recall(postgres, exact, 5);
                        double recall10 = recall(postgres, exact, 10);
                        Stats stats = Stats.of(samples);
                        summary.add(String.format(Locale.ROOT,
                                "%d,%d,%d,%d,%d,%d,%d,%d,%.4f,%.4f,%.4f,%.4f,%.6f,%.6f,%s,%s",
                                VECTOR_COUNT, DIMENSION, QUERY_COUNT, TOP_K, config.m(), config.efConstruction(),
                                efSearch, indexBuildMs, stats.p50(), stats.p95(), stats.p99(), stats.avg(),
                                recall5, recall10, naturalHnsw, forcedHnsw));
                        writeRaw(output, config, efSearch, samples);
                    } finally {
                        resetPlanner(postgres);
                    }

                    String suffix = "m" + config.m() + "-efc" + config.efConstruction() + "-efs" + efSearch;
                    Files.writeString(output.resolve("explain-natural-" + suffix + ".json"), naturalExplain,
                            StandardCharsets.UTF_8);
                    Files.writeString(output.resolve("explain-forced-" + suffix + ".json"), forcedExplain,
                            StandardCharsets.UTF_8);
                    Files.write(output.resolve("hnsw-control-results.csv"), summary, StandardCharsets.UTF_8);
                }
            }
        }
    }

    private void createTable(Connection postgres) throws SQLException {
        try (Statement statement = postgres.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS vector");
            statement.execute("DROP TABLE IF EXISTS rag_hnsw_control");
            statement.execute("CREATE TEMP TABLE rag_hnsw_control(" +
                    "id BIGINT PRIMARY KEY,content TEXT,metadata JSONB,embedding vector(" + DIMENSION + "))");
        }
    }

    private void seed(Connection postgres) throws SQLException {
        postgres.setAutoCommit(false);
        try (PreparedStatement statement = postgres.prepareStatement(
                "INSERT INTO rag_hnsw_control(id,content,metadata,embedding) VALUES(?,?,?::jsonb,?::vector)")) {
            for (int row = 0; row < VECTOR_COUNT; row++) {
                statement.setLong(1, row + 1L);
                statement.setString(2, DeterministicVectorDataset.content(row));
                statement.setString(3, DeterministicVectorDataset.metadata(row));
                statement.setString(4, DeterministicVectorDataset.vectorLiteral(
                        DeterministicVectorDataset.vector(row, DIMENSION)));
                statement.addBatch();
                if ((row + 1) % 250 == 0) statement.executeBatch();
            }
            statement.executeBatch();
            postgres.commit();
        } catch (Exception error) {
            postgres.rollback();
            throw error;
        } finally {
            postgres.setAutoCommit(true);
        }
        try (Statement statement = postgres.createStatement()) {
            // Every control row belongs to the same active knowledge base. A metadata B-tree has no
            // selectivity here and can mask HNSW by becoming a bitmap scan + full sort at high ef_search.
            if (CREATE_FILTER_INDEX) {
                statement.execute("CREATE INDEX idx_rag_hnsw_control_filter ON rag_hnsw_control " +
                        "((metadata->>'knowledgeBaseId'),(metadata->>'active'))");
            }
            statement.execute("ANALYZE rag_hnsw_control");
        }
    }

    private Map<Integer, List<Long>> exactGroundTruth(Connection postgres) throws SQLException {
        Map<Integer, List<Long>> exact = new LinkedHashMap<>();
        for (int queryIndex = 0; queryIndex < QUERY_COUNT; queryIndex++) {
            exact.put(queryIndex, query(postgres, queryIndex, TOP_K));
        }
        return exact;
    }

    private long rebuildIndex(Connection postgres, IndexConfig config) throws SQLException {
        try (Statement statement = postgres.createStatement()) {
            statement.execute("DROP INDEX IF EXISTS idx_rag_hnsw_control");
            long started = System.nanoTime();
            statement.execute("CREATE INDEX idx_rag_hnsw_control ON rag_hnsw_control USING hnsw " +
                    "(embedding vector_cosine_ops) WITH (m=" + config.m() +
                    ",ef_construction=" + config.efConstruction() + ")");
            long elapsed = System.nanoTime() - started;
            statement.execute("ANALYZE rag_hnsw_control");
            return Math.round(elapsed / 1_000_000d);
        }
    }

    private void configureBuild(Connection postgres) throws SQLException {
        String workMem = System.getProperty("rag.hnsw.maintenance-work-mem", "1GB");
        if (!workMem.matches("[0-9]+(MB|GB)")) throw new IllegalArgumentException("Invalid maintenance_work_mem");
        int workers = Integer.getInteger("rag.hnsw.parallel-maintenance-workers", 4);
        try (Statement statement = postgres.createStatement()) {
            statement.execute("SET maintenance_work_mem='" + workMem + "'");
            statement.execute("SET max_parallel_maintenance_workers=" + workers);
        }
    }

    private String environment(Connection postgres) throws SQLException {
        List<String> rows = new ArrayList<>();
        rows.add("timestamp=" + java.time.OffsetDateTime.now());
        rows.add("vectorCount=" + VECTOR_COUNT);
        rows.add("dimension=" + DIMENSION);
        rows.add("queryCount=" + QUERY_COUNT);
        rows.add("topK=" + TOP_K);
        rows.add("warmup=" + WARMUP);
        rows.add("measurements=" + MEASUREMENTS);
        rows.add("metadataFilterIndex=" + CREATE_FILTER_INDEX);
        try (Statement statement = postgres.createStatement()) {
            try (ResultSet result = statement.executeQuery("SHOW server_version")) {
                result.next(); rows.add("postgres=" + result.getString(1));
            }
            try (ResultSet result = statement.executeQuery("SHOW maintenance_work_mem")) {
                result.next(); rows.add("maintenanceWorkMem=" + result.getString(1));
            }
            try (ResultSet result = statement.executeQuery("SHOW max_parallel_maintenance_workers")) {
                result.next(); rows.add("parallelMaintenanceWorkers=" + result.getString(1));
            }
            try (ResultSet result = statement.executeQuery(
                    "SELECT extversion FROM pg_extension WHERE extname='vector'")) {
                if (result.next()) rows.add("pgvector=" + result.getString(1));
            }
        }
        return String.join(System.lineSeparator(), rows) + System.lineSeparator();
    }

    private List<Long> query(Connection postgres, int queryIndex, int topK) throws SQLException {
        String vector = DeterministicVectorDataset.vectorLiteral(DeterministicVectorDataset.vector(
                DeterministicVectorDataset.queryRow(queryIndex, VECTOR_COUNT), DIMENSION));
        List<Long> ids = new ArrayList<>(topK);
        try (PreparedStatement statement = postgres.prepareStatement("""
                SELECT id,content,metadata::text,1-(embedding <=> ?::vector) score
                FROM rag_hnsw_control
                WHERE metadata->>'knowledgeBaseId'='1' AND metadata->>'active'='true'
                  AND 1-(embedding <=> ?::vector) >= 0.0
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """)) {
            statement.setString(1, vector);
            statement.setString(2, vector);
            statement.setString(3, vector);
            statement.setInt(4, topK);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) ids.add(result.getLong(1));
            }
        }
        return ids;
    }

    private double recall(Connection postgres, Map<Integer, List<Long>> exact, int topK) throws SQLException {
        double sum = 0;
        for (Map.Entry<Integer, List<Long>> entry : exact.entrySet()) {
            List<Long> expected = entry.getValue().subList(0, topK);
            List<Long> actual = query(postgres, entry.getKey(), topK);
            sum += actual.stream().filter(expected::contains).count() / (double) topK;
        }
        return sum / exact.size();
    }

    private String explain(Connection postgres, boolean forceHnsw) throws SQLException {
        if (forceHnsw) forceHnswPlanner(postgres);
        String vector = DeterministicVectorDataset.vectorLiteral(DeterministicVectorDataset.vector(
                DeterministicVectorDataset.queryRow(17 % QUERY_COUNT, VECTOR_COUNT), DIMENSION));
        try (PreparedStatement statement = postgres.prepareStatement("""
                EXPLAIN (ANALYZE,BUFFERS,FORMAT JSON)
                SELECT id,content,metadata::text,1-(embedding <=> ?::vector) score
                FROM rag_hnsw_control
                WHERE metadata->>'knowledgeBaseId'='1' AND metadata->>'active'='true'
                  AND 1-(embedding <=> ?::vector) >= 0.0
                ORDER BY embedding <=> ?::vector
                LIMIT 10
                """)) {
            statement.setString(1, vector);
            statement.setString(2, vector);
            statement.setString(3, vector);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getString(1);
            }
        } finally {
            if (forceHnsw) resetPlanner(postgres);
        }
    }

    private String indexDefinition(Connection postgres) throws SQLException {
        try (PreparedStatement statement = postgres.prepareStatement(
                "SELECT indexdef FROM pg_indexes WHERE indexname='idx_rag_hnsw_control'");
             ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getString(1) : "MISSING";
        }
    }

    private void writeRaw(Path output, IndexConfig config, int efSearch, List<Long> samples) throws Exception {
        List<String> rows = new ArrayList<>(samples.size() + 1);
        rows.add("sample,queryIndex,latencyMs");
        for (int i = 0; i < samples.size(); i++) {
            rows.add(String.format(Locale.ROOT, "%d,%d,%.6f", i + 1, i % QUERY_COUNT,
                    samples.get(i) / 1_000_000d));
        }
        Files.write(output.resolve("raw-m" + config.m() + "-efc" + config.efConstruction() +
                "-efs" + efSearch + ".csv"), rows, StandardCharsets.UTF_8);
    }

    private static void set(Connection connection, String name, int value) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET " + name + "=" + value);
        }
    }

    private static void forceHnswPlanner(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET enable_seqscan=off");
            statement.execute("SET enable_bitmapscan=off");
            statement.execute("SET enable_sort=off");
        }
    }

    private static void resetPlanner(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("RESET enable_seqscan");
            statement.execute("RESET enable_bitmapscan");
            statement.execute("RESET enable_sort");
        }
    }

    private static List<Integer> efSearchValues() {
        return Arrays.stream(System.getProperty("rag.hnsw.ef-search-values", "40,80,120,200,400").split(","))
                .map(String::trim).map(Integer::parseInt).distinct().toList();
    }

    private static List<IndexConfig> indexConfigs() {
        return Arrays.stream(System.getProperty("rag.hnsw.index-configs",
                        "16:64,24:64,32:64,16:128,16:256").split(","))
                .map(String::trim)
                .map(value -> value.split(":"))
                .map(parts -> new IndexConfig(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])))
                .distinct().toList();
    }

    private static String property(String name, String fallback) {
        return System.getProperty(name, fallback);
    }

    private static boolean usesHnsw(String explainJson) {
        return explainJson.contains("\"Index Name\": \"idx_rag_hnsw_control\"");
    }

    private record IndexConfig(int m, int efConstruction) {}

    private record Stats(double p50, double p95, double p99, double avg) {
        static Stats of(List<Long> values) {
            long[] sorted = values.stream().mapToLong(Long::longValue).sorted().toArray();
            return new Stats(ms(percentile(sorted, .50)), ms(percentile(sorted, .95)),
                    ms(percentile(sorted, .99)), values.stream().mapToLong(Long::longValue)
                    .average().orElse(0) / 1_000_000d);
        }

        private static long percentile(long[] values, double percentile) {
            return values[Math.max(0, (int) Math.ceil(percentile * values.length) - 1)];
        }

        private static double ms(long nanos) {
            return nanos / 1_000_000d;
        }
    }
}
