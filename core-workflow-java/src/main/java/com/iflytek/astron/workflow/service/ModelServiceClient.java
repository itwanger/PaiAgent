package com.iflytek.astron.workflow.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Model service client
 * Calls console-hub's model API to execute LLM inference
 */
@Slf4j
@Service
public class ModelServiceClient {
    
    @Value("${workflow.services.model-service-url}")
    private String modelServiceUrl;
    
    private final OkHttpClient httpClient;
    
    public ModelServiceClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(300, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * Call LLM for chat completion
     * 
     * @param modelId model ID from database
     * @param prompt user prompt (may contain variables)
     * @return LLM response text
     * @throws IOException if API call fails
     */
    public String chatCompletion(Integer modelId, String prompt) throws IOException {
        // TODO: Mock implementation for testing - remove this and uncomment real implementation
        log.warn("Using MOCK LLM response for testing");
        log.info("Mock LLM call: modelId={}, promptLength={}", modelId, prompt.length());
        log.debug("Mock LLM prompt: {}", prompt);
        
        String mockResponse = """
                大家好，欢迎来到《沉默王二的编程人生》播客！今天我们要聊的是一个在编程世界里举足轻重的语言——Java。
                
                Java，这个由Sun Microsystems在1995年推出的编程语言，可以说是改变了整个软件开发的格局。它的设计理念"一次编写，到处运行"让无数开发者为之倾倒。
                
                Java不仅仅是一门语言，它更是一个完整的生态系统。从企业级应用到安卓开发，从大数据处理到云计算，Java的身影无处不在。
                
                作为一名资深的Java开发者，我深深感受到Java带给我们的不仅是技术上的成长，更是一种解决问题的思维方式。它的面向对象特性、强大的类库支持，以及庞大的开源社区，都让Java成为了许多程序员的首选。
                
                好了，关于Java的介绍就到这里。如果你对Java编程感兴趣，记得关注我的播客，我们下期再见！
                """;
        
        log.info("Mock LLM response length: {}", mockResponse.length());
        return mockResponse;
        
        /* Real implementation - uncomment when authentication is configured
        String url = modelServiceUrl + "/api/model/chat";
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("modelId", modelId);
        
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);
        requestBody.put("messages", List.of(message));
        
        String jsonBody = JSON.toJSONString(requestBody);
        
        log.info("Calling model service: url={}, modelId={}, promptLength={}", 
                url, modelId, prompt.length());
        log.debug("LLM request prompt: {}", prompt);
        
        RequestBody body = RequestBody.create(
                jsonBody, 
                MediaType.parse("application/json; charset=utf-8")
        );
        
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException("Model service call failed: " + response.code() + ", " + errorBody);
            }
            
            String responseBody = response.body().string();
            log.debug("Model service response: {}", responseBody);
            
            JSONObject jsonResponse = JSON.parseObject(responseBody);
            
            if (jsonResponse.containsKey("data")) {
                JSONObject data = jsonResponse.getJSONObject("data");
                if (data.containsKey("content")) {
                    String content = data.getString("content");
                    log.info("LLM response received, length: {}", content.length());
                    return content;
                }
            }
            
            if (jsonResponse.containsKey("choices")) {
                String content = jsonResponse.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content");
                log.info("LLM response received, length: {}", content.length());
                return content;
            }
            
            throw new IOException("Unexpected response format: " + responseBody);
        }
        */
    }
}
