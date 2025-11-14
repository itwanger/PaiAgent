package com.iflytek.astron.workflow.service.llm;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * iFlytek Spark LLM Client
 * Directly connects to iFlytek Spark API via WebSocket (similar to Python's SparkChatAi)
 */
@Slf4j
@Service
public class SparkLLMClient {
    
    @Value("${spark.app-id:}")
    private String appId;
    
    @Value("${spark.api-key:}")
    private String apiKey;
    
    @Value("${spark.api-secret:}")
    private String apiSecret;
    
    @Value("${spark.api-url:wss://spark-api.xf-yun.com/v3.5/chat}")
    private String apiUrl;
    
    /**
     * Call Spark LLM via WebSocket
     * 
     * @param domain Model domain (e.g., "generalv3.5")
     * @param temperature Temperature parameter (0.0-1.0)
     * @param maxTokens Maximum tokens in response
     * @param messages List of conversation messages
     * @return LLM response text
     * @throws Exception if API call fails
     */
    public String chat(String domain, Double temperature, Integer maxTokens, List<Map<String, String>> messages) throws Exception {
        log.info("Calling Spark LLM: domain={}, temperature={}, maxTokens={}, messageCount={}", 
                domain, temperature, maxTokens, messages.size());
        
        // Create authenticated URL
        SparkChatAuth auth = new SparkChatAuth(apiUrl, apiKey, apiSecret);
        String authenticatedUrl = auth.createUrl();
        
        // Prepare request payload
        String payload = buildPayload(domain, temperature, maxTokens, messages);
        log.debug("Spark request payload: {}", payload);
        
        // Create WebSocket connection and send request
        CompletableFuture<String> responseFuture = new CompletableFuture<>();
        AtomicReference<StringBuilder> fullResponse = new AtomicReference<>(new StringBuilder());
        
        WebSocketClient client = new WebSocketClient(new URI(authenticatedUrl)) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                log.debug("WebSocket connection opened");
                send(payload);
            }
            
            @Override
            public void onMessage(String message) {
                try {
                    log.debug("Received message: {}", message);
                    JSONObject msg = JSON.parseObject(message);
                    
                    // Extract response data
                    JSONObject header = msg.getJSONObject("header");
                    int code = header.getInteger("code");
                    int status = header.getInteger("status");
                    
                    if (code != 0) {
                        String errorMsg = header.getString("message");
                        log.error("Spark API error: code={}, message={}", code, errorMsg);
                        responseFuture.completeExceptionally(
                            new Exception("Spark API error: " + errorMsg)
                        );
                        close();
                        return;
                    }
                    
                    // Extract content
                    JSONObject payload = msg.getJSONObject("payload");
                    JSONObject choices = payload.getJSONObject("choices");
                    JSONArray textArray = choices.getJSONArray("text");
                    
                    if (textArray != null && !textArray.isEmpty()) {
                        JSONObject textObj = textArray.getJSONObject(0);
                        String content = textObj.getString("content");
                        if (content != null && !content.isEmpty()) {
                            fullResponse.get().append(content);
                        }
                    }
                    
                    // Check if response is complete (status=2 means final chunk)
                    if (status == 2) {
                        String finalResponse = fullResponse.get().toString();
                        log.info("Spark LLM response complete, length: {}", finalResponse.length());
                        responseFuture.complete(finalResponse);
                        close();
                    }
                    
                } catch (Exception e) {
                    log.error("Error processing message: {}", message, e);
                    responseFuture.completeExceptionally(e);
                    close();
                }
            }
            
            @Override
            public void onClose(int code, String reason, boolean remote) {
                log.debug("WebSocket connection closed: code={}, reason={}, remote={}", code, reason, remote);
                if (!responseFuture.isDone()) {
                    responseFuture.completeExceptionally(
                        new Exception("WebSocket closed before receiving complete response")
                    );
                }
            }
            
            @Override
            public void onError(Exception ex) {
                log.error("WebSocket error", ex);
                responseFuture.completeExceptionally(ex);
            }
        };
        
        // Connect and wait for response (timeout: 60 seconds)
        client.connectBlocking(60, TimeUnit.SECONDS);
        String response = responseFuture.get(300, TimeUnit.SECONDS);
        
        return response;
    }
    
    /**
     * Build request payload for Spark API
     */
    private String buildPayload(String domain, Double temperature, Integer maxTokens, List<Map<String, String>> messages) {
        Map<String, Object> payload = new HashMap<>();
        
        // Header
        Map<String, Object> header = new HashMap<>();
        header.put("app_id", appId);
        header.put("uid", "workflow-user");
        payload.put("header", header);
        
        // Parameter
        Map<String, Object> parameter = new HashMap<>();
        Map<String, Object> chat = new HashMap<>();
        chat.put("domain", domain);
        chat.put("temperature", temperature != null ? temperature : 0.5);
        chat.put("max_tokens", maxTokens != null ? maxTokens : 2048);
        chat.put("top_k", 4);
        chat.put("auditing", "default");
        chat.put("question_type", "not_knowledge");
        parameter.put("chat", chat);
        payload.put("parameter", parameter);
        
        // Payload
        Map<String, Object> payloadData = new HashMap<>();
        Map<String, Object> message = new HashMap<>();
        message.put("text", messages);
        payloadData.put("message", message);
        payload.put("payload", payloadData);
        
        return JSON.toJSONString(payload);
    }
}
