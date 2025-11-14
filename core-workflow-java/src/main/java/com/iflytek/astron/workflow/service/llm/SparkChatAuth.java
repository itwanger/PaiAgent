package com.iflytek.astron.workflow.service.llm;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * HMAC authentication handler for iFlytek Spark Chat API
 * Implements HMAC-SHA256 authentication mechanism
 */
@Slf4j
public class SparkChatAuth {
    
    private final String url;
    private final String apiKey;
    private final String apiSecret;
    
    public SparkChatAuth(String url, String apiKey, String apiSecret) {
        this.url = url;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
    }
    
    /**
     * Create authenticated WebSocket URL with HMAC signature
     * 
     * @return Authenticated URL with signature parameters
     * @throws Exception if URL generation fails
     */
    public String createUrl() throws Exception {
        // Parse URL
        URL parsedUrl = new URL(url);
        String host = parsedUrl.getHost();
        String path = parsedUrl.getPath();
        
        // Generate RFC1123 formatted timestamp
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = sdf.format(new Date());
        
        // Construct signature string
        String signatureOrigin = "host: " + host + "\n" +
                                "date: " + date + "\n" +
                                "GET " + path + " HTTP/1.1";
        
        // Generate HMAC-SHA256 signature
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(
            apiSecret.getBytes(StandardCharsets.UTF_8), 
            "HmacSHA256"
        );
        mac.init(secretKeySpec);
        byte[] signatureSha = mac.doFinal(signatureOrigin.getBytes(StandardCharsets.UTF_8));
        
        // Base64 encode signature
        String signatureShaBase64 = Base64.getEncoder().encodeToString(signatureSha);
        
        // Generate authorization header
        String authorizationOrigin = String.format(
            "api_key=\"%s\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"%s\"",
            apiKey,
            signatureShaBase64
        );
        
        String authorization = Base64.getEncoder().encodeToString(
            authorizationOrigin.getBytes(StandardCharsets.UTF_8)
        );
        
        // Build final authenticated URL
        String authenticatedUrl = String.format(
            "%s?authorization=%s&date=%s&host=%s",
            url,
            java.net.URLEncoder.encode(authorization, StandardCharsets.UTF_8),
            java.net.URLEncoder.encode(date, StandardCharsets.UTF_8),
            host
        );
        
        log.debug("Generated authenticated Spark URL: {}", authenticatedUrl);
        return authenticatedUrl;
    }
}
