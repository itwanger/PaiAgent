package com.paiagent.service.rag.benchmark;

import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.management.ManagementFactory;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Opt-in database benchmark. Run only through benchmark/rag/run-benchmark.ps1.
 * It uses connection-scoped temporary tables and never touches production tables.
 */
class RagRetrievalBenchmarkIT {
    private static final int DIMENSION = Integer.getInteger("rag.benchmark.dimension", 1024);
    private static final int QUERY_COUNT = Integer.getInteger("rag.benchmark.query-count", 100);
    private static final int WARMUP = Integer.getInteger("rag.benchmark.warmup", 100);
    private static final int COLD_QUERIES = Integer.getInteger("rag.benchmark.cold-queries", 20);

    @Test
    void compareMySqlJvmWithPgVectorHnsw() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("rag.benchmark.enabled"),
                "Set -Drag.benchmark.enabled=true to run the database benchmark");
        Class.forName("com.mysql.cj.jdbc.Driver");
        Class.forName("org.postgresql.Driver");

        Path output = Path.of(System.getProperty("rag.benchmark.output", "../benchmark/rag/results"));
        Files.createDirectories(output);
        List<String> csv = new ArrayList<>();
        csv.add("vectorCount,phase,scheme,samples,p50Ms,p95Ms,p99Ms,avgMs,peakHeapBytes,avgAllocatedBytes,gcCount,gcTimeMs,avgReturnedLogicalBytes,avgJvmVectors,recallAt5,recallAt10,hnswUsed");

        try (Connection mysql = DriverManager.getConnection(property("rag.mysql.url",
                     "jdbc:mysql://localhost:3306/paiagent?useSSL=false&allowPublicKeyRetrieval=true"),
                     property("rag.mysql.user", "root"),
                     BenchmarkCredentials.require("rag.mysql.password", "MYSQL_PASSWORD"));
             Connection postgres = DriverManager.getConnection(property("rag.pg.url",
                     "jdbc:postgresql://localhost:5432/paiagent_vector"),
                     property("rag.pg.user", "paiagent"),
                     BenchmarkCredentials.require("rag.pg.password", "RAG_POSTGRES_PASSWORD"))) {
            mysql.setAutoCommit(true);
            postgres.setAutoCommit(true);
            createTables(mysql, postgres);
            for (int vectorCount : scales()) {
                seed(mysql, postgres, vectorCount);
                runScale(mysql, postgres, vectorCount, output, csv);
                Files.write(output.resolve("retrieval-results.csv"), csv, StandardCharsets.UTF_8);
            }
        }
        Files.write(output.resolve("retrieval-results.csv"), csv, StandardCharsets.UTF_8);
    }

    private void runScale(Connection mysql, Connection postgres, int count, Path output, List<String> csv)
            throws Exception {
        int measurements = Integer.getInteger("rag.benchmark.measurements", defaultMeasurements(count));
        Map<Integer, List<Long>> exactTop10 = new LinkedHashMap<>();
        boolean skipMySql = Boolean.getBoolean("rag.benchmark.skip-mysql");
        if (skipMySql) {
            exactTop10.putAll(bruteForceGroundTruth(count));
            System.gc();
            Thread.sleep(200);
        } else {
            RunResult mysqlCold = runQueries(mysql, count, COLD_QUERIES, 10, true, exactTop10);
            for (int i = 0; i < WARMUP; i++) queryMySql(mysql, count, i % QUERY_COUNT, 10);
            RunResult mysqlStable = runQueries(mysql, count, measurements, 10, true, exactTop10);
            writeRawSamples(output, count, "mysql-jvm", mysqlStable);
            csv.add(row(count, "cold", "MySQL + JVM", mysqlCold, Double.NaN, Double.NaN, false));
            csv.add(row(count, "stable", "MySQL + JVM", mysqlStable, Double.NaN, Double.NaN, false));
        }
        Files.writeString(output.resolve("indexes-" + count + ".txt"), indexDefinitions(postgres), StandardCharsets.UTF_8);
        for (int efSearch : efSearchValues()) {
            try (Statement setting = postgres.createStatement()) { setting.execute("SET hnsw.ef_search=" + efSearch); }
            RunResult pgCold = runQueries(postgres, count, COLD_QUERIES, 10, false, null);
            int pgWarmup = Integer.getInteger("rag.benchmark.pg-warmup", WARMUP);
            for (int i = 0; i < pgWarmup; i++) queryPostgres(postgres, count, i % QUERY_COUNT, 10);
            RunResult pgStable = runQueries(postgres, count, measurements, 10, false, null);
            writeRawSamples(output, count, "pgvector-ef" + efSearch, pgStable);

            double recall5 = recall(postgres, count, exactTop10, 5);
            double recall10 = recall(postgres, count, exactTop10, 10);
            String explain = explain(postgres, count, false);
            String forcedExplain = explain(postgres, count, true);
            boolean hnsw = explain.contains("idx_rag_bench_hnsw");
            Files.writeString(output.resolve("explain-" + count + "-ef" + efSearch + ".json"), explain, StandardCharsets.UTF_8);
            Files.writeString(output.resolve("explain-forced-hnsw-" + count + "-ef" + efSearch + ".json"), forcedExplain, StandardCharsets.UTF_8);
            csv.add(row(count, "cold", "pgvector + HNSW ef=" + efSearch, pgCold, Double.NaN, Double.NaN, hnsw));
            csv.add(row(count, "stable", "pgvector + HNSW ef=" + efSearch, pgStable, recall5, recall10, hnsw));
        }
    }

    private RunResult runQueries(Connection connection, int count, int samples, int topK,
                                 boolean mysql, Map<Integer, List<Long>> captured) throws Exception {
        List<Long> latencies = new ArrayList<>(samples);
        long bytes = 0;
        long vectors = 0;
        com.sun.management.ThreadMXBean threadBean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (threadBean.isThreadAllocatedMemorySupported() && !threadBean.isThreadAllocatedMemoryEnabled()) {
            threadBean.setThreadAllocatedMemoryEnabled(true);
        }
        long allocatedBefore = threadBean.isThreadAllocatedMemorySupported()
                ? threadBean.getThreadAllocatedBytes(Thread.currentThread().threadId()) : -1;
        long gcCountBefore = gcCount();
        long gcTimeBefore = gcTime();
        PeakHeapSampler heap = new PeakHeapSampler();
        heap.start();
        try {
            for (int i = 0; i < samples; i++) {
                int queryIndex = i % QUERY_COUNT;
                Observation observation = mysql
                        ? queryMySql(connection, count, queryIndex, topK)
                        : queryPostgres(connection, count, queryIndex, topK);
                latencies.add(observation.nanos());
                bytes += observation.logicalBytes();
                vectors += observation.jvmVectors();
                if (captured != null && !captured.containsKey(queryIndex)) {
                    captured.put(queryIndex, observation.ids());
                }
            }
        } finally {
            heap.close();
        }
        long allocatedAfter = threadBean.isThreadAllocatedMemorySupported()
                ? threadBean.getThreadAllocatedBytes(Thread.currentThread().threadId()) : -1;
        long averageAllocated = allocatedBefore < 0 || allocatedAfter < 0 ? -1 : (allocatedAfter - allocatedBefore) / samples;
        return new RunResult(latencies, heap.peak(), averageAllocated, gcCount() - gcCountBefore,
                gcTime() - gcTimeBefore, bytes / samples, vectors / samples);
    }

    private Observation queryMySql(Connection connection, int count, int queryIndex, int topK) throws SQLException {
        float[] query = DeterministicVectorDataset.vector(
                DeterministicVectorDataset.queryRow(queryIndex, count), DIMENSION);
        long started = System.nanoTime();
        List<ScoredId> scores = new ArrayList<>(count);
        long bytes = 0;
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT id,content,metadata,embedding FROM rag_bench_vector")) {
            while (rs.next()) {
                String content = rs.getString(2);
                String metadata = rs.getString(3);
                String embedding = rs.getString(4);
                List<Double> parsed = JSON.parseArray(embedding, Double.class);
                scores.add(new ScoredId(rs.getLong(1), cosine(query, parsed)));
                bytes += utf8(content) + utf8(metadata) + utf8(embedding) + Long.BYTES;
            }
        }
        scores.sort(Comparator.comparingDouble(ScoredId::score).reversed().thenComparingLong(ScoredId::id));
        List<Long> ids = scores.stream().limit(topK).map(ScoredId::id).toList();
        return new Observation(System.nanoTime() - started, ids, bytes, scores.size());
    }

    private Observation queryPostgres(Connection connection, int count, int queryIndex, int topK) throws SQLException {
        String query = DeterministicVectorDataset.vectorLiteral(DeterministicVectorDataset.vector(
                DeterministicVectorDataset.queryRow(queryIndex, count), DIMENSION));
        long started = System.nanoTime();
        List<Long> ids = new ArrayList<>(topK);
        long bytes = 0;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id,content,metadata::text,1-(embedding <=> ?::vector) score
                FROM rag_bench_vector
                WHERE metadata->>'knowledgeBaseId'='1' AND metadata->>'active'='true'
                  AND 1-(embedding <=> ?::vector) >= 0.0
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """)) {
            statement.setString(1, query);
            statement.setString(2, query);
            statement.setString(3, query);
            statement.setInt(4, topK);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                    bytes += Long.BYTES + utf8(rs.getString(2)) + utf8(rs.getString(3)) + Double.BYTES;
                }
            }
        }
        return new Observation(System.nanoTime() - started, ids, bytes, 0);
    }

    private double recall(Connection postgres, int count, Map<Integer, List<Long>> exact, int topK)
            throws SQLException {
        double total = 0;
        for (Map.Entry<Integer, List<Long>> entry : exact.entrySet()) {
            int query = entry.getKey();
            List<Long> expected = entry.getValue().subList(0, topK);
            List<Long> actual = queryPostgres(postgres, count, query, topK).ids();
            long overlap = actual.stream().filter(expected::contains).count();
            total += overlap / (double) topK;
        }
        return total / exact.size();
    }

    private Map<Integer, List<Long>> bruteForceGroundTruth(int count) {
        float[][] vectors = new float[count][];
        for (int row = 0; row < count; row++) vectors[row] = DeterministicVectorDataset.vector(row, DIMENSION);
        Map<Integer, List<Long>> result = new LinkedHashMap<>();
        for (int queryIndex = 0; queryIndex < QUERY_COUNT; queryIndex++) {
            float[] query = vectors[DeterministicVectorDataset.queryRow(queryIndex, count)];
            PriorityQueue<ScoredId> top = new PriorityQueue<>(Comparator.comparingDouble(ScoredId::score));
            for (int row = 0; row < count; row++) {
                double score = cosine(query, vectors[row]);
                ScoredId candidate = new ScoredId(row + 1L, score);
                if (top.size() < 10) top.add(candidate);
                else if (score > top.peek().score()) { top.poll(); top.add(candidate); }
            }
            result.put(queryIndex, top.stream()
                    .sorted(Comparator.comparingDouble(ScoredId::score).reversed().thenComparingLong(ScoredId::id))
                    .map(ScoredId::id).toList());
        }
        return result;
    }

    private void createTables(Connection mysql, Connection postgres) throws SQLException {
        try (Statement s = mysql.createStatement()) {
            s.execute("DROP TEMPORARY TABLE IF EXISTS rag_bench_vector");
            s.execute("CREATE TEMPORARY TABLE rag_bench_vector(id BIGINT PRIMARY KEY,content TEXT,metadata JSON,embedding JSON)");
        }
        try (Statement s = postgres.createStatement()) {
            s.execute("CREATE EXTENSION IF NOT EXISTS vector");
            s.execute("DROP TABLE IF EXISTS rag_bench_vector");
            s.execute("CREATE TEMP TABLE rag_bench_vector(id BIGINT PRIMARY KEY,content TEXT,metadata JSONB,embedding vector(" + DIMENSION + "))");
        }
    }

    private void seed(Connection mysql, Connection postgres, int count) throws SQLException {
        try (Statement m = mysql.createStatement(); Statement p = postgres.createStatement()) {
            m.execute("TRUNCATE TABLE rag_bench_vector");
            p.execute("TRUNCATE TABLE rag_bench_vector");
        }
        mysql.setAutoCommit(false);
        postgres.setAutoCommit(false);
        try (PreparedStatement m = mysql.prepareStatement(
                     "INSERT INTO rag_bench_vector(id,content,metadata,embedding) VALUES(?,?,?,?)");
             PreparedStatement p = postgres.prepareStatement(
                     "INSERT INTO rag_bench_vector(id,content,metadata,embedding) VALUES(?,?,?::jsonb,?::vector)")) {
            for (int i = 0; i < count; i++) {
                String content = DeterministicVectorDataset.content(i);
                String metadata = DeterministicVectorDataset.metadata(i);
                String vector = DeterministicVectorDataset.vectorLiteral(
                        DeterministicVectorDataset.vector(i, DIMENSION));
                m.setLong(1, i + 1L); m.setString(2, content); m.setString(3, metadata); m.setString(4, vector); m.addBatch();
                p.setLong(1, i + 1L); p.setString(2, content); p.setString(3, metadata); p.setString(4, vector); p.addBatch();
                if ((i + 1) % 250 == 0) { m.executeBatch(); p.executeBatch(); }
            }
            m.executeBatch(); p.executeBatch();
            mysql.commit(); postgres.commit();
        } catch (Exception error) {
            mysql.rollback(); postgres.rollback(); throw error;
        } finally {
            mysql.setAutoCommit(true); postgres.setAutoCommit(true);
        }
        try (Statement p = postgres.createStatement()) {
            p.execute("DROP INDEX IF EXISTS idx_rag_bench_hnsw");
            p.execute("DROP INDEX IF EXISTS idx_rag_bench_filter");
            p.execute("CREATE INDEX idx_rag_bench_filter ON rag_bench_vector ((metadata->>'knowledgeBaseId'),(metadata->>'active'))");
            p.execute("CREATE INDEX idx_rag_bench_hnsw ON rag_bench_vector USING hnsw (embedding vector_cosine_ops) WITH (m=16,ef_construction=64)");
            p.execute("ANALYZE rag_bench_vector");
        }
    }

    private String explain(Connection postgres, int count, boolean disableSeqScan) throws SQLException {
        String vector = DeterministicVectorDataset.vectorLiteral(DeterministicVectorDataset.vector(17 % count, DIMENSION));
        if (disableSeqScan) {
            try (Statement setting = postgres.createStatement()) { setting.execute("SET enable_seqscan=off"); }
        }
        try (PreparedStatement statement = postgres.prepareStatement("""
                EXPLAIN (ANALYZE,BUFFERS,FORMAT JSON)
                SELECT id,content,metadata::text,1-(embedding <=> ?::vector) score FROM rag_bench_vector
                WHERE metadata->>'knowledgeBaseId'='1' AND metadata->>'active'='true'
                  AND 1-(embedding <=> ?::vector) >= 0.0
                ORDER BY embedding <=> ?::vector LIMIT 10
                """)) {
            statement.setString(1, vector);
            statement.setString(2, vector);
            statement.setString(3, vector);
            try (ResultSet rs = statement.executeQuery()) { rs.next(); return rs.getString(1); }
        } finally {
            if (disableSeqScan) {
                try (Statement setting = postgres.createStatement()) { setting.execute("RESET enable_seqscan"); }
            }
        }
    }

    private String indexDefinitions(Connection postgres) throws SQLException {
        StringBuilder value = new StringBuilder();
        try (PreparedStatement statement = postgres.prepareStatement("""
                SELECT indexname,indexdef FROM pg_indexes
                WHERE tablename='rag_bench_vector' ORDER BY indexname
                """); ResultSet rs = statement.executeQuery()) {
            while (rs.next()) value.append(rs.getString(1)).append('=').append(rs.getString(2)).append(System.lineSeparator());
        }
        return value.toString();
    }

    private String row(int count, String phase, String scheme, RunResult result,
                       double recall5, double recall10, boolean hnsw) {
        Stats stats = Stats.of(result.latencies());
        return String.format(Locale.ROOT, "%d,%s,%s,%d,%.4f,%.4f,%.4f,%.4f,%d,%d,%d,%d,%d,%d,%s,%s,%s",
                count, phase, scheme, result.latencies().size(), stats.p50(), stats.p95(), stats.p99(), stats.avg(),
                result.peakHeap(), result.avgAllocatedBytes(), result.gcCount(), result.gcTimeMs(),
                result.avgBytes(), result.avgVectors(), number(recall5), number(recall10), hnsw);
    }

    private void writeRawSamples(Path output, int count, String scheme, RunResult result) throws IOException {
        List<String> rows = new ArrayList<>(result.latencies().size() + 1);
        rows.add("sample,queryIndex,latencyMs");
        for (int i = 0; i < result.latencies().size(); i++) {
            rows.add(String.format(Locale.ROOT, "%d,%d,%.6f", i + 1, i % QUERY_COUNT,
                    result.latencies().get(i) / 1_000_000d));
        }
        Files.write(output.resolve("raw-" + count + "-" + scheme + ".csv"), rows, StandardCharsets.UTF_8);
    }

    private static long gcCount() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(bean -> Math.max(0, bean.getCollectionCount())).sum();
    }

    private static long gcTime() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(bean -> Math.max(0, bean.getCollectionTime())).sum();
    }

    private static String number(double value) { return Double.isNaN(value) ? "" : String.format(Locale.ROOT, "%.6f", value); }
    private static String property(String name, String fallback) { return System.getProperty(name, fallback); }
    private static long utf8(String text) { return text == null ? 0 : text.getBytes(StandardCharsets.UTF_8).length; }
    private static int defaultMeasurements(int count) { return count <= 1_000 ? 1_000 : count <= 10_000 ? 300 : 100; }
    private static List<Integer> scales() {
        return Arrays.stream(System.getProperty("rag.benchmark.scales", "1000,10000,50000").split(","))
                .map(String::trim).map(Integer::parseInt).toList();
    }
    private static List<Integer> efSearchValues() {
        String fallback = System.getProperty("rag.benchmark.ef-search", "40");
        return Arrays.stream(System.getProperty("rag.benchmark.ef-search-values", fallback).split(","))
                .map(String::trim).map(Integer::parseInt).distinct().toList();
    }
    private static double cosine(float[] left, List<Double> right) {
        double dot = 0, leftNorm = 0, rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            double r = right.get(i); dot += left[i] * r; leftNorm += left[i] * left[i]; rightNorm += r * r;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
    private static double cosine(float[] left, float[] right) {
        double dot = 0, leftNorm = 0, rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i]; leftNorm += left[i] * left[i]; rightNorm += right[i] * right[i];
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private record ScoredId(long id, double score) {}
    private record Observation(long nanos, List<Long> ids, long logicalBytes, long jvmVectors) {}
    private record RunResult(List<Long> latencies, long peakHeap, long avgAllocatedBytes,
                             long gcCount, long gcTimeMs, long avgBytes, long avgVectors) {}
    private record Stats(double p50, double p95, double p99, double avg) {
        static Stats of(List<Long> values) {
            long[] sorted = values.stream().mapToLong(Long::longValue).sorted().toArray();
            return new Stats(ms(percentile(sorted, .50)), ms(percentile(sorted, .95)), ms(percentile(sorted, .99)),
                    values.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000_000d);
        }
        private static long percentile(long[] values, double p) { return values[Math.max(0, (int) Math.ceil(p * values.length) - 1)]; }
        private static double ms(long nanos) { return nanos / 1_000_000d; }
    }

    private static final class PeakHeapSampler implements AutoCloseable {
        private volatile boolean running = true;
        private volatile long peak;
        private final Thread thread = Thread.ofPlatform().daemon().unstarted(() -> {
            while (running) {
                long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                peak = Math.max(peak, used);
                try { Thread.sleep(2); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); return; }
            }
        });
        void start() { thread.start(); }
        long peak() { return peak; }
        @Override public void close() throws InterruptedException { running = false; thread.join(); }
    }
}
