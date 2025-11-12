package com.iflytek.astron.workflow.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Output item with name and schema
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OutputItem {
    
    @JsonProperty("id")
    private String id;
    
    /**
     * Output name (e.g., "llm_output", "voice_url")
     */
    @JsonProperty("name")
    private String name;
    
    /**
     * Output schema definition
     */
    @JsonProperty("schema")
    private Map<String, Object> schema;
    
    /**
     * Whether this output is required
     */
    @JsonProperty("required")
    private boolean required;
}
