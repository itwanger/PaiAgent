package com.iflytek.astron.workflow.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * AI Tools service client
 * Calls core-aitools for voice synthesis and other AI capabilities
 */
@Slf4j
@Service
public class AIToolsClient {
    
    @Value("${workflow.services.aitools-url}")
    private String aitoolsUrl;
    
    private final OkHttpClient httpClient;
    
    public AIToolsClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(300, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * Call voice synthesis tool
     * 
     * @param toolId tool ID (e.g., "tool@8b2262bef821000")
     * @param operationId operation ID (e.g., "超拟人合成-46EXFdLW")
     * @param text text to synthesize
     * @param vcn voice name (e.g., "x5_lingfeiyi_flow")
     * @param speed speech speed (0-100, default 50)
     * @return audio URL from MinIO
     * @throws IOException if API call fails
     */
    public String voiceSynthesis(String toolId, String operationId, String text, String vcn, Integer speed) throws IOException {
        String url = aitoolsUrl + "/api/tools/execute";
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("toolId", toolId);
        requestBody.put("operationId", operationId);
        
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("text", text);
        parameters.put("vcn", vcn);
        parameters.put("speed", speed != null ? speed : 50);
        requestBody.put("parameters", parameters);
        
        String jsonBody = JSON.toJSONString(requestBody);
        
        log.info("Calling aitools service: toolId={}, vcn={}, textLength={}", toolId, vcn, text.length());
        log.debug("Voice synthesis request: {}", jsonBody);
        
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
                throw new IOException("AITools service call failed: " + response.code() + ", " + errorBody);
            }
            
            String responseBody = response.body().string();
            log.debug("AITools response: {}", responseBody);
            
            JSONObject jsonResponse = JSON.parseObject(responseBody);
            
            if (jsonResponse.containsKey("data")) {
                JSONObject data = jsonResponse.getJSONObject("data");
                if (data.containsKey("voice_url")) {
                    String voiceUrl = data.getString("voice_url");
                    log.info("Voice synthesis completed: {}", voiceUrl);
                    return voiceUrl;
                }
            }
            
            if (jsonResponse.containsKey("voice_url")) {
                String voiceUrl = jsonResponse.getString("voice_url");
                log.info("Voice synthesis completed: {}", voiceUrl);
                return voiceUrl;
            }
            
            throw new IOException("Unexpected response format, missing 'voice_url': " + responseBody);
        }
    }
}
