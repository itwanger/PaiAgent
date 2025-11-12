package com.iflytek.astron.workflow.engine.node.impl;

import com.iflytek.astron.workflow.domain.Node;
import com.iflytek.astron.workflow.engine.VariablePool;
import com.iflytek.astron.workflow.engine.node.AbstractNodeExecutor;
import com.iflytek.astron.workflow.engine.node.StreamCallback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Start node executor
 * Simply passes through the initial inputs to outputs
 */
@Slf4j
@Component
public class StartNodeExecutor extends AbstractNodeExecutor {
    
    @Override
    public String getNodeType() {
        return "node-start";
    }
    
    @Override
    protected Map<String, Object> executeNode(Node node, Map<String, Object> inputs, 
                                             VariablePool variablePool, StreamCallback callback) {
        log.info("Executing start node: {}", node.getId());
        
        callback.sendNodeStart(node.getId(), "Start");
        
        Map<String, Object> outputs = new HashMap<>(inputs);
        
        log.debug("Start node outputs: {}", outputs);
        
        callback.sendNodeOutput(node.getId(), "user_input", inputs.get("user_input"));
        callback.sendNodeEnd(node.getId(), "Start");
        
        return outputs;
    }
}
