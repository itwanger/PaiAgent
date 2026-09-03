package com.paiagent.service.rag;

import com.paiagent.config.RagProperties;
import com.paiagent.service.AgentPlanConfigResolver;
import com.paiagent.service.ResolvedAgentPlanConfig;
import com.paiagent.service.VolcengineAgentPlanClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class RagEmbeddingModel implements EmbeddingModel {
    private final RagProperties properties;
    private final AgentPlanConfigResolver resolver;
    private final VolcengineAgentPlanClient client;

    public RagEmbeddingModel(RagProperties properties, AgentPlanConfigResolver resolver,
                             VolcengineAgentPlanClient client) {
        this.properties = properties;
        this.resolver = resolver;
        this.client = client;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        ResolvedAgentPlanConfig config = resolver.resolveKnowledgeConfig(
                properties.getEmbedding().getConfigId(), properties.getEmbedding().getModel());
        if (!StringUtils.hasText(config.apiUrl()) || !StringUtils.hasText(config.apiKey())) {
            throw new IllegalStateException("统一 RAG Embedding Profile 未配置有效 API URL/API Key");
        }
        try {
            List<List<Double>> vectors = client.createEmbeddings(config, request.getInstructions());
            List<Embedding> results = new ArrayList<>(vectors.size());
            for (int i = 0; i < vectors.size(); i++) {
                float[] vector = toFloat(vectors.get(i));
                if (vector.length != properties.getEmbedding().getDimension()) {
                    throw new IllegalStateException("Embedding 维度不匹配: expected="
                            + properties.getEmbedding().getDimension() + ", actual=" + vector.length);
                }
                results.add(new Embedding(vector, i));
            }
            return new EmbeddingResponse(results);
        } catch (IOException e) {
            throw new IllegalStateException("Embedding 调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public int dimensions() {
        return properties.getEmbedding().getDimension();
    }

    private float[] toFloat(List<Double> values) {
        float[] output = new float[values.size()];
        for (int i = 0; i < values.size(); i++) output[i] = values.get(i).floatValue();
        return output;
    }
}
