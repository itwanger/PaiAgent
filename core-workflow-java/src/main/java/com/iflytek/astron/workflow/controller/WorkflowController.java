package com.iflytek.astron.workflow.controller;

import com.alibaba.fastjson2.JSON;
import com.iflytek.astron.workflow.domain.Result;
import com.iflytek.astron.workflow.domain.WorkflowDSL;
import com.iflytek.astron.workflow.engine.WorkflowEngine;
import com.iflytek.astron.workflow.engine.node.StreamCallback;
import com.iflytek.astron.workflow.service.WorkflowService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Workflow execution controller
 * Provides REST API for workflow execution with SSE streaming
 * Compatible with Python version API: /workflow/v1/chat/completions
 */
@Slf4j
@RestController
@RequestMapping("/workflow/v1")
public class WorkflowController {
    
    private final WorkflowService workflowService;
    private final WorkflowEngine workflowEngine;
    
    public WorkflowController(WorkflowService workflowService, WorkflowEngine workflowEngine) {
        this.workflowService = workflowService;
        this.workflowEngine = workflowEngine;
    }
    
    /**
     * Execute workflow with SSE streaming
     * 
     * Endpoint: POST /workflow/v1/chat/completions (compatible with Python version)
     * Request body:
     * {
     *   "flow_id": "184736",
     *   "inputs": { "user_input": "介绍一下 Java" },
     *   "chatId": "some-chat-id",
     *   "regen": false
     * }
     * 
     * Response: SSE stream with events:
     * - node_start: Node execution started
     * - node_output: Node produced output
     * - node_end: Node execution completed
     * - workflow_complete: Workflow finished
     * - error: Error occurred
     */
    @PostMapping(value = "/chat/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter executeWorkflow(@RequestBody WorkflowRequest request) {
        log.info("========================================");
        log.info("🚀 [JAVA VERSION] Workflow execution request: flowId={}, inputs={}", request.getFlowId(), request.getInputs());
        log.info("========================================");
        
        SseEmitter emitter = new SseEmitter(600_000L);
        
        CompletableFuture.runAsync(() -> {
            try {
                WorkflowDSL workflowDSL = workflowService.getWorkflowDSL(request.getFlowId());
                
                StreamCallback callback = new SseStreamCallback(emitter);
                
                workflowEngine.execute(workflowDSL, request.getInputs(), callback);
                
                sendEvent(emitter, "workflow_complete", Map.of("status", "success"));
                
                log.info("🎉 [JAVA VERSION] Workflow completed successfully!");
                
                emitter.complete();
                
            } catch (Exception e) {
                log.error("Workflow execution failed: {}", e.getMessage(), e);
                try {
                    sendEvent(emitter, "error", Map.of(
                            "error", e.getClass().getSimpleName(),
                            "message", e.getMessage()
                    ));
                    emitter.completeWithError(e);
                } catch (IOException ex) {
                    log.error("Failed to send error event: {}", ex.getMessage());
                }
            }
        });
        
        emitter.onTimeout(() -> {
            log.warn("Workflow execution timeout");
            emitter.complete();
        });
        
        emitter.onError(e -> {
            log.error("SSE error: {}", e.getMessage(), e);
            emitter.completeWithError(e);
        });
        
        return emitter;
    }
    
    /**
     * SSE Stream callback implementation
     */
    private static class SseStreamCallback implements StreamCallback {
        
        private final SseEmitter emitter;
        
        public SseStreamCallback(SseEmitter emitter) {
            this.emitter = emitter;
        }
        
        @Override
        public void send(String eventType, Object data) {
            try {
                String jsonData = JSON.toJSONString(data);
                emitter.send(SseEmitter.event()
                        .name(eventType)
                        .data(jsonData));
            } catch (IOException e) {
                log.error("Failed to send SSE event: {}", e.getMessage(), e);
            }
        }
    }
    
    /**
     * Send an SSE event
     */
    private void sendEvent(SseEmitter emitter, String eventType, Object data) throws IOException {
        String jsonData = JSON.toJSONString(data);
        emitter.send(SseEmitter.event()
                .name(eventType)
                .data(jsonData));
    }
    
    @PostMapping("/protocol/update/{flowId}")
    public Result<Void> updateWorkflow(
            @PathVariable String flowId,
            @RequestBody WorkflowUpdateRequest request) {
        log.info("🔄 [JAVA VERSION] Workflow update request: flowId={}", flowId);
        
        try {
            workflowService.updateWorkflow(flowId, request);
            
            log.info("✅ [JAVA VERSION] Workflow updated successfully: flowId={}", flowId);
            
            return Result.success();
            
        } catch (Exception e) {
            log.error("❌ [JAVA VERSION] Workflow update failed: flowId={}, error={}", 
                     flowId, e.getMessage(), e);
            
            return Result.failure(e.getMessage());
        }
    }
    
    @Data
    public static class WorkflowUpdateRequest {
        
        private String id;
        private String name;
        private String description;
        private Map<String, Object> data;
        
        @com.fasterxml.jackson.annotation.JsonProperty("app_id")
        private String appId;
    }
    
    @Data
    public static class WorkflowRequest {
        
        @com.fasterxml.jackson.annotation.JsonProperty("flow_id")
        private String flowId;
        
        @com.fasterxml.jackson.annotation.JsonProperty("inputs")
        private Map<String, Object> inputs;
        
        @com.fasterxml.jackson.annotation.JsonProperty("chatId")
        private String chatId;
        
        @com.fasterxml.jackson.annotation.JsonProperty("regen")
        private Boolean regen;
    }
}
