package com.iflytek.astron.console.hub.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson configuration Provides RedissonClient bean for distributed lock and cache
 */
@Slf4j
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:127.0.0.1}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    @Value("${spring.data.redis.password:#{null}}")
    private String redisPassword;

    @Bean
    public RedissonClient redissonClient() {
        log.info("初始化 RedissonClient - host: {}, port: {}, database: {}, hasPassword: {}",
                redisHost, redisPort, redisDatabase, redisPassword != null && !redisPassword.isEmpty());

        Config config = new Config();
        String address = "redis://" + redisHost + ":" + redisPort;

        config.useSingleServer()
                .setAddress(address)
                .setDatabase(redisDatabase)
                .setTimeout(3000)
                .setConnectionPoolSize(8)
                .setConnectionMinimumIdleSize(2);

        // Only set password if it's not empty
        if (redisPassword != null && !redisPassword.trim().isEmpty()) {
            config.useSingleServer().setPassword(redisPassword);
            log.info("Redis 密码已设置");
        } else {
            log.info("Redis 无密码模式");
        }

        log.info("Redisson 连接地址: {}", address);

        return Redisson.create(config);
    }
}
