package com.iflytek.astron.workflow.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Workflow node with ID and data
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Node {
    
    /**
     * Node ID in format: "node-type::sequenceId"
     * Examples: "node-start::001", "node-llm::002", "node-plugin::003", "node-end::004"
     */
    @JsonProperty("id")
    private String id;
    
    /**
     * Node data containing configuration and parameters
     */
    @JsonProperty("data")
    private NodeData data;
    
    /**
     * Extract node type from ID
     * @return node type (e.g., "node-start", "node-llm", "node-plugin", "node-end")
     */
    public String getNodeType() {
        if (id != null && id.contains("::")) {
            return id.split("::")[0];
        }
        return null;
    }
}
