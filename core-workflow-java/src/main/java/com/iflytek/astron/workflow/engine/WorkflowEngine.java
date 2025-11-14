package com.iflytek.astron.workflow.engine;

import com.iflytek.astron.workflow.domain.Edge;
import com.iflytek.astron.workflow.domain.Node;
import com.iflytek.astron.workflow.domain.WorkflowDSL;
import com.iflytek.astron.workflow.engine.node.NodeExecutor;
import com.iflytek.astron.workflow.engine.node.StreamCallback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Workflow execution engine
 * Executes workflow nodes in sequential order based on edges
 */
@Slf4j
@Component
public class WorkflowEngine {
    
    private final Map<String, NodeExecutor> nodeExecutors;
    private final List<NodeExecutor> executorList;
    private final VariablePool variablePool;
    
    public WorkflowEngine(List<NodeExecutor> executors, VariablePool variablePool) {
        this.nodeExecutors = new HashMap<>();
        this.executorList = executors;
        for (NodeExecutor executor : executors) {
            this.nodeExecutors.put(executor.getNodeType(), executor);
        }
        this.variablePool = variablePool;
        log.info("Registered {} node executors: {}", nodeExecutors.size(), nodeExecutors.keySet());
    }
    
    /**
     * Execute a workflow
     * 
     * @param workflowDSL workflow definition
     * @param inputs initial input values (from user)
     * @param callback stream callback for SSE output
     * @throws Exception if execution fails
     */
    public void execute(WorkflowDSL workflowDSL, Map<String, Object> inputs, StreamCallback callback) throws Exception {
        log.info("Starting workflow execution with {} nodes", workflowDSL.getNodes().size());
        
        variablePool.clear();
        
        Node startNode = findStartNode(workflowDSL);
        if (startNode == null) {
            throw new IllegalStateException("No start node found in workflow");
        }
        
        initializeStartNodeInputs(startNode, inputs);
        
        List<Node> executionOrder = buildExecutionOrder(workflowDSL, startNode);
        log.info("Execution order: {}", executionOrder.stream().map(Node::getId).toList());
        
        for (Node node : executionOrder) {
            executeNode(node, callback);
        }
        
        log.info("Workflow execution completed successfully");
    }
    
    /**
     * Find the start node in the workflow
     */
    private Node findStartNode(WorkflowDSL workflowDSL) {
        return workflowDSL.getNodes().stream()
                .filter(node -> "node-start".equals(node.getNodeType()))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Initialize start node inputs with user-provided values
     */
    private void initializeStartNodeInputs(Node startNode, Map<String, Object> inputs) {
        for (Map.Entry<String, Object> entry : inputs.entrySet()) {
            variablePool.set(startNode.getId(), entry.getKey(), entry.getValue());
            log.debug("Initialized start node input: {}.{} = {}", 
                    startNode.getId(), entry.getKey(), entry.getValue());
        }
    }
    
    /**
     * Build execution order by following edges from start node
     * This is a simplified sequential execution (no branching/loops)
     */
    private List<Node> buildExecutionOrder(WorkflowDSL workflowDSL, Node startNode) {
        List<Node> executionOrder = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        
        Node currentNode = startNode;
        
        while (currentNode != null) {
            if (visited.contains(currentNode.getId())) {
                log.warn("Cycle detected at node: {}", currentNode.getId());
                break;
            }
            
            executionOrder.add(currentNode);
            visited.add(currentNode.getId());
            
            List<Edge> outgoingEdges = workflowDSL.getOutgoingEdges(currentNode.getId());
            
            if (outgoingEdges.isEmpty()) {
                break;
            }
            
            Edge nextEdge = outgoingEdges.get(0);
            currentNode = workflowDSL.findNodeById(nextEdge.getTarget());
        }
        
        return executionOrder;
    }
    
    /**
     * Execute a single node
     */
    private void executeNode(Node node, StreamCallback callback) throws Exception {
        String nodeType = node.getNodeType();
        
        NodeExecutor executor = nodeExecutors.get(nodeType);
        
        // If no exact match, try to find a compatible executor
        if (executor == null) {
            for (NodeExecutor candidate : executorList) {
                // Check LLMNodeExecutor for spark-llm
                if (candidate instanceof com.iflytek.astron.workflow.engine.node.impl.LLMNodeExecutor) {
                    com.iflytek.astron.workflow.engine.node.impl.LLMNodeExecutor llmExecutor = 
                        (com.iflytek.astron.workflow.engine.node.impl.LLMNodeExecutor) candidate;
                    if (llmExecutor.supports(nodeType)) {
                        executor = candidate;
                        log.info("Found compatible LLM executor for node type: {}", nodeType);
                        break;
                    }
                }
                // Check PluginNodeExecutor for plugin
                if (candidate instanceof com.iflytek.astron.workflow.engine.node.impl.PluginNodeExecutor) {
                    com.iflytek.astron.workflow.engine.node.impl.PluginNodeExecutor pluginExecutor = 
                        (com.iflytek.astron.workflow.engine.node.impl.PluginNodeExecutor) candidate;
                    if (pluginExecutor.supports(nodeType)) {
                        executor = candidate;
                        log.info("Found compatible Plugin executor for node type: {}", nodeType);
                        break;
                    }
                }
            }
        }
        
        if (executor == null) {
            throw new IllegalStateException("No executor found for node type: " + nodeType);
        }
        
        executor.execute(node, variablePool, callback);
    }
}
