package com.iflytek.astron.workflow.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Edge connecting two nodes in the workflow
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Edge {
    
    /**
     * Source node ID
     */
    @JsonProperty("sourceNodeId")
    private String sourceNodeId;
    
    /**
     * Target node ID
     */
    @JsonProperty("targetNodeId")
    private String targetNodeId;
    
    /**
     * Source handle (output name from source node)
     */
    @JsonProperty("sourceHandle")
    private String sourceHandle;
    
    public String getSource() {
        return sourceNodeId;
    }
    
    public String getTarget() {
        return targetNodeId;
    }
}
