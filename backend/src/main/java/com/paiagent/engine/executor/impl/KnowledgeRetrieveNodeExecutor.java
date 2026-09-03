package com.paiagent.engine.executor.impl;

import com.paiagent.engine.model.WorkflowNode;
import com.paiagent.service.KnowledgeBaseService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class KnowledgeRetrieveNodeExecutor extends AbstractAgentPlanNodeExecutor {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeRetrieveNodeExecutor(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Override
    public Map<String, Object> execute(WorkflowNode node, Map<String, Object> input) throws Exception {
        String query = textValue(node, input, "query", "input");
        if (query == null) {
            throw new IllegalArgumentException("检索知识库节点缺少 query");
        }

        Map<String, Object> output = knowledgeBaseService.retrieve(
                stringData(node, "knowledgeBaseId", "default"),
                query,
                List.of(),
                intData(node, "topK", 5),
                doubleData(node, "scoreThreshold", 0.2)
        );
        output.put("output", output.get("context"));
        return output;
    }

    @Override
    public String getSupportedNodeType() {
        return "knowledge_retrieve";
    }
}
