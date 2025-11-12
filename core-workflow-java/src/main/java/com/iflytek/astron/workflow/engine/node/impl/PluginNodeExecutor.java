package com.iflytek.astron.workflow.engine.node.impl;

import com.iflytek.astron.workflow.domain.Node;
import com.iflytek.astron.workflow.engine.VariablePool;
import com.iflytek.astron.workflow.engine.node.AbstractNodeExecutor;
import com.iflytek.astron.workflow.engine.node.StreamCallback;
import com.iflytek.astron.workflow.service.AIToolsClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Plugin node executor
 * Calls external plugin/tool services (e.g., voice synthesis via core-aitools)
 */
@Slf4j
@Component
public class PluginNodeExecutor extends AbstractNodeExecutor {
    
    private final AIToolsClient aiToolsClient;
    
    public PluginNodeExecutor(AIToolsClient aiToolsClient) {
        this.aiToolsClient = aiToolsClient;
    }
    
    @Override
    public String getNodeType() {
        return "node-plugin";
    }
    
    @Override
    protected Map<String, Object> executeNode(Node node, Map<String, Object> inputs, 
                                             VariablePool variablePool, StreamCallback callback) throws Exception {
        log.info("Executing plugin node: {}", node.getId());
        
        callback.sendNodeStart(node.getId(), "Plugin");
        
        Map<String, Object> nodeParam = node.getData().getNodeParam();
        
        String pluginId = getString(nodeParam, "pluginId");
        String operationId = getString(nodeParam, "operationId");
        
        if ("tool@8b2262bef821000".equals(pluginId)) {
            Map<String, Object> result = executeVoiceSynthesis(node.getId(), nodeParam, inputs, variablePool, callback);
            callback.sendNodeEnd(node.getId(), "Plugin");
            return result;
        }
        
        throw new UnsupportedOperationException("Unsupported plugin: " + pluginId);
    }
    
    private Map<String, Object> executeVoiceSynthesis(String nodeId,
                                                      Map<String, Object> nodeParam, 
                                                      Map<String, Object> inputs,
                                                      VariablePool variablePool,
                                                      StreamCallback callback) throws Exception {
        String pluginId = getString(nodeParam, "pluginId");
        String operationId = getString(nodeParam, "operationId");
        String vcn = getString(nodeParam, "vcn");
        Integer speed = getInteger(nodeParam, "speed", 50);
        
        Object textObj = inputs.get("text");
        if (textObj == null) {
            throw new IllegalArgumentException("Missing 'text' input for voice synthesis");
        }
        String text = String.valueOf(textObj);
        
        log.info("Voice synthesis: vcn={}, speed={}, textLength={}", vcn, speed, text.length());
        
        callback.send("voice_synthesis_start", Map.of(
            "nodeId", nodeId,
            "vcn", vcn,
            "textLength", text.length()
        ));
        
        String voiceUrl = aiToolsClient.voiceSynthesis(pluginId, operationId, text, vcn, speed);
        
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("voice_url", voiceUrl);
        
        log.info("Plugin node completed: voice_url={}", voiceUrl);
        
        callback.sendNodeOutput(nodeId, "voice_url", voiceUrl);
        
        return outputs;
    }
    
    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : null;
    }
    
    private Integer getInteger(Map<String, Object> map, String key, Integer defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            return Integer.parseInt((String) value);
        }
        return defaultValue;
    }
}
