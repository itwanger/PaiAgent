package com.paiagent.engine;

import com.alibaba.fastjson2.JSON;
import com.paiagent.dto.ExecutionEvent;
import com.paiagent.dto.ExecutionResponse;
import com.paiagent.dto.ResumeExecutionRequest;
import com.paiagent.engine.dag.DAGParser;
import com.paiagent.engine.executor.NodeExecutor;
import com.paiagent.engine.executor.NodeExecutorFactory;
import com.paiagent.engine.model.WorkflowConfig;
import com.paiagent.engine.model.WorkflowEdge;
import com.paiagent.engine.model.WorkflowNode;
import com.paiagent.entity.ExecutionRecord;
import com.paiagent.entity.ExecutionSnapshot;
import com.paiagent.entity.ExecutionVariable;
import com.paiagent.entity.Workflow;
import com.paiagent.mapper.ExecutionRecordMapper;
import com.paiagent.mapper.ExecutionSnapshotMapper;
import com.paiagent.mapper.ExecutionVariableMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Service
public class WorkflowEngine implements WorkflowExecutor {
    
    @Autowired
    private DAGParser dagParser;

    @Autowired
    private WorkflowConfigParser workflowConfigParser;
    
    @Autowired
    private NodeExecutorFactory executorFactory;
    
    @Autowired
    private ExecutionRecordMapper executionRecordMapper;

    @Autowired(required = false)
    private ExecutionSnapshotMapper executionSnapshotMapper;

    @Autowired(required = false)
    private ExecutionVariableMapper executionVariableMapper;
    
    @Override
    public ExecutionResponse execute(Workflow workflow, String inputData) {
        return executeWithCallback(workflow, inputData, null);
    }
    
    @Override
    public ExecutionResponse executeWithCallback(Workflow workflow, String inputData, Consumer<ExecutionEvent> eventCallback) {
        long startTime = System.currentTimeMillis();
        WorkflowConfig config = workflowConfigParser.parse(workflow.getFlowData());
        List<WorkflowNode> sortedNodes = dagParser.parse(config);
        List<WorkflowEdge> edges = config.getEdges() != null ? config.getEdges() : new ArrayList<>();
        EdgeIndexes indexes = buildEdgeIndexes(edges);
        ExecutionRecord record = createRunningRecord(workflow.getId(), inputData);

        if (eventCallback != null) {
            eventCallback.accept(ExecutionEvent.workflowStart(record.getId()));
        }

        return executeNodes(record, workflow.getId(), inputData, sortedNodes, indexes, new ResumeState(), startTime, eventCallback);
    }

    public ExecutionResponse resumeExecution(Workflow workflow,
                                             Long executionId,
                                             ResumeExecutionRequest request,
                                             Consumer<ExecutionEvent> eventCallback) {
        if (executionSnapshotMapper == null) {
            throw new IllegalStateException("断点续执行未启用: executionSnapshotMapper 不可用");
        }

        ExecutionRecord record = executionRecordMapper.selectById(executionId);
        if (record == null || !Objects.equals(record.getFlowId(), workflow.getId())) {
            throw new IllegalArgumentException("执行记录不存在或不属于当前工作流");
        }

        ResumeExecutionRequest resumeRequest = request != null ? request : new ResumeExecutionRequest();
        WorkflowConfig config = workflowConfigParser.parse(workflow.getFlowData());
        List<WorkflowNode> sortedNodes = dagParser.parse(config);
        List<WorkflowEdge> edges = config.getEdges() != null ? config.getEdges() : new ArrayList<>();
        EdgeIndexes indexes = buildEdgeIndexes(edges);

        List<ExecutionSnapshot> snapshots = executionSnapshotMapper.selectByExecutionId(executionId);
        Map<String, ExecutionSnapshot> snapshotsByNode = snapshots.stream()
                .collect(Collectors.toMap(ExecutionSnapshot::getNodeId, snapshot -> snapshot, (left, right) -> right, LinkedHashMap::new));

        String startNodeId = resolveResumeStartNodeId(sortedNodes, snapshotsByNode, resumeRequest);
        if (startNodeId == null) {
            return responseFromRecord(record, snapshots);
        }

        String inputData = extractInputValue(record.getInputData());
        ResumeState resumeState = buildResumeState(
                sortedNodes,
                snapshotsByNode,
                indexes,
                startNodeId,
                Boolean.TRUE.equals(resumeRequest.getSkipSuccessNodes())
        );
        resumeState.modifiedVariables = resumeRequest.getModifiedVariables() != null
                ? new LinkedHashMap<>(resumeRequest.getModifiedVariables())
                : Collections.emptyMap();
        saveModifiedVariables(executionId, resumeState.modifiedVariables);

        record.setStatus("RUNNING");
        record.setErrorMessage(null);
        executionRecordMapper.updateById(record);

        long startTime = System.currentTimeMillis();
        if (eventCallback != null) {
            eventCallback.accept(ExecutionEvent.workflowStart(record.getId()));
        }

        return executeNodes(record, workflow.getId(), inputData, sortedNodes, indexes, resumeState, startTime, eventCallback);
    }

    private ExecutionResponse executeNodes(ExecutionRecord record,
                                           Long flowId,
                                           String inputData,
                                           List<WorkflowNode> sortedNodes,
                                           EdgeIndexes indexes,
                                           ResumeState resumeState,
                                           long startTime,
                                           Consumer<ExecutionEvent> eventCallback) {
        Map<String, List<WorkflowEdge>> edgesBySource = indexes.edgesBySource;
        Map<String, List<WorkflowEdge>> edgesByTarget = indexes.edgesByTarget;
        
        List<ExecutionResponse.NodeResult> nodeResults = new ArrayList<>(resumeState.restoredNodeResults);
        Map<String, Map<String, Object>> nodeOutputs = new HashMap<>(resumeState.nodeOutputs);
        Set<String> skippedNodes = new HashSet<>(resumeState.skippedNodes);
        
        Map<String, Object> currentInput = new HashMap<>();
        currentInput.put("input", inputData);
        currentInput.putAll(resumeState.latestOutput);
        currentInput.putAll(resumeState.modifiedVariables);
        
        String status = "SUCCESS";
        String errorMessage = null;
        String outputData = null;
        boolean startReached = resumeState.startNodeId == null;
        
        try {
            for (WorkflowNode node : sortedNodes) {
                if (!startReached) {
                    if (node.getId().equals(resumeState.startNodeId)) {
                        startReached = true;
                    } else {
                        continue;
                    }
                }

                // 跳过条件分支未选中的节点
                if (skippedNodes.contains(node.getId())) {
                    log.info("跳过节点 [{}]（条件分支未选中）", node.getId());
                    continue;
                }
                
                // 解析当前节点的输入
                Map<String, Object> nodeInput = resolveNodeInput(node, nodeOutputs, edgesByTarget, currentInput);
                if (!resumeState.modifiedVariables.isEmpty()) {
                    nodeInput = new LinkedHashMap<>(nodeInput);
                    nodeInput.putAll(resumeState.modifiedVariables);
                }
                
                long nodeStartTime = System.currentTimeMillis();
                ExecutionSnapshot snapshot = createOrUpdateSnapshotStart(record.getId(), flowId, node, nodeInput, sortedNodes.indexOf(node), nodeStartTime);
                
                if (eventCallback != null) {
                    eventCallback.accept(ExecutionEvent.nodeStart(node.getId(), node.getType()));
                }
                
                ExecutionResponse.NodeResult nodeResult = new ExecutionResponse.NodeResult();
                nodeResult.setNodeId(node.getId());
                nodeResult.setNodeName(node.getType());
                nodeResult.setInput(toJson(stripRuntimeFields(nodeInput)));
                
                try {
                    NodeExecutor executor = executorFactory.getExecutor(node.getType());
                    Map<String, Object> rawOutput = executor.execute(node, nodeInput, eventCallback);
                    Map<String, Object> output = stripRuntimeFields(rawOutput);
                    
                    nodeOutputs.put(node.getId(), output);
                    
                    nodeResult.setStatus("SUCCESS");
                    nodeResult.setOutput(toJson(output));
                    
                    long nodeEndTime = System.currentTimeMillis();
                    int nodeDuration = (int) (nodeEndTime - nodeStartTime);
                    nodeResult.setDuration(nodeDuration);
                    markSnapshotSuccess(snapshot, output, nodeDuration, nodeEndTime);
                    
                    if (eventCallback != null) {
                        Map<String, Object> eventData = new HashMap<>();
                        eventData.put("input", stripRuntimeFields(nodeInput));
                        eventData.put("output", output);
                        eventData.put("duration", nodeDuration);
                        eventCallback.accept(ExecutionEvent.nodeSuccess(node.getId(), node.getType(), eventData, nodeDuration));
                    }
                    
                    currentInput = output;
                    
                    // 条件分支路由
                    if ("condition".equals(node.getType())) {
                        String selectedBranch = (String) rawOutput.get("__selectedBranch__");
                        markSkippedNodes(node.getId(), selectedBranch, edgesBySource, edgesByTarget, skippedNodes);
                    }
                    
                } catch (Exception e) {
                    log.error("节点执行失败: {}", node.getId(), e);
                    nodeResult.setStatus("FAILED");
                    nodeResult.setError(e.getMessage());
                    status = "FAILED";
                    errorMessage = "节点 " + node.getId() + " 执行失败: " + e.getMessage();
                    markSnapshotFailed(snapshot, e.getMessage(), (int) (System.currentTimeMillis() - nodeStartTime));
                    
                    if (eventCallback != null) {
                        eventCallback.accept(ExecutionEvent.nodeError(node.getId(), node.getType(), e.getMessage()));
                    }
                    
                    throw e;
                } finally {
                    long nodeEndTime = System.currentTimeMillis();
                    nodeResult.setDuration((int) (nodeEndTime - nodeStartTime));
                    nodeResults.add(nodeResult);
                }
            }
            
            outputData = toJson(stripRuntimeFields(currentInput));
            
        } catch (Exception e) {
            status = "FAILED";
            if (errorMessage == null) {
                errorMessage = e.getMessage();
            }
        }
        
        long endTime = System.currentTimeMillis();
        int duration = (int) (endTime - startTime);
        
        if (eventCallback != null) {
            eventCallback.accept(ExecutionEvent.workflowComplete(status, currentInput, duration));
        }
        
        record.setFlowId(flowId);
        String inputDataJson = buildInputDataJson(inputData);
        log.info("保存执行记录 - inputData: {}", inputDataJson);
        log.info("保存执行记录 - outputData: {}", outputData);
        record.setInputData(inputDataJson);
        record.setOutputData(outputData);
        record.setStatus(status);
        record.setNodeResults(toJson(nodeResults));
        record.setErrorMessage(errorMessage);
        record.setDuration(duration);
        executionRecordMapper.updateById(record);
        
        ExecutionResponse response = new ExecutionResponse();
        response.setExecutionId(record.getId());
        response.setStatus(status);
        response.setInputData(inputDataJson);
        response.setNodeResults(nodeResults);
        response.setOutputData(outputData);
        response.setDuration(duration);
        response.setErrorMessage(errorMessage);
        
        return response;
    }
    
    @Override
    public String getEngineType() {
        return "dag";
    }
    
    /**
     * 根据前置节点输出解析节点输入，并注入工作流运行时上下文。
     */
    private Map<String, Object> resolveNodeInput(WorkflowNode node,
                                                   Map<String, Map<String, Object>> nodeOutputs,
                                                   Map<String, List<WorkflowEdge>> edgesByTarget,
                                                   Map<String, Object> fallbackInput) {
        List<WorkflowEdge> incomingEdges = edgesByTarget.get(node.getId());
        if (incomingEdges == null || incomingEdges.isEmpty()) {
            return withRuntimeContext(fallbackInput, nodeOutputs);
        }
        Map<String, Object> resolved = new LinkedHashMap<>();
        for (WorkflowEdge edge : incomingEdges) {
            Map<String, Object> sourceOutput = nodeOutputs.get(edge.getSource());
            if (sourceOutput != null) {
                resolved.putAll(sourceOutput);
            }
        }
        if (resolved.isEmpty()) {
            return withRuntimeContext(fallbackInput, nodeOutputs);
        }
        return withRuntimeContext(resolved, nodeOutputs);
    }

    private Map<String, Object> withRuntimeContext(Map<String, Object> input,
                                                   Map<String, Map<String, Object>> nodeOutputs) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (input != null) {
            result.putAll(input);
        }
        result.put("__nodeOutputs__", nodeOutputs);
        return result;
    }
    
    /**
     * 标记条件分支中未选中路径的所有下游节点为跳过状态
     */
    private void markSkippedNodes(String conditionNodeId,
                                   String selectedBranch,
                                   Map<String, List<WorkflowEdge>> edgesBySource,
                                   Map<String, List<WorkflowEdge>> edgesByTarget,
                                   Set<String> skippedNodes) {
        List<WorkflowEdge> outEdges = edgesBySource.get(conditionNodeId);
        if (outEdges == null || outEdges.isEmpty()) {
            return;
        }
        
        // 判断出边是否使用了 sourceHandle（分支标识）
        boolean hasHandles = outEdges.stream().anyMatch(e -> e.getSourceHandle() != null);
        if (!hasHandles) {
            return;
        }
        
        // 收集活跃和非活跃分支的直接目标
        Set<String> activeTargets = new HashSet<>();
        Set<String> inactiveTargets = new HashSet<>();
        
        for (WorkflowEdge edge : outEdges) {
            String handle = edge.getSourceHandle();
            if (handle != null && handle.equals(selectedBranch)) {
                activeTargets.add(edge.getTarget());
            } else if (handle == null && "default".equals(selectedBranch)) {
                activeTargets.add(edge.getTarget());
            } else {
                inactiveTargets.add(edge.getTarget());
            }
        }
        
        // 递归标记非活跃分支下游节点
        for (String target : inactiveTargets) {
            if (!activeTargets.contains(target)) {
                markDownstreamSkipped(target, skippedNodes, edgesBySource, edgesByTarget);
            }
        }
    }
    
    /**
     * 递归标记下游节点为跳过。仅当节点所有入边都来自已跳过节点时才标记。
     */
    private void markDownstreamSkipped(String nodeId,
                                        Set<String> skippedNodes,
                                        Map<String, List<WorkflowEdge>> edgesBySource,
                                        Map<String, List<WorkflowEdge>> edgesByTarget) {
        if (skippedNodes.contains(nodeId)) {
            return;
        }
        
        // 检查是否所有入边的源节点都已被跳过（或尚无输出）
        List<WorkflowEdge> incomingEdges = edgesByTarget.get(nodeId);
        if (incomingEdges != null && incomingEdges.size() > 1) {
            for (WorkflowEdge edge : incomingEdges) {
                if (!skippedNodes.contains(edge.getSource())) {
                    // 有活跃路径可达此节点，不跳过
                    return;
                }
            }
        }
        
        skippedNodes.add(nodeId);
        log.info("标记节点 [{}] 为跳过（条件分支未选中路径）", nodeId);
        
        // 递归标记下游
        List<WorkflowEdge> outEdges = edgesBySource.get(nodeId);
        if (outEdges != null) {
            for (WorkflowEdge edge : outEdges) {
                markDownstreamSkipped(edge.getTarget(), skippedNodes, edgesBySource, edgesByTarget);
            }
        }
    }

    private ExecutionRecord createRunningRecord(Long flowId, String inputData) {
        ExecutionRecord record = new ExecutionRecord();
        record.setFlowId(flowId);
        record.setInputData(buildInputDataJson(inputData));
        record.setStatus("RUNNING");
        record.setNodeResults("[]");
        record.setDuration(0);
        executionRecordMapper.insert(record);
        return record;
    }
    private EdgeIndexes buildEdgeIndexes(List<WorkflowEdge> edges) {
        Map<String, List<WorkflowEdge>> edgesBySource = new HashMap<>();
        Map<String, List<WorkflowEdge>> edgesByTarget = new HashMap<>();
        for (WorkflowEdge edge : edges) {
            edgesBySource.computeIfAbsent(edge.getSource(), k -> new ArrayList<>()).add(edge);
            edgesByTarget.computeIfAbsent(edge.getTarget(), k -> new ArrayList<>()).add(edge);
        }
        return new EdgeIndexes(edgesBySource, edgesByTarget);
    }

    private ExecutionSnapshot createOrUpdateSnapshotStart(Long executionId,
                                                           Long flowId,
                                                           WorkflowNode node,
                                                           Map<String, Object> nodeInput,
                                                           int executionOrder,
                                                           long startedAtMillis) {
        if (executionSnapshotMapper == null || executionId == null) {
            return null;
        }

        ExecutionSnapshot snapshot = executionSnapshotMapper.selectByExecutionIdAndNodeId(executionId, node.getId());
        if (snapshot == null) {
            snapshot = new ExecutionSnapshot();
            snapshot.setExecutionId(executionId);
            snapshot.setFlowId(flowId);
            snapshot.setNodeId(node.getId());
            snapshot.setNodeType(node.getType());
            snapshot.setNodeName(resolveNodeName(node));
            snapshot.setRetryCount(0);
            snapshot.setExecutionOrder(executionOrder);
        } else {
            snapshot.setRetryCount(snapshot.getRetryCount() == null ? 1 : snapshot.getRetryCount() + 1);
        }

        snapshot.setNodeType(node.getType());
        snapshot.setNodeName(resolveNodeName(node));
        snapshot.setExecutionOrder(executionOrder);
        snapshot.setStatus("RUNNING");
        snapshot.setInputData(toJson(stripRuntimeFields(nodeInput)));
        snapshot.setOutputData(null);
        snapshot.setErrorMessage(null);
        snapshot.setStartedAt(LocalDateTime.now());
        snapshot.setCompletedAt(null);
        snapshot.setDuration(null);

        if (snapshot.getId() == null) {
            executionSnapshotMapper.insert(snapshot);
        } else {
            executionSnapshotMapper.updateById(snapshot);
        }
        return snapshot;
    }

    private void markSnapshotSuccess(ExecutionSnapshot snapshot,
                                     Map<String, Object> output,
                                     int duration,
                                     long completedAtMillis) {
        if (executionSnapshotMapper == null || snapshot == null || snapshot.getId() == null) {
            return;
        }
        snapshot.setStatus("SUCCESS");
        snapshot.setOutputData(toJson(stripRuntimeFields(output)));
        snapshot.setErrorMessage(null);
        snapshot.setCompletedAt(LocalDateTime.now());
        snapshot.setDuration(duration);
        executionSnapshotMapper.updateById(snapshot);
    }

    private void markSnapshotFailed(ExecutionSnapshot snapshot, String errorMessage, int duration) {
        if (executionSnapshotMapper == null || snapshot == null || snapshot.getId() == null) {
            return;
        }
        snapshot.setStatus("FAILED");
        snapshot.setErrorMessage(errorMessage);
        snapshot.setCompletedAt(LocalDateTime.now());
        snapshot.setDuration(duration);
        executionSnapshotMapper.updateById(snapshot);
    }

    private String resolveResumeStartNodeId(List<WorkflowNode> sortedNodes,
                                            Map<String, ExecutionSnapshot> snapshotsByNode,
                                            ResumeExecutionRequest request) {
        if (request.getStartNodeId() != null && !request.getStartNodeId().isBlank()) {
            return request.getStartNodeId();
        }

        for (WorkflowNode node : sortedNodes) {
            ExecutionSnapshot snapshot = snapshotsByNode.get(node.getId());
            if (snapshot != null && "FAILED".equals(snapshot.getStatus())) {
                return node.getId();
            }
        }

        for (WorkflowNode node : sortedNodes) {
            ExecutionSnapshot snapshot = snapshotsByNode.get(node.getId());
            if (snapshot == null || (!"SUCCESS".equals(snapshot.getStatus()) && !"SKIPPED".equals(snapshot.getStatus()))) {
                return node.getId();
            }
        }

        return null;
    }

    private ResumeState buildResumeState(List<WorkflowNode> sortedNodes,
                                         Map<String, ExecutionSnapshot> snapshotsByNode,
                                         EdgeIndexes indexes,
                                         String startNodeId,
                                         boolean skipSuccessNodes) {
        ResumeState state = new ResumeState();
        state.startNodeId = startNodeId;
        if (!skipSuccessNodes) {
            return state;
        }

        for (WorkflowNode node : sortedNodes) {
            if (node.getId().equals(startNodeId)) {
                break;
            }

            ExecutionSnapshot snapshot = snapshotsByNode.get(node.getId());
            if (snapshot == null || !"SUCCESS".equals(snapshot.getStatus())) {
                continue;
            }

            Map<String, Object> output = parseJsonObject(snapshot.getOutputData());
            state.nodeOutputs.put(node.getId(), output);
            state.latestOutput = output;
            state.restoredNodeResults.add(nodeResultFromSnapshot(snapshot));

            if ("condition".equals(node.getType())) {
                String selectedBranch = output.get("__selectedBranch__") instanceof String
                        ? (String) output.get("__selectedBranch__")
                        : "default";
                markSkippedNodes(node.getId(), selectedBranch, indexes.edgesBySource, indexes.edgesByTarget, state.skippedNodes);
            }
        }

        return state;
    }

    private ExecutionResponse responseFromRecord(ExecutionRecord record, List<ExecutionSnapshot> snapshots) {
        ExecutionResponse response = new ExecutionResponse();
        response.setExecutionId(record.getId());
        response.setStatus(record.getStatus());
        response.setInputData(record.getInputData());
        response.setOutputData(record.getOutputData());
        response.setDuration(record.getDuration());
        response.setErrorMessage(record.getErrorMessage());

        List<ExecutionResponse.NodeResult> nodeResults;
        if (record.getNodeResults() != null && !record.getNodeResults().isBlank()) {
            nodeResults = JSON.parseArray(record.getNodeResults(), ExecutionResponse.NodeResult.class);
        } else {
            nodeResults = snapshots.stream().map(this::nodeResultFromSnapshot).collect(Collectors.toList());
        }
        response.setNodeResults(nodeResults);
        return response;
    }

    private ExecutionResponse.NodeResult nodeResultFromSnapshot(ExecutionSnapshot snapshot) {
        ExecutionResponse.NodeResult nodeResult = new ExecutionResponse.NodeResult();
        nodeResult.setNodeId(snapshot.getNodeId());
        nodeResult.setNodeName(snapshot.getNodeType());
        nodeResult.setStatus(snapshot.getStatus());
        nodeResult.setInput(snapshot.getInputData());
        nodeResult.setOutput(snapshot.getOutputData());
        nodeResult.setDuration(snapshot.getDuration() != null ? snapshot.getDuration() : 0);
        nodeResult.setError(snapshot.getErrorMessage());
        return nodeResult;
    }

    private void saveModifiedVariables(Long executionId, Map<String, Object> modifiedVariables) {
        if (executionVariableMapper == null || executionId == null || modifiedVariables == null || modifiedVariables.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Object> entry : modifiedVariables.entrySet()) {
            ExecutionVariable variable = executionVariableMapper.selectByExecutionIdAndVariableName(executionId, entry.getKey());
            if (variable == null) {
                variable = new ExecutionVariable();
                variable.setExecutionId(executionId);
                variable.setVariableName(entry.getKey());
                variable.setVariableType(resolveVariableType(entry.getValue()));
                variable.setVariableValue(toJson(entry.getValue()));
                variable.setIsModified(1);
                executionVariableMapper.insert(variable);
            } else {
                variable.setVariableType(resolveVariableType(entry.getValue()));
                variable.setVariableValue(toJson(entry.getValue()));
                variable.setIsModified(1);
                executionVariableMapper.updateById(variable);
            }
        }
    }

    private String resolveVariableType(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number) {
            return "NUMBER";
        }
        if (value instanceof Boolean) {
            return "BOOLEAN";
        }
        if (value instanceof Map || value instanceof List) {
            return "JSON";
        }
        return "STRING";
    }

    private String resolveNodeName(WorkflowNode node) {
        if (node.getData() != null) {
            Object label = node.getData().get("label");
            if (label instanceof String && !((String) label).isBlank()) {
                return (String) label;
            }
        }
        return node.getType();
    }

    private String buildInputDataJson(String inputData) {
        Map<String, Object> inputDataMap = new HashMap<>();
        inputDataMap.put("input", inputData);
        return toJson(inputDataMap);
    }

    private String extractInputValue(String inputDataJson) {
        Map<String, Object> input = parseJsonObject(inputDataJson);
        Object inputValue = input.get("input");
        return inputValue != null ? String.valueOf(inputValue) : "";
    }

    private Map<String, Object> parseJsonObject(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return new LinkedHashMap<>(JSON.parseObject(json));
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String toJson(Object value) {
        return JSON.toJSONString(stripRuntimeValue(value, new IdentityHashMap<>()));
    }

    private Map<String, Object> stripRuntimeFields(Map<String, Object> value) {
        @SuppressWarnings("unchecked")
        Map<String, Object> stripped = (Map<String, Object>) stripRuntimeValue(value, new IdentityHashMap<>());
        return stripped;
    }

    private Object stripRuntimeValue(Object value, IdentityHashMap<Object, Boolean> seen) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (seen.containsKey(value)) {
            return "[Circular]";
        }

        if (value instanceof Map<?, ?> map) {
            seen.put(value, Boolean.TRUE);
            Map<String, Object> stripped = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if ("__nodeOutputs__".equals(key)) {
                    continue;
                }
                stripped.put(key, stripRuntimeValue(entry.getValue(), seen));
            }
            seen.remove(value);
            return stripped;
        }

        if (value instanceof List<?> list) {
            seen.put(value, Boolean.TRUE);
            List<Object> stripped = new ArrayList<>();
            for (Object item : list) {
                stripped.add(stripRuntimeValue(item, seen));
            }
            seen.remove(value);
            return stripped;
        }

        return value;
    }

    private static class EdgeIndexes {
        private final Map<String, List<WorkflowEdge>> edgesBySource;
        private final Map<String, List<WorkflowEdge>> edgesByTarget;

        private EdgeIndexes(Map<String, List<WorkflowEdge>> edgesBySource,
                            Map<String, List<WorkflowEdge>> edgesByTarget) {
            this.edgesBySource = edgesBySource;
            this.edgesByTarget = edgesByTarget;
        }
    }

    private static class ResumeState {
        private String startNodeId;
        private final List<ExecutionResponse.NodeResult> restoredNodeResults = new ArrayList<>();
        private final Map<String, Map<String, Object>> nodeOutputs = new HashMap<>();
        private final Set<String> skippedNodes = new HashSet<>();
        private Map<String, Object> latestOutput = new HashMap<>();
        private Map<String, Object> modifiedVariables = Collections.emptyMap();
    }
}
