package com.iflytek.astron.console.hub.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Redisson configuration for local development
 * Disables password authentication for local Redis
 */
@Configuration
@Profile("local")
public class LocalRedissonConfig {

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://localhost:6379")
                .setDatabase(1)
                .setTimeout(3000)
                .setConnectionPoolSize(8)
                .setConnectionMinimumIdleSize(2)
                // 关键：不设置密码
                .setPassword(null);
        
        return Redisson.create(config);
    }
}
