package com.iflytek.astron.workflow.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Input schema definition
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InputSchema {
    
    /**
     * Data type: "string", "boolean", "integer", "number", "array", "object"
     */
    @JsonProperty("type")
    private String type;
    
    /**
     * Value definition (literal or reference)
     */
    @JsonProperty("value")
    private Value value;
}
