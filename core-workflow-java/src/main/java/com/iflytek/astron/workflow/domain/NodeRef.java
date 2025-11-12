package com.iflytek.astron.workflow.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Node reference for variable references like {{node-llm::002.llm_output}}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodeRef {
    
    /**
     * ID of the referenced node (e.g., "node-llm::002")
     */
    @JsonProperty("nodeId")
    private String nodeId;
    
    /**
     * Name of the output variable (e.g., "llm_output")
     */
    @JsonProperty("name")
    private String name;
}
