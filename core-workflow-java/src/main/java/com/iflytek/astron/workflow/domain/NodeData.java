package com.iflytek.astron.workflow.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Node data containing configuration and parameters
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodeData {
    
    /**
     * Input items
     */
    @JsonProperty("inputs")
    private List<InputItem> inputs = new ArrayList<>();
    
    /**
     * Node metadata
     */
    @JsonProperty("nodeMeta")
    private NodeMeta nodeMeta;
    
    /**
     * Node-specific parameters (flexible structure for different node types)
     */
    @JsonProperty("nodeParam")
    private Map<String, Object> nodeParam = new HashMap<>();
    
    /**
     * Output items
     */
    @JsonProperty("outputs")
    private List<OutputItem> outputs = new ArrayList<>();
}
