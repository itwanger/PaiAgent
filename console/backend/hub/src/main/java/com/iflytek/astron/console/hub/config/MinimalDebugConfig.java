package com.iflytek.astron.console.hub.config;

import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.lang.reflect.Proxy;

/**
 * 本地调试配置 - 只禁用 OAuth2 和 Redisson,保留 Redis
 */
@Configuration
@Profile("minimal")
@EnableAutoConfiguration(exclude = {
        org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration.class,
        org.redisson.spring.starter.RedissonAutoConfigurationV2.class
})
public class MinimalDebugConfig {
    
    /**
     * 提供一个 no-op 的 RedissonClient bean 用于本地调试
     */
    @Bean
    public RedissonClient redissonClient() {
        return (RedissonClient) Proxy.newProxyInstance(
            RedissonClient.class.getClassLoader(),
            new Class[]{RedissonClient.class},
            (proxy, method, args) -> {
                // Handle getTopic to return a non-null proxy
                if ("getTopic".equals(method.getName())) {
                    return Proxy.newProxyInstance(
                        org.redisson.api.RTopic.class.getClassLoader(),
                        new Class[]{org.redisson.api.RTopic.class},
                        (topicProxy, topicMethod, topicArgs) -> {
                            if (topicMethod.getReturnType().equals(Void.TYPE)) {
                                return null;
                            }
                            if (topicMethod.getName().equals("addListener")) {
                                return 0; // Return dummy listener ID
                            }
                            return null;
                        }
                    );
                }
                // Handle getLock to return a non-null RLock proxy
                if ("getLock".equals(method.getName())) {
                    return Proxy.newProxyInstance(
                        org.redisson.api.RLock.class.getClassLoader(),
                        new Class[]{org.redisson.api.RLock.class},
                        (lockProxy, lockMethod, lockArgs) -> {
                            String methodName = lockMethod.getName();
                            // tryLock methods
                            if (methodName.equals("tryLock")) {
                                return true; // Always succeed acquiring lock
                            }
                            // isHeldByCurrentThread
                            if (methodName.equals("isHeldByCurrentThread")) {
                                return true; // Pretend we hold the lock
                            }
                            // unlock - no-op
                            if (methodName.equals("unlock")) {
                                return null;
                            }
                            // Default return for other methods
                            if (lockMethod.getReturnType().equals(Void.TYPE)) {
                                return null;
                            }
                            if (lockMethod.getReturnType().equals(Boolean.TYPE)) {
                                return false;
                            }
                            return null;
                        }
                    );
                }
                // No-op implementation for other methods
                if (method.getReturnType().equals(Void.TYPE)) {
                    return null;
                }
                return null;
            }
        );
    }
}




