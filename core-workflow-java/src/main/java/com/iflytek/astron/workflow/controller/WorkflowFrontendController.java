package com.iflytek.astron.workflow.controller;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.annotation.JsonProperty;
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
 * Frontend-compatible workflow execution controller
 * Provides API compatible with console-hub's workflow chat endpoint
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/workflow")
public class WorkflowFrontendController {
    
    private final WorkflowService workflowService;
    private final WorkflowEngine workflowEngine;
    
    public WorkflowFrontendController(WorkflowService workflowService, WorkflowEngine workflowEngine) {
        this.workflowService = workflowService;
        this.workflowEngine = workflowEngine;
    }
    
    /**
     * Frontend-compatible workflow chat stream endpoint
     * Endpoint: POST /api/v1/workflow/chat/stream
     * 
     * Request body:
     * {
     *   "flowId": "184736",
     *   "inputs": { "user_input": "介绍一下 Java" },
     *   "chatId": "some-chat-id",
     *   "userId": "user-id"
     * }
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter workflowChatStream(@RequestBody FrontendWorkflowRequest request) {
        log.info("Frontend workflow chat stream request: flowId={}, userId={}, chatId={}", 
                request.getFlowId(), request.getUserId(), request.getChatId());
        
        SseEmitter emitter = new SseEmitter(600_000L);
        
        CompletableFuture.runAsync(() -> {
            try {
                WorkflowDSL workflowDSL = workflowService.getWorkflowDSL(request.getFlowId());
                
                StreamCallback callback = new SseStreamCallback(emitter);
                
                workflowEngine.execute(workflowDSL, request.getInputs(), callback);
                
                sendEvent(emitter, "workflow_complete", Map.of("status", "success"));
                
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
                // Ignore - client may have disconnected
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
    
    /**
     * Frontend workflow request (compatible with console-hub format)
     */
    @Data
    public static class FrontendWorkflowRequest {
        
        @JsonProperty("flowId")
        private String flowId;
        
        @JsonProperty("inputs")
        private Map<String, Object> inputs;
        
        @JsonProperty("chatId")
        private String chatId;
        
        @JsonProperty("userId")
        private String userId;
        
        @JsonProperty("regen")
        private Boolean regen;
    }
}
