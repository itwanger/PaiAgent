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
 * Supports both "node-llm" and "spark-llm" node types
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
    
    /**
     * Check if this executor can handle the given node type
     * Supports both "node-llm" and "spark-llm"
     */
    public boolean supports(String nodeType) {
        return "node-llm".equals(nodeType) || "spark-llm".equals(nodeType);
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
        
        // Determine output name from node's output definition
        String outputName = "llm_output"; // default
        if (node.getData().getOutputs() != null && !node.getData().getOutputs().isEmpty()) {
            outputName = node.getData().getOutputs().get(0).getName();
            log.debug("Using output name from definition: {}", outputName);
        }
        
        Map<String, Object> outputs = new HashMap<>();
        outputs.put(outputName, llmOutput);
        
        log.info("LLM node completed: outputName={}, outputLength={}", outputName, llmOutput.length());
        
        callback.sendNodeOutput(node.getId(), outputName, llmOutput);
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
        // Try "prompt" first (for node-llm)
        Object promptObj = nodeParam.get("prompt");
        if (promptObj != null) {
            return String.valueOf(promptObj);
        }
        
        // Try "template" (for spark-llm)
        Object templateObj = nodeParam.get("template");
        if (templateObj != null) {
            return String.valueOf(templateObj);
        }
        
        throw new IllegalArgumentException("Missing 'prompt' or 'template' in LLM node parameters");
    }
}
