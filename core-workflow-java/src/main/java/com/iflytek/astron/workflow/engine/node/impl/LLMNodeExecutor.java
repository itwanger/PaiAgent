package com.iflytek.astron.workflow.engine.node.impl;

import com.iflytek.astron.workflow.domain.Node;
import com.iflytek.astron.workflow.engine.VariablePool;
import com.iflytek.astron.workflow.engine.node.AbstractNodeExecutor;
import com.iflytek.astron.workflow.engine.node.StreamCallback;
import com.iflytek.astron.workflow.service.ModelServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * LLM node executor
 * Calls the model service to execute LLM inference
 */
@Slf4j
@Component
public class LLMNodeExecutor extends AbstractNodeExecutor {
    
    private final ModelServiceClient modelServiceClient;
    
    public LLMNodeExecutor(ModelServiceClient modelServiceClient) {
        this.modelServiceClient = modelServiceClient;
    }
    
    @Override
    public String getNodeType() {
        return "node-llm";
    }
    
    @Override
    protected Map<String, Object> executeNode(Node node, Map<String, Object> inputs, 
                                             VariablePool variablePool, StreamCallback callback) throws Exception {
        log.info("Executing LLM node: {}", node.getId());
        
        callback.sendNodeStart(node.getId(), "LLM");
        
        Map<String, Object> nodeParam = node.getData().getNodeParam();
        
        Integer modelId = getModelId(nodeParam);
        String promptTemplate = getPrompt(nodeParam);
        
        String resolvedPrompt = variablePool.resolve(promptTemplate);
        
        log.info("LLM node: modelId={}, promptLength={}", modelId, resolvedPrompt.length());
        log.debug("LLM prompt (resolved): {}", resolvedPrompt);
        
        callback.send("llm_thinking", Map.of("nodeId", node.getId(), "prompt", resolvedPrompt));
        
        String llmOutput = modelServiceClient.chatCompletion(modelId, resolvedPrompt);
        
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("llm_output", llmOutput);
        
        log.info("LLM node completed: outputLength={}", llmOutput.length());
        
        callback.sendNodeOutput(node.getId(), "llm_output", llmOutput);
        callback.sendNodeEnd(node.getId(), "LLM");
        
        return outputs;
    }
    
    private Integer getModelId(Map<String, Object> nodeParam) {
        Object modelIdObj = nodeParam.get("modelId");
        if (modelIdObj instanceof Integer) {
            return (Integer) modelIdObj;
        } else if (modelIdObj instanceof String) {
            return Integer.parseInt((String) modelIdObj);
        } else if (modelIdObj instanceof Number) {
            return ((Number) modelIdObj).intValue();
        }
        throw new IllegalArgumentException("Invalid modelId in LLM node: " + modelIdObj);
    }
    
    private String getPrompt(Map<String, Object> nodeParam) {
        Object promptObj = nodeParam.get("prompt");
        if (promptObj == null) {
            throw new IllegalArgumentException("Missing 'prompt' in LLM node parameters");
        }
        return String.valueOf(promptObj);
    }
}
