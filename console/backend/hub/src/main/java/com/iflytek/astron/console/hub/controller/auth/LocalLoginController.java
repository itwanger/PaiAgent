package com.iflytek.astron.console.hub.controller.auth;

import com.iflytek.astron.console.toolkit.common.Result;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LocalLoginController {

    @PostMapping("/login")
    public Result<Map<String, String>> login(@RequestBody LoginRequest request) {
        log.info("Local login attempt: username={}", request.getUsername());

        if ("admin".equals(request.getUsername()) && "123".equals(request.getPassword())) {
            Map<String, String> result = new HashMap<>();
            result.put("accessToken", "mock-local-dev-token");
            result.put("refreshToken", "mock-local-dev-refresh-token");
            log.info("Local login successful for user: {}", request.getUsername());
            return Result.success(result);
        }

        log.warn("Local login failed for user: {}", request.getUsername());
        return Result.failure("用户名或密码错误");
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }
}
