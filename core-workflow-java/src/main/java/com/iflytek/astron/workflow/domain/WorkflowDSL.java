package com.iflytek.astron.workflow.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Workflow DSL (Domain Specific Language) structure
 * Represents the complete workflow definition with nodes and edges
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDSL {
    
    /**
     * List of nodes in the workflow
     */
    @JsonProperty("nodes")
    private List<Node> nodes = new ArrayList<>();
    
    /**
     * List of edges connecting nodes
     */
    @JsonProperty("edges")
    private List<Edge> edges = new ArrayList<>();
    
    /**
     * Find a node by ID
     * @param nodeId node ID to search for
     * @return the node if found, null otherwise
     */
    public Node findNodeById(String nodeId) {
        return nodes.stream()
                .filter(node -> node.getId().equals(nodeId))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Get all edges from a specific source node
     * @param sourceNodeId source node ID
     * @return list of outgoing edges
     */
    public List<Edge> getOutgoingEdges(String sourceNodeId) {
        List<Edge> outgoing = new ArrayList<>();
        for (Edge edge : edges) {
            if (edge.getSource().equals(sourceNodeId)) {
                outgoing.add(edge);
            }
        }
        return outgoing;
    }
}
