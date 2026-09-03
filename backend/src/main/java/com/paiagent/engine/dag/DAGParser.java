package com.paiagent.engine.dag;

import com.paiagent.engine.model.WorkflowConfig;
import com.paiagent.engine.model.WorkflowEdge;
import com.paiagent.engine.model.WorkflowNode;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * DAG 解析器
 * 负责将工作流配置解析为有向无环图,并执行拓扑排序
 */
@Component
public class DAGParser {
    
    /**
     * 解析工作流配置,返回拓扑排序后的节点执行顺序
     */
    public List<WorkflowNode> parse(WorkflowConfig config) {
        List<WorkflowNode> nodes = config.getNodes() != null ? config.getNodes() : Collections.emptyList();
        if (nodes.isEmpty()) {
            return Collections.emptyList();
        }
        List<WorkflowEdge> edges = config.getEdges() != null ? config.getEdges() : new ArrayList<>();
        
        // 构建节点 ID 到节点的映射
        Map<String, WorkflowNode> nodeMap = new HashMap<>();
        for (WorkflowNode node : nodes) {
            nodeMap.put(node.getId(), node);
        }
        
        // 构建依赖关系图: 节点 -> 它依赖的节点列表(前置节点)
        Map<String, List<String>> dependencies = new HashMap<>();
        // 构建反向依赖图: 节点 -> 依赖它的节点列表(后继节点)
        Map<String, List<String>> dependents = new HashMap<>();
        
        // 初始化
        for (WorkflowNode node : nodes) {
            dependencies.put(node.getId(), new ArrayList<>());
            dependents.put(node.getId(), new ArrayList<>());
        }
        
        // 构建依赖关系
        for (WorkflowEdge edge : edges) {
            String source = edge.getSource();
            String target = edge.getTarget();
            
            // target 依赖 source
            dependencies.get(target).add(source);
            // source 的后继节点包含 target
            dependents.get(source).add(target);
        }
        
        // 检测循环依赖
        detectCycle(dependencies, nodes);
        
        // 拓扑排序，复用已构建的 dependents
        return topologicalSort(nodeMap, dependencies, dependents);
    }
    
    /**
     * 检测循环依赖(使用 DFS)
     */
    private void detectCycle(Map<String, List<String>> dependencies, List<WorkflowNode> nodes) {
        Set<String> visited = new HashSet<>();
        Set<String> recStack = new HashSet<>();
        
        for (WorkflowNode node : nodes) {
            if (hasCycleDFS(node.getId(), dependencies, visited, recStack)) {
                throw new RuntimeException("工作流存在循环依赖,节点: " + node.getId());
            }
        }
    }
    
    /**
     * DFS 检测循环
     */
    private boolean hasCycleDFS(String nodeId, Map<String, List<String>> dependencies, 
                                 Set<String> visited, Set<String> recStack) {
        if (recStack.contains(nodeId)) {
            return true; // 发现循环
        }
        
        if (visited.contains(nodeId)) {
            return false; // 已访问过且无循环
        }
        
        visited.add(nodeId);
        recStack.add(nodeId);
        
        // 递归检查所有依赖节点
        List<String> deps = dependencies.get(nodeId);
        if (deps != null) {
            for (String dep : deps) {
                if (hasCycleDFS(dep, dependencies, visited, recStack)) {
                    return true;
                }
            }
        }
        
        recStack.remove(nodeId);
        return false;
    }
    
    /**
     * 拓扑排序(Kahn 算法)
     */
    private List<WorkflowNode> topologicalSort(Map<String, WorkflowNode> nodeMap, 
                                                 Map<String, List<String>> dependencies,
                                                 Map<String, List<String>> dependents) {
        List<WorkflowNode> result = new ArrayList<>();

        // 计算每个节点的入度
        Map<String, Integer> inDegree = new HashMap<>();
        for (String nodeId : nodeMap.keySet()) {
            inDegree.put(nodeId, dependencies.get(nodeId).size());
        }

        // 找出所有入度为 0 的节点(起始节点)
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }

        // Kahn 算法执行拓扑排序，直接使用传入的 dependents
        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            result.add(nodeMap.get(nodeId));

            // 将该节点的所有后继节点的入度减 1
            List<String> deps = dependents.get(nodeId);
            if (deps != null) {
                for (String dep : deps) {
                    int degree = inDegree.get(dep) - 1;
                    inDegree.put(dep, degree);
                    if (degree == 0) {
                        queue.offer(dep);
                    }
                }
            }
        }

        // 如果排序后的节点数小于总节点数,说明存在循环依赖
        if (result.size() != nodeMap.size()) {
            throw new RuntimeException("工作流存在循环依赖,无法完成拓扑排序");
        }

        return result;
    }
}
