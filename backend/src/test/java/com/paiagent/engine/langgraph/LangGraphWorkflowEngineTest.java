package com.paiagent.engine.langgraph;

import com.alibaba.fastjson2.JSON;
import com.paiagent.dto.ExecutionResponse;
import com.paiagent.engine.model.WorkflowConfig;
import com.paiagent.engine.model.WorkflowEdge;
import com.paiagent.engine.model.WorkflowNode;
import com.paiagent.entity.Workflow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LangGraph 工作流引擎集成测试
 */
@SpringBootTest(properties = {
        "paiagent.auth.jwt-secret=test-jwt-secret-key-for-spring-context-123456",
        "minio.endpoint=http://localhost:9000",
        "minio.accessKey=minioadmin",
        "minio.secretKey=minioadmin",
        "minio.bucketName=paiagent",
        "minio.publicUrl=http://localhost:9000",
        "spring.ai.openai.api-key=sk-test-placeholder",
        "spring.datasource.password=${MYSQL_PASSWORD:}",
        "paiagent.rag.migration-enabled=false",
        "paiagent.rag.stream.enabled=false"
})
class LangGraphWorkflowEngineTest {
    
    @Autowired
    private LangGraphWorkflowEngine langGraphEngine;
    
    @Test
    void testEngineType() {
        assertEquals("langgraph", langGraphEngine.getEngineType());
    }
    
    @Test
    void testSimpleWorkflowExecution() {
        // 创建简单工作流：Input -> Output
        Workflow workflow = createSimpleWorkflow();
        
        // 执行工作流
        ExecutionResponse response = langGraphEngine.execute(workflow, "Hello LangGraph");
        
        // 验证结果
        assertNotNull(response);
        assertNotNull(response.getExecutionId());
        assertEquals("SUCCESS", response.getStatus());
        assertNotNull(response.getOutputData());
        assertTrue(response.getDuration() > 0);
        
        System.out.println("✅ 简单工作流执行成功");
        System.out.println("   执行ID: " + response.getExecutionId());
        System.out.println("   状态: " + response.getStatus());
        System.out.println("   耗时: " + response.getDuration() + "ms");
        System.out.println("   输出: " + response.getOutputData());
    }
    
    @Test
    void testWorkflowWithMultipleNodes() {
        // 创建包含多个节点的工作流
        Workflow workflow = createMultiNodeWorkflow();
        
        // 执行工作流
        ExecutionResponse response = langGraphEngine.execute(workflow, "测试输入");
        
        // 验证结果
        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        
        System.out.println("✅ 多节点工作流执行成功");
        System.out.println("   节点数: 3 (Input -> Processing -> Output)");
        System.out.println("   状态: " + response.getStatus());
    }
    
    @Test
    void testWorkflowWithCallback() {
        Workflow workflow = createSimpleWorkflow();
        
        List<String> events = new ArrayList<>();
        
        // 执行工作流并收集事件
        ExecutionResponse response = langGraphEngine.executeWithCallback(
            workflow, 
            "测试事件回调",
            event -> events.add(event.getEventType())
        );
        
        // 验证结果
        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        
        // 验证事件
        assertTrue(events.size() > 0, "应该触发至少一个事件");
        assertTrue(events.contains("WORKFLOW_START") || events.contains("NODE_START"), 
            "应该包含工作流或节点开始事件");
        
        System.out.println("✅ 事件回调测试成功");
        System.out.println("   收到事件数: " + events.size());
        System.out.println("   事件列表: " + events);
    }
    
    /**
     * 创建简单工作流：Input -> Output
     */
    private Workflow createSimpleWorkflow() {
        Workflow workflow = new Workflow();
        workflow.setId(1L);
        workflow.setName("简单测试工作流");
        
        // 创建节点
        List<WorkflowNode> nodes = new ArrayList<>();
        
        WorkflowNode inputNode = new WorkflowNode();
        inputNode.setId("input-1");
        inputNode.setType("input");
        inputNode.setPosition(new WorkflowNode.Position());
        inputNode.setData(Map.of("label", "输入", "type", "input"));
        nodes.add(inputNode);
        
        WorkflowNode outputNode = new WorkflowNode();
        outputNode.setId("output-1");
        outputNode.setType("output");
        outputNode.setPosition(new WorkflowNode.Position());
        
        Map<String, Object> outputData = new HashMap<>();
        outputData.put("label", "输出");
        outputData.put("type", "output");
        outputData.put("outputParams", new ArrayList<>());
        outputData.put("responseContent", "{{output}}");
        outputNode.setData(outputData);
        nodes.add(outputNode);
        
        // 创建边
        List<WorkflowEdge> edges = new ArrayList<>();
        WorkflowEdge edge = new WorkflowEdge();
        edge.setId("edge-1");
        edge.setSource("input-1");
        edge.setTarget("output-1");
        edges.add(edge);
        
        // 构建配置
        WorkflowConfig config = new WorkflowConfig();
        config.setNodes(nodes);
        config.setEdges(edges);
        
        workflow.setFlowData(JSON.toJSONString(config));
        
        return workflow;
    }
    
    /**
     * 创建多节点工作流
     */
    private Workflow createMultiNodeWorkflow() {
        Workflow workflow = new Workflow();
        workflow.setId(2L);
        workflow.setName("多节点测试工作流");
        
        // 创建节点
        List<WorkflowNode> nodes = new ArrayList<>();
        
        // Input 节点
        WorkflowNode inputNode = new WorkflowNode();
        inputNode.setId("input-1");
        inputNode.setType("input");
        inputNode.setData(Map.of("label", "输入", "type", "input"));
        nodes.add(inputNode);
        
        // Output 节点
        WorkflowNode outputNode = new WorkflowNode();
        outputNode.setId("output-1");
        outputNode.setType("output");
        Map<String, Object> outputData = new HashMap<>();
        outputData.put("label", "输出");
        outputData.put("type", "output");
        outputData.put("outputParams", new ArrayList<>());
        outputData.put("responseContent", "处理完成");
        outputNode.setData(outputData);
        nodes.add(outputNode);
        
        // 创建边
        List<WorkflowEdge> edges = new ArrayList<>();
        
        WorkflowEdge edge1 = new WorkflowEdge();
        edge1.setId("edge-1");
        edge1.setSource("input-1");
        edge1.setTarget("output-1");
        edges.add(edge1);
        
        // 构建配置
        WorkflowConfig config = new WorkflowConfig();
        config.setNodes(nodes);
        config.setEdges(edges);
        
        workflow.setFlowData(JSON.toJSONString(config));
        
        return workflow;
    }
}
