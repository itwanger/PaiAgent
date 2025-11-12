package com.iflytek.astron.workflow.engine.node;

import com.iflytek.astron.workflow.domain.InputItem;
import com.iflytek.astron.workflow.domain.Node;
import com.iflytek.astron.workflow.domain.NodeRef;
import com.iflytek.astron.workflow.domain.Value;
import com.iflytek.astron.workflow.engine.VariablePool;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Abstract base class for node executors
 * Provides common functionality for all node types
 */
@Slf4j
public abstract class AbstractNodeExecutor implements NodeExecutor {
    
    @Override
    public void execute(Node node, VariablePool variablePool, StreamCallback callback) throws Exception {
        String nodeId = node.getId();
        String nodeType = node.getNodeType();
        
        log.info("Executing node: {} (type: {})", nodeId, nodeType);
        
        callback.sendNodeStart(nodeId, nodeType);
        
        try {
            Map<String, Object> resolvedInputs = resolveInputs(node, variablePool);
            
            Map<String, Object> outputs = executeNode(node, resolvedInputs, variablePool, callback);
            
            storeOutputs(node, outputs, variablePool, callback);
            
            callback.sendNodeEnd(nodeId, nodeType);
            
        } catch (Exception e) {
            log.error("Error executing node {}: {}", nodeId, e.getMessage(), e);
            callback.sendError(nodeId, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Execute the node-specific logic
     * Subclasses must implement this method
     * 
     * @param node workflow node
     * @param inputs resolved input values
     * @param variablePool variable pool
     * @param callback stream callback
     * @return map of output values (key: output name, value: output value)
     * @throws Exception if execution fails
     */
    protected abstract Map<String, Object> executeNode(
            Node node, 
            Map<String, Object> inputs, 
            VariablePool variablePool,
            StreamCallback callback
    ) throws Exception;
    
    /**
     * Resolve all inputs for this node
     * Handles both literal values and variable references
     * 
     * @param node workflow node
     * @param variablePool variable pool
     * @return map of resolved input values
     */
    protected Map<String, Object> resolveInputs(Node node, VariablePool variablePool) {
        Map<String, Object> resolvedInputs = new HashMap<>();
        
        if (node.getData().getInputs() == null || node.getData().getInputs().isEmpty()) {
            log.debug("No inputs defined for node {}", node.getId());
            return resolvedInputs;
        }
        
        for (InputItem input : node.getData().getInputs()) {
            String inputName = input.getName();
            
            if (input.getSchema() == null || input.getSchema().getValue() == null) {
                log.warn("Input '{}' has no schema or value", inputName);
                continue;
            }
            
            Value value = input.getSchema().getValue();
            
            if (value.isReference()) {
                if (value.getContent() instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> refMap = (Map<String, String>) value.getContent();
                    String refNodeId = refMap.get("nodeId");
                    String refName = refMap.get("name");
                    
                    if (refNodeId != null && refName != null) {
                        Object refValue = variablePool.get(refNodeId, refName);
                        resolvedInputs.put(inputName, refValue);
                        log.debug("Resolved input '{}' from reference {}.{} = {}", 
                                inputName, refNodeId, refName, refValue);
                    }
                } else {
                    log.warn("Reference content is not a Map for input '{}'", inputName);
                }
            } else {
                resolvedInputs.put(inputName, value.getContent());
                log.debug("Resolved input '{}' from literal = {}", inputName, value.getContent());
            }
        }
        
        return resolvedInputs;
    }
    
    /**
     * Store node outputs in the variable pool
     * 
     * @param node workflow node
     * @param outputs output values produced by the node
     * @param variablePool variable pool
     * @param callback stream callback
     */
    protected void storeOutputs(Node node, Map<String, Object> outputs, 
                                VariablePool variablePool, StreamCallback callback) {
        String nodeId = node.getId();
        
        for (Map.Entry<String, Object> entry : outputs.entrySet()) {
            String outputName = entry.getKey();
            Object outputValue = entry.getValue();
            
            variablePool.set(nodeId, outputName, outputValue);
            callback.sendNodeOutput(nodeId, outputName, outputValue);
            
            log.debug("Stored output: {}.{} = {}", nodeId, outputName, outputValue);
        }
    }
}
