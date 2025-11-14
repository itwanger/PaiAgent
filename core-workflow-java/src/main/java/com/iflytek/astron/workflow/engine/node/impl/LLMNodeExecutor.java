package com.iflytek.astron.workflow.engine.node.impl;

import com.iflytek.astron.workflow.domain.Node;
import com.iflytek.astron.workflow.engine.VariablePool;
import com.iflytek.astron.workflow.engine.node.AbstractNodeExecutor;
import com.iflytek.astron.workflow.engine.node.StreamCallback;
import com.iflytek.astron.workflow.service.llm.SparkLLMClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM node executor
 * Directly calls iFlytek Spark API via WebSocket (like Python's SparkChatAi)
 * Supports both "node-llm" and "spark-llm" node types
 */
@Slf4j
@Component
public class LLMNodeExecutor extends AbstractNodeExecutor {
    
    private final SparkLLMClient sparkLLMClient;
    
    public LLMNodeExecutor(SparkLLMClient sparkLLMClient) {
        this.sparkLLMClient = sparkLLMClient;
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
        
        // Extract parameters from nodeParam
        String domain = getDomain(nodeParam);
        Double temperature = getTemperature(nodeParam);
        Integer maxTokens = getMaxTokens(nodeParam);
        String promptTemplate = getPrompt(nodeParam);
        
        // Resolve variables in prompt
        String resolvedPrompt = variablePool.resolve(promptTemplate);
        
        log.info("LLM node: domain={}, temperature={}, maxTokens={}, promptLength={}", 
                domain, temperature, maxTokens, resolvedPrompt.length());
        log.debug("LLM prompt (resolved): {}", resolvedPrompt);
        
        callback.send("llm_thinking", Map.of("nodeId", node.getId(), "prompt", resolvedPrompt));
        
        // Build messages list (similar to Python version)
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", resolvedPrompt);
        messages.add(userMessage);
        
        // Call Spark LLM via WebSocket
        String llmOutput = sparkLLMClient.chat(domain, temperature, maxTokens, messages);
        
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
    
    private String getDomain(Map<String, Object> nodeParam) {
        Object domainObj = nodeParam.get("domain");
        if (domainObj != null) {
            return String.valueOf(domainObj);
        }
        // Default domain
        return "generalv3.5";
    }
    
    private Double getTemperature(Map<String, Object> nodeParam) {
        Object tempObj = nodeParam.get("temperature");
        if (tempObj instanceof Number) {
            return ((Number) tempObj).doubleValue();
        } else if (tempObj instanceof String) {
            return Double.parseDouble((String) tempObj);
        }
        // Default temperature
        return 0.5;
    }
    
    private Integer getMaxTokens(Map<String, Object> nodeParam) {
        Object maxTokensObj = nodeParam.get("maxTokens");
        if (maxTokensObj instanceof Integer) {
            return (Integer) maxTokensObj;
        } else if (maxTokensObj instanceof String) {
            return Integer.parseInt((String) maxTokensObj);
        } else if (maxTokensObj instanceof Number) {
            return ((Number) maxTokensObj).intValue();
        }
        // Default max tokens
        return 2048;
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
