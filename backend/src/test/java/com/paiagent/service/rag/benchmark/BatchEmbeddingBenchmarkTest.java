package com.paiagent.service.rag.benchmark;

import com.paiagent.config.RagProperties;
import com.paiagent.service.AgentPlanConfigResolver;
import com.paiagent.service.ResolvedAgentPlanConfig;
import com.paiagent.service.VolcengineAgentPlanClient;
import com.paiagent.service.rag.RagEmbeddingModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BatchEmbeddingBenchmarkTest {
    private static final int DIMENSION = 32;
    private static final int BATCH_SIZE = 10;

    @Test
    void batchModeReducesCallsAndPreservesEveryChunk() throws Exception {
        for (int chunks : List.of(100, 500, 1_000)) {
            Result single = run(chunks, 1);
            Result batch = run(chunks, BATCH_SIZE);

            assertEquals(chunks, single.successfulChunks());
            assertEquals(chunks, single.calls());
            assertEquals(chunks, batch.successfulChunks());
            assertEquals((chunks + BATCH_SIZE - 1) / BATCH_SIZE, batch.calls());
            assertEquals(0, single.failedChunks());
            assertEquals(0, batch.failedChunks());

            System.out.printf("BATCH_EMBEDDING chunks=%d singleCalls=%d singleMs=%.3f "
                            + "batchCalls=%d batchMs=%.3f%n",
                    chunks, single.calls(), single.totalNanos() / 1_000_000d,
                    batch.calls(), batch.totalNanos() / 1_000_000d);
        }
    }

    private Result run(int chunks, int batchSize) throws Exception {
        RagProperties properties = new RagProperties();
        properties.getEmbedding().setDimension(DIMENSION);
        AgentPlanConfigResolver resolver = mock(AgentPlanConfigResolver.class);
        ResolvedAgentPlanConfig config = new ResolvedAgentPlanConfig(
                1L, "benchmark", "http://local-mock", "key", "model", "model", null, null, false);
        when(resolver.resolveKnowledgeConfig(null, properties.getEmbedding().getModel())).thenReturn(config);

        CountingEmbeddingClient client = new CountingEmbeddingClient();
        RagEmbeddingModel model = new RagEmbeddingModel(properties, resolver, client);
        List<String> inputs = new ArrayList<>(chunks);
        for (int i = 0; i < chunks; i++) inputs.add("chunk-" + i);

        int successful = 0;
        long started = System.nanoTime();
        for (int start = 0; start < chunks; start += batchSize) {
            int end = Math.min(chunks, start + batchSize);
            successful += model.call(new EmbeddingRequest(inputs.subList(start, end), null))
                    .getResults().size();
        }
        return new Result(System.nanoTime() - started, client.calls.get(), successful, chunks - successful);
    }

    private static final class CountingEmbeddingClient extends VolcengineAgentPlanClient {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public List<List<Double>> createEmbeddings(ResolvedAgentPlanConfig config, List<String> inputs)
                throws IOException {
            calls.incrementAndGet();
            try {
                // 固定请求开销 + 很小的逐输入开销，模拟本地可复现的网络服务。
                Thread.sleep(1, Math.min(900_000, inputs.size() * 20_000));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted", error);
            }
            List<List<Double>> result = new ArrayList<>(inputs.size());
            for (String input : inputs) {
                List<Double> vector = new ArrayList<>(DIMENSION);
                for (int i = 0; i < DIMENSION; i++) vector.add((double) (input.hashCode() + i) / Integer.MAX_VALUE);
                result.add(vector);
            }
            return result;
        }
    }

    private record Result(long totalNanos, int calls, int successfulChunks, int failedChunks) {
    }
}
