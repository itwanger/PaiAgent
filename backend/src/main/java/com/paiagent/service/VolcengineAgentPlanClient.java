package com.paiagent.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin HTTP client for Agent Plan capabilities. Endpoint details stay here instead of node executors.
 */
@Slf4j
@Service
public class VolcengineAgentPlanClient {

    public List<Double> createEmbedding(ResolvedAgentPlanConfig config, String input) throws IOException {
        List<List<Double>> embeddings = createEmbeddings(config, List.of(input));
        return embeddings.isEmpty() ? List.of() : embeddings.getFirst();
    }

    /** OpenAI-compatible batch Embedding call; output is restored to request order by response index. */
    public List<List<Double>> createEmbeddings(ResolvedAgentPlanConfig config, List<String> inputs) throws IOException {
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }
        JSONObject request = new JSONObject();
        request.put("model", config.model());
        request.put("input", inputs);

        JSONObject response = postJson(config, "/embeddings", request);
        JSONArray data = response.getJSONArray("data");
        if (data == null || data.size() != inputs.size()) {
            throw new IOException("Embedding 返回数量与输入数量不一致");
        }
        List<List<Double>> result = new ArrayList<>(java.util.Collections.nCopies(inputs.size(), null));
        for (int position = 0; position < data.size(); position++) {
            JSONObject item = data.getJSONObject(position);
            Integer index = item.getInteger("index");
            int target = index == null ? position : index;
            if (target < 0 || target >= inputs.size()) throw new IOException("Embedding 返回非法 index: " + target);
            JSONArray embedding = item.getJSONArray("embedding");
            if (embedding == null || embedding.isEmpty()) throw new IOException("Embedding 返回空向量");
            List<Double> vector = new ArrayList<>(embedding.size());
            for (Object value : embedding) {
                if (value instanceof Number number) vector.add(number.doubleValue());
            }
            result.set(target, vector);
        }
        if (result.stream().anyMatch(java.util.Objects::isNull)) throw new IOException("Embedding 返回缺少部分结果");
        return result;
    }

    public Map<String, Object> generateImage(ResolvedAgentPlanConfig config, String prompt,
                                             String referenceImageUrl, String size, int count,
                                             String negativePrompt, String style) throws IOException {
        JSONObject request = new JSONObject();
        request.put("model", config.model());
        request.put("prompt", prompt);
        request.put("response_format", "url");
        request.put("output_format", "png");
        request.put("watermark", false);
        request.put("stream", false);
        if (StringUtils.hasText(size)) {
            request.put("size", size);
        }
        if (StringUtils.hasText(referenceImageUrl)) {
            request.put("image", referenceImageUrl);
        }

        JSONObject response = postJson(config, "/images/generations", request);
        List<String> urls = new ArrayList<>();
        JSONArray data = response.getJSONArray("data");
        if (data != null) {
            for (Object item : data) {
                if (item instanceof JSONObject object) {
                    String url = object.getString("url");
                    if (StringUtils.hasText(url)) {
                        urls.add(url);
                    }
                }
            }
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("imageUrls", urls);
        output.put("metadata", response);
        return output;
    }

    public String createVideoTask(ResolvedAgentPlanConfig config, String prompt, String referenceImageUrl,
                                  int duration, String resolution, String ratio, String cameraMotion) throws IOException {
        JSONObject request = new JSONObject();
        request.put("model", config.model());

        JSONArray content = new JSONArray();
        JSONObject textContent = new JSONObject();
        textContent.put("type", "text");
        textContent.put("text", prompt);
        content.add(textContent);
        if (StringUtils.hasText(referenceImageUrl)) {
            JSONObject imageUrl = new JSONObject();
            imageUrl.put("url", referenceImageUrl);

            JSONObject imageContent = new JSONObject();
            imageContent.put("type", "image_url");
            imageContent.put("image_url", imageUrl);
            content.add(imageContent);
        }
        request.put("content", content);

        request.put("generate_audio", true);
        request.put("duration", duration);
        if (StringUtils.hasText(resolution)) {
            request.put("resolution", resolution);
        }
        if (StringUtils.hasText(ratio)) {
            request.put("ratio", ratio);
        }
        request.put("watermark", false);

        JSONObject response = postJson(config, "/contents/generations/tasks", request);
        return firstText(response.getString("id"), response.getString("task_id"), response.getString("taskId"));
    }

    public Map<String, Object> getVideoTask(ResolvedAgentPlanConfig config, String taskId) throws IOException {
        JSONObject response = getJson(config, "/contents/generations/tasks/" + taskId);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("status", firstText(response.getString("status"), response.getString("task_status"), "running"));
        output.put("videoUrl", findString(response, "video_url", "videoUrl", "url"));
        output.put("coverUrl", findString(response, "last_frame_url", "cover_url", "coverUrl", "poster_url"));
        output.put("raw", response);
        return output;
    }

    private JSONObject postJson(ResolvedAgentPlanConfig config, String path, JSONObject body) throws IOException {
        return postJson(config, path, body, config.apiKey());
    }

    private JSONObject postJson(ResolvedAgentPlanConfig config, String path, JSONObject body, String apiKey) throws IOException {
        HttpURLConnection conn = openConnection(config, path, "POST", apiKey);
        byte[] bytes = body.toJSONString().getBytes(StandardCharsets.UTF_8);
        conn.setDoOutput(true);
        try (var output = conn.getOutputStream()) {
            output.write(bytes);
        }
        return readResponse(conn);
    }

    private JSONObject getJson(ResolvedAgentPlanConfig config, String path) throws IOException {
        HttpURLConnection conn = openConnection(config, path, "GET", config.apiKey());
        return readResponse(conn);
    }

    private HttpURLConnection openConnection(ResolvedAgentPlanConfig config, String path, String method, String apiKey) throws IOException {
        URL url = URI.create(normalizeBaseUrl(config.apiUrl()) + normalizePath(path)).toURL();
        return openConnection(url, method, apiKey);
    }

    private HttpURLConnection openConnection(URL url, String method, String apiKey) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(120_000);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        return conn;
    }

    private JSONObject readResponse(HttpURLConnection conn) throws IOException {
        int status = conn.getResponseCode();
        var stream = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
        byte[] bytes = stream == null ? new byte[0] : stream.readAllBytes();
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (status < 200 || status >= 300) {
            String safe = text.length() > 1000 ? text.substring(0, 1000) : text;
            throw new IOException("Agent Plan 调用失败, HTTP " + status + ": " + safe);
        }
        return JSON.parseObject(text);
    }

    private String normalizeBaseUrl(String apiUrl) {
        String base = apiUrl == null ? "" : apiUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private String normalizePath(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }

    private String findString(JSONObject object, String... keys) {
        for (String key : keys) {
            String value = object.getString(key);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        for (Object value : object.values()) {
            if (value instanceof JSONObject child) {
                String found = findString(child, keys);
                if (StringUtils.hasText(found)) {
                    return found;
                }
            }
        }
        return null;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

}
