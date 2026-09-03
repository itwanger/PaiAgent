package com.paiagent.engine.executor.impl;

import com.paiagent.engine.model.WorkflowNode;
import com.paiagent.service.AgentMemoryService;
import com.paiagent.service.KnowledgeBaseService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class KnowledgeUpsertNodeExecutor extends AbstractAgentPlanNodeExecutor {

    private final KnowledgeBaseService knowledgeBaseService;
    private final AgentMemoryService memoryService;

    public KnowledgeUpsertNodeExecutor(KnowledgeBaseService knowledgeBaseService,
                                       AgentMemoryService memoryService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.memoryService = memoryService;
    }

    @Override
    public Map<String, Object> execute(WorkflowNode node, Map<String, Object> input) throws Exception {
        String content = textValue(node, input, "content", "output");
        if (content == null) {
            throw new IllegalArgumentException("写入知识库节点缺少 content");
        }

        Map<String, Object> output = knowledgeBaseService.upsert(
                stringData(node, "knowledgeBaseId", "default"),
                stringData(node, "title", null),
                content,
                stringData(node, "sourceUrl", null),
                memoryService.splitTags(stringData(node, "tags", "")),
                List.of(),
                null
        );
        output.put("output", output.get("contentId"));
        return output;
    }

    @Override
    public String getSupportedNodeType() {
        return "knowledge_upsert";
    }
}
