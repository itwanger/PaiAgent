package com.paiagent.engine.langgraph;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.state.AgentState;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LangGraph4j 基础功能测试
 * 验证 LangGraph4j 依赖是否正确加载
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
class LangGraphBasicTest {
    
    @Test
    void testWorkflowStateCreation() {
        // 测试 WorkflowState 创建
        WorkflowState state = new WorkflowState();
        state.setInputData("test input");
        state.setStartTime(System.currentTimeMillis());
        
        assertEquals("test input", state.getInputData());
        assertEquals("RUNNING", state.getStatus());
        assertNotNull(state.getStartTime());
    }
    
    @Test
    void testWorkflowStateNodeOutput() {
        WorkflowState state = new WorkflowState();
        
        // 更新节点输出
        Map<String, Object> output1 = new HashMap<>();
        output1.put("result", "node1 result");
        state.updateNodeOutput("node1", output1, "SUCCESS");
        
        // 验证节点输出
        Map<String, Object> retrieved = state.getNodeOutput("node1");
        assertNotNull(retrieved);
        assertEquals("node1 result", retrieved.get("result"));
        assertEquals("node1", state.getCurrentNodeId());
    }
    
    @Test
    void testWorkflowStatePreviousOutput() {
        WorkflowState state = new WorkflowState();
        
        // 添加节点输出
        Map<String, Object> output1 = new HashMap<>();
        output1.put("data", "value1");
        state.updateNodeOutput("node1", output1, "SUCCESS");
        
        // 获取前一个节点输出
        Map<String, Object> previous = state.getPreviousNodeOutput();
        assertNotNull(previous);
        assertEquals("value1", previous.get("data"));
    }
    
    @Test
    void testLangGraphDependency() throws Exception {
        // 验证可以创建 StateGraph
        StateGraph<AgentState> graph = new StateGraph<>(AgentState::new);
        
        // 添加一个简单节点
        graph.addNode("test", state -> 
            java.util.concurrent.CompletableFuture.completedFuture(
                Map.of("message", "Hello LangGraph")
            )
        );
        
        // 设置入口点
        graph.addEdge(StateGraph.START, "test");
        graph.addEdge("test", StateGraph.END);
        
        // 编译图
        var compiled = graph.compile();
        
        // 验证图编译成功
        assertNotNull(compiled);
        
        System.out.println("✅ LangGraph4j 依赖加载成功！");
    }
}
