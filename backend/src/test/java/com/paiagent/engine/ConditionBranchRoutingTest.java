package com.paiagent.engine;

import com.alibaba.fastjson2.JSON;
import com.paiagent.dto.ExecutionResponse;
import com.paiagent.engine.dag.DAGParser;
import com.paiagent.engine.executor.NodeExecutor;
import com.paiagent.engine.executor.NodeExecutorFactory;
import com.paiagent.engine.executor.impl.ConditionNodeExecutor;
import com.paiagent.engine.executor.impl.InputNodeExecutor;
import com.paiagent.engine.model.WorkflowConfig;
import com.paiagent.engine.model.WorkflowEdge;
import com.paiagent.engine.model.WorkflowNode;
import com.paiagent.entity.ExecutionRecord;
import com.paiagent.entity.Workflow;
import com.paiagent.mapper.ExecutionRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 测试 WorkflowEngine 的条件分支路由功能
 */
class ConditionBranchRoutingTest {

    private WorkflowEngine engine;
    private ExecutionRecordMapper executionRecordMapper;

    @BeforeEach
    void setUp() {
        engine = new WorkflowEngine();

        DAGParser dagParser = new DAGParser();
        ReflectionTestUtils.setField(engine, "dagParser", dagParser);
        ReflectionTestUtils.setField(engine, "workflowConfigParser", new WorkflowConfigParser());

        // 注册执行器：模拟 LLM 节点解析输入 JSON 字符串为 Map
        List<NodeExecutor> executors = List.of(
                new InputNodeExecutor(),
                new ConditionNodeExecutor(),
                new MockLlmNodeExecutor(),
                new SimpleOutputNodeExecutor("branchA"),
                new SimpleOutputNodeExecutor("branchB"),
                new SimpleOutputNodeExecutor("branchDefault")
        );
        NodeExecutorFactory factory = new NodeExecutorFactory(executors);
        ReflectionTestUtils.setField(engine, "executorFactory", factory);

        executionRecordMapper = mock(ExecutionRecordMapper.class);
        when(executionRecordMapper.insert(any(ExecutionRecord.class))).thenReturn(1);
        ReflectionTestUtils.setField(engine, "executionRecordMapper", executionRecordMapper);
    }

    @Test
    void testConditionBranchSelectsFirstBranch() {
        // 工作流: input -> mockLlm -> condition -> branchA / branchB
        // 条件: status == "success" 走 branchA
        Workflow workflow = buildConditionalWorkflow();

        ExecutionResponse response = engine.execute(workflow, "{\"status\":\"success\"}");

        assertEquals("SUCCESS", response.getStatus());

        // branchA 应该被执行
        boolean branchAExecuted = response.getNodeResults().stream()
                .anyMatch(r -> "branchA".equals(r.getNodeName()));
        assertTrue(branchAExecuted, "branchA should be executed");

        // branchB 应该被跳过
        boolean branchBExecuted = response.getNodeResults().stream()
                .anyMatch(r -> "branchB".equals(r.getNodeName()));
        assertFalse(branchBExecuted, "branchB should be skipped");
    }

    @Test
    void testConditionBranchSelectsSecondBranch() {
        Workflow workflow = buildConditionalWorkflow();

        ExecutionResponse response = engine.execute(workflow, "{\"status\":\"failed\"}");

        assertEquals("SUCCESS", response.getStatus());

        // branchB 应该被执行
        boolean branchBExecuted = response.getNodeResults().stream()
                .anyMatch(r -> "branchB".equals(r.getNodeName()));
        assertTrue(branchBExecuted, "branchB should be executed");

        // branchA 应该被跳过
        boolean branchAExecuted = response.getNodeResults().stream()
                .anyMatch(r -> "branchA".equals(r.getNodeName()));
        assertFalse(branchAExecuted, "branchA should be skipped");
    }

    @Test
    void testConditionBranchDefaultWhenNoMatch() {
        Workflow workflow = buildConditionalWorkflowWithDefault();

        ExecutionResponse response = engine.execute(workflow, "{\"status\":\"unknown\"}");

        assertEquals("SUCCESS", response.getStatus());

        // branchDefault 应该被执行（default 分支）
        boolean defaultExecuted = response.getNodeResults().stream()
                .anyMatch(r -> "branchDefault".equals(r.getNodeName()));
        assertTrue(defaultExecuted, "default branch should be executed");

        // branchA 和 branchB 应该被跳过
        boolean branchAExecuted = response.getNodeResults().stream()
                .anyMatch(r -> "branchA".equals(r.getNodeName()));
        assertFalse(branchAExecuted, "branchA should be skipped");
    }

    @Test
    void testLinearWorkflowUnaffected() {
        // 不包含条件节点的线性工作流应该不受影响
        Workflow workflow = buildLinearWorkflow();

        ExecutionResponse response = engine.execute(workflow, "hello");

        assertEquals("SUCCESS", response.getStatus());
        assertEquals(2, response.getNodeResults().size());
    }

    @Test
    void resolveNodeInputAddsRuntimeContextWhenNodeHasNoIncomingEdges() {
        WorkflowNode node = createNode("node-input", "input", null);
        Map<String, Map<String, Object>> nodeOutputs = new HashMap<>();
        nodeOutputs.put("previous", Map.of("output", "already available"));
        Map<String, Object> fallbackInput = new LinkedHashMap<>();
        fallbackInput.put("input", "hello");

        Map<String, Object> resolved = ReflectionTestUtils.invokeMethod(
                engine,
                "resolveNodeInput",
                node,
                nodeOutputs,
                Collections.emptyMap(),
                fallbackInput
        );

        assertEquals("hello", resolved.get("input"));
        assertSame(nodeOutputs, resolved.get("__nodeOutputs__"));
        assertFalse(fallbackInput.containsKey("__nodeOutputs__"), "fallbackInput should not be mutated");
    }

    @Test
    void resolveNodeInputKeepsRuntimeContextWhenIncomingSourceHasNoOutput() {
        WorkflowNode node = createNode("node-target", "output", null);
        WorkflowEdge missingSourceEdge = createEdge("e1", "node-missing", "node-target", null, null);
        Map<String, List<WorkflowEdge>> edgesByTarget = Map.of("node-target", List.of(missingSourceEdge));
        Map<String, Map<String, Object>> nodeOutputs = new HashMap<>();
        nodeOutputs.put("other-node", Map.of("output", "global output"));
        Map<String, Object> fallbackInput = new LinkedHashMap<>();
        fallbackInput.put("input", "fallback");

        Map<String, Object> resolved = ReflectionTestUtils.invokeMethod(
                engine,
                "resolveNodeInput",
                node,
                nodeOutputs,
                edgesByTarget,
                fallbackInput
        );

        assertEquals("fallback", resolved.get("input"));
        assertSame(nodeOutputs, resolved.get("__nodeOutputs__"));
        assertFalse(fallbackInput.containsKey("__nodeOutputs__"), "fallbackInput should not be mutated");
    }

    // --- Workflow Builders ---

    private Workflow buildConditionalWorkflow() {
        List<WorkflowNode> nodes = new ArrayList<>();
        List<WorkflowEdge> edges = new ArrayList<>();

        // Input node
        WorkflowNode inputNode = createNode("node-input", "input", null);
        nodes.add(inputNode);

        // Mock LLM node: 解析 input JSON 字符串为结构化 map
        WorkflowNode llmNode = createNode("node-llm", "mockLlm", null);
        nodes.add(llmNode);

        // Condition node
        List<Map<String, Object>> conditions = List.of(
                Map.of("id", "condition_0", "field", "status", "operator", "eq", "value", "success"),
                Map.of("id", "condition_1", "field", "status", "operator", "eq", "value", "failed")
        );
        WorkflowNode condNode = createNode("node-condition", "condition",
                Map.of("conditions", conditions));
        nodes.add(condNode);

        // Branch A node
        WorkflowNode branchANode = createNode("node-branchA", "branchA", null);
        nodes.add(branchANode);

        // Branch B node
        WorkflowNode branchBNode = createNode("node-branchB", "branchB", null);
        nodes.add(branchBNode);

        // Edges: input -> llm -> condition -> branchA/branchB
        edges.add(createEdge("e0", "node-input", "node-llm", null, null));
        edges.add(createEdge("e1", "node-llm", "node-condition", null, null));
        edges.add(createEdge("e2", "node-condition", "node-branchA", "condition_0", null));
        edges.add(createEdge("e3", "node-condition", "node-branchB", "condition_1", null));

        WorkflowConfig config = new WorkflowConfig();
        config.setNodes(nodes);
        config.setEdges(edges);

        Workflow workflow = new Workflow();
        workflow.setId(1L);
        workflow.setFlowData(JSON.toJSONString(config));
        return workflow;
    }

    private Workflow buildConditionalWorkflowWithDefault() {
        List<WorkflowNode> nodes = new ArrayList<>();
        List<WorkflowEdge> edges = new ArrayList<>();

        WorkflowNode inputNode = createNode("node-input", "input", null);
        nodes.add(inputNode);

        WorkflowNode llmNode = createNode("node-llm", "mockLlm", null);
        nodes.add(llmNode);

        List<Map<String, Object>> conditions = List.of(
                Map.of("id", "condition_0", "field", "status", "operator", "eq", "value", "success"),
                Map.of("id", "condition_1", "field", "status", "operator", "eq", "value", "failed")
        );
        WorkflowNode condNode = createNode("node-condition", "condition",
                Map.of("conditions", conditions));
        nodes.add(condNode);

        WorkflowNode branchANode = createNode("node-branchA", "branchA", null);
        nodes.add(branchANode);

        WorkflowNode branchBNode = createNode("node-branchB", "branchB", null);
        nodes.add(branchBNode);

        WorkflowNode branchDefaultNode = createNode("node-branchDefault", "branchDefault", null);
        nodes.add(branchDefaultNode);

        edges.add(createEdge("e0", "node-input", "node-llm", null, null));
        edges.add(createEdge("e1", "node-llm", "node-condition", null, null));
        edges.add(createEdge("e2", "node-condition", "node-branchA", "condition_0", null));
        edges.add(createEdge("e3", "node-condition", "node-branchB", "condition_1", null));
        edges.add(createEdge("e4", "node-condition", "node-branchDefault", "default", null));

        WorkflowConfig config = new WorkflowConfig();
        config.setNodes(nodes);
        config.setEdges(edges);

        Workflow workflow = new Workflow();
        workflow.setId(2L);
        workflow.setFlowData(JSON.toJSONString(config));
        return workflow;
    }

    private Workflow buildLinearWorkflow() {
        List<WorkflowNode> nodes = new ArrayList<>();
        List<WorkflowEdge> edges = new ArrayList<>();

        WorkflowNode inputNode = createNode("node-input", "input", null);
        nodes.add(inputNode);

        WorkflowNode outputNode = createNode("node-branchA", "branchA", null);
        nodes.add(outputNode);

        edges.add(createEdge("e1", "node-input", "node-branchA", null, null));

        WorkflowConfig config = new WorkflowConfig();
        config.setNodes(nodes);
        config.setEdges(edges);

        Workflow workflow = new Workflow();
        workflow.setId(3L);
        workflow.setFlowData(JSON.toJSONString(config));
        return workflow;
    }

    private WorkflowNode createNode(String id, String type, Map<String, Object> data) {
        WorkflowNode node = new WorkflowNode();
        node.setId(id);
        node.setType(type);
        node.setData(data != null ? new HashMap<>(data) : new HashMap<>());
        return node;
    }

    private WorkflowEdge createEdge(String id, String source, String target,
                                     String sourceHandle, String targetHandle) {
        WorkflowEdge edge = new WorkflowEdge();
        edge.setId(id);
        edge.setSource(source);
        edge.setTarget(target);
        edge.setSourceHandle(sourceHandle);
        edge.setTargetHandle(targetHandle);
        return edge;
    }

    /**
     * 模拟 LLM 节点：解析 input 字段中的 JSON 字符串为结构化 Map
     */
    static class MockLlmNodeExecutor implements NodeExecutor {
        @Override
        public Map<String, Object> execute(WorkflowNode node, Map<String, Object> input) {
            Object inputValue = input.get("input");
            if (inputValue instanceof String) {
                try {
                    Map<String, Object> parsed = JSON.parseObject((String) inputValue, Map.class);
                    return new HashMap<>(parsed);
                } catch (Exception e) {
                    // 非 JSON 字符串，直接返回
                }
            }
            return new HashMap<>(input);
        }

        @Override
        public String getSupportedNodeType() {
            return "mockLlm";
        }
    }

    /**
     * 简单的模拟节点执行器，只透传输入并添加标记
     */
    static class SimpleOutputNodeExecutor implements NodeExecutor {
        private final String nodeType;

        SimpleOutputNodeExecutor(String nodeType) {
            this.nodeType = nodeType;
        }

        @Override
        public Map<String, Object> execute(WorkflowNode node, Map<String, Object> input) {
            Map<String, Object> output = new HashMap<>(input);
            output.put("executedBy", nodeType);
            return output;
        }

        @Override
        public String getSupportedNodeType() {
            return nodeType;
        }
    }
}
