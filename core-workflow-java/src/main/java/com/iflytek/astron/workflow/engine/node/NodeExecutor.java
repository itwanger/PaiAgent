package com.iflytek.astron.workflow.engine.node;

import com.iflytek.astron.workflow.domain.Node;
import com.iflytek.astron.workflow.engine.VariablePool;

/**
 * Base interface for all node executors
 * Each node type (Start, LLM, Plugin, End) must implement this interface
 */
public interface NodeExecutor {
    
    /**
     * Execute the node logic
     * 
     * @param node workflow node containing configuration
     * @param variablePool variable pool for reading inputs and storing outputs
     * @param callback callback for streaming output (SSE)
     * @throws Exception if execution fails
     */
    void execute(Node node, VariablePool variablePool, StreamCallback callback) throws Exception;
    
    /**
     * Get the node type this executor handles
     * @return node type string (e.g., "node-start", "node-llm", "node-plugin", "node-end")
     */
    String getNodeType();
}
