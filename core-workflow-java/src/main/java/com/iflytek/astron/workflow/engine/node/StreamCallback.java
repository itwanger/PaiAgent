package com.iflytek.astron.workflow.engine.node;

/**
 * Callback interface for streaming node execution output
 * Used for SSE (Server-Sent Events) real-time progress updates
 */
public interface StreamCallback {
    
    /**
     * Send a streaming message to the client
     * 
     * @param eventType event type (e.g., "node_start", "node_output", "node_end", "error")
     * @param data event data (will be serialized to JSON)
     */
    void send(String eventType, Object data);
    
    /**
     * Helper method to send node start event
     * @param nodeId node ID
     * @param nodeType node type
     */
    default void sendNodeStart(String nodeId, String nodeType) {
        send("node_start", new NodeEvent(nodeId, nodeType, "started"));
    }
    
    /**
     * Helper method to send node output event
     * @param nodeId node ID
     * @param outputName output variable name
     * @param outputValue output value
     */
    default void sendNodeOutput(String nodeId, String outputName, Object outputValue) {
        send("node_output", new NodeOutputEvent(nodeId, outputName, outputValue));
    }
    
    /**
     * Helper method to send node end event
     * @param nodeId node ID
     * @param nodeType node type
     */
    default void sendNodeEnd(String nodeId, String nodeType) {
        send("node_end", new NodeEvent(nodeId, nodeType, "completed"));
    }
    
    /**
     * Helper method to send error event
     * @param nodeId node ID
     * @param errorMessage error message
     */
    default void sendError(String nodeId, String errorMessage) {
        send("error", new ErrorEvent(nodeId, errorMessage));
    }
    
    /**
     * Node event data structure
     */
    class NodeEvent {
        public String nodeId;
        public String nodeType;
        public String status;
        
        public NodeEvent(String nodeId, String nodeType, String status) {
            this.nodeId = nodeId;
            this.nodeType = nodeType;
            this.status = status;
        }
    }
    
    /**
     * Node output event data structure
     */
    class NodeOutputEvent {
        public String nodeId;
        public String outputName;
        public Object outputValue;
        
        public NodeOutputEvent(String nodeId, String outputName, Object outputValue) {
            this.nodeId = nodeId;
            this.outputName = outputName;
            this.outputValue = outputValue;
        }
    }
    
    /**
     * Error event data structure
     */
    class ErrorEvent {
        public String nodeId;
        public String errorMessage;
        
        public ErrorEvent(String nodeId, String errorMessage) {
            this.nodeId = nodeId;
            this.errorMessage = errorMessage;
        }
    }
}
