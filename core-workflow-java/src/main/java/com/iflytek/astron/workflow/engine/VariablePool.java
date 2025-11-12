package com.iflytek.astron.workflow.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Variable pool for managing node outputs and resolving template references
 * Handles variable references in format: {{node-id.output-name}}
 * 
 * Example:
 * - Template: "User said: {{node-start::001.user_input}}"
 * - After resolution: "User said: 介绍一下 Java"
 */
@Slf4j
@Component
public class VariablePool {
    
    /**
     * Pattern to match variable references: {{node-id.variable-name}}
     */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");
    
    /**
     * Storage for node outputs
     * Key: "node-id.output-name" (e.g., "node-start::001.user_input")
     * Value: actual output value
     */
    private final Map<String, Object> variables = new HashMap<>();
    
    /**
     * Set a variable in the pool
     * @param nodeId node ID (e.g., "node-start::001")
     * @param outputName output name (e.g., "user_input")
     * @param value actual value
     */
    public void set(String nodeId, String outputName, Object value) {
        String key = buildKey(nodeId, outputName);
        variables.put(key, value);
        log.debug("VariablePool.set: {} = {}", key, value);
    }
    
    /**
     * Get a variable from the pool
     * @param nodeId node ID
     * @param outputName output name
     * @return variable value, or null if not found
     */
    public Object get(String nodeId, String outputName) {
        String key = buildKey(nodeId, outputName);
        return variables.get(key);
    }
    
    /**
     * Get a variable as String
     * @param nodeId node ID
     * @param outputName output name
     * @return string value, or null if not found
     */
    public String getString(String nodeId, String outputName) {
        Object value = get(nodeId, outputName);
        return value != null ? String.valueOf(value) : null;
    }
    
    /**
     * Resolve all variable references in a template string
     * 
     * Example:
     * Input: "User: {{node-start::001.user_input}}, LLM: {{node-llm::002.llm_output}}"
     * Output: "User: 介绍一下 Java, LLM: Java是一种..."
     * 
     * @param template template string with variable references
     * @return resolved string with actual values
     */
    public String resolve(String template) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String reference = matcher.group(1); // e.g., "node-start::001.user_input"
            String value = resolveReference(reference);
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
    
    /**
     * Resolve a single variable reference
     * @param reference reference string (e.g., "node-start::001.user_input")
     * @return resolved value, or empty string if not found
     */
    private String resolveReference(String reference) {
        Object value = variables.get(reference);
        if (value == null) {
            log.warn("Variable not found: {}", reference);
            return "";
        }
        return String.valueOf(value);
    }
    
    /**
     * Build storage key from node ID and output name
     * @param nodeId node ID
     * @param outputName output name
     * @return storage key (e.g., "node-start::001.user_input")
     */
    private String buildKey(String nodeId, String outputName) {
        return nodeId + "." + outputName;
    }
    
    /**
     * Clear all variables (used between workflow executions)
     */
    public void clear() {
        variables.clear();
        log.debug("VariablePool cleared");
    }
    
    /**
     * Get all variables (for debugging)
     * @return copy of all variables
     */
    public Map<String, Object> getAll() {
        return new HashMap<>(variables);
    }
}
