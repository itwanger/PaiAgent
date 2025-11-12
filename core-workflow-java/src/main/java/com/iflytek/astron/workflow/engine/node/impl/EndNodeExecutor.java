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
 * End node executor
 * Formats the final output using a template
 */
@Slf4j
@Component
public class EndNodeExecutor extends AbstractNodeExecutor {
    
    @Override
    public String getNodeType() {
        return "node-end";
    }
    
    @Override
    protected Map<String, Object> executeNode(Node node, Map<String, Object> inputs, 
                                             VariablePool variablePool, StreamCallback callback) {
        log.info("Executing end node: {}", node.getId());
        
        callback.sendNodeStart(node.getId(), "End");
        
        Map<String, Object> nodeParam = node.getData().getNodeParam();
        
        Integer outputMode = getOutputMode(nodeParam);
        String template = getTemplate(nodeParam);
        
        String finalOutput;
        
        if (outputMode == 1 && template != null && !template.isEmpty()) {
            finalOutput = variablePool.resolve(template);
            log.info("End node: formatted output using template (length={})", finalOutput.length());
        } else {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Object> entry : inputs.entrySet()) {
                sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
            finalOutput = sb.toString();
            log.info("End node: direct output (mode=0)");
        }
        
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("final_output", finalOutput);
        
        log.debug("End node output: {}", finalOutput);
        
        callback.sendNodeOutput(node.getId(), "final_output", finalOutput);
        callback.sendNodeEnd(node.getId(), "End");
        
        return outputs;
    }
    
    private Integer getOutputMode(Map<String, Object> nodeParam) {
        Object outputModeObj = nodeParam.get("outputMode");
        if (outputModeObj instanceof Integer) {
            return (Integer) outputModeObj;
        } else if (outputModeObj instanceof Number) {
            return ((Number) outputModeObj).intValue();
        }
        return 1;
    }
    
    private String getTemplate(Map<String, Object> nodeParam) {
        Object templateObj = nodeParam.get("template");
        return templateObj != null ? String.valueOf(templateObj) : "";
    }
}
