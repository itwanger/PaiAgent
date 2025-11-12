package com.iflytek.astron.console.hub.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * 本地调试配置 - 禁用不需要的组件
 */
@Configuration
@Profile("minimal")
@EnableAutoConfiguration(exclude = {
        org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration.class,
        org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
        org.redisson.spring.starter.RedissonAutoConfiguration.class,
        org.redisson.spring.starter.RedissonAutoConfigurationV2.class
})
public class MinimalDebugConfig {
    // 这个配置类用于禁用不需要的自动配置
}
