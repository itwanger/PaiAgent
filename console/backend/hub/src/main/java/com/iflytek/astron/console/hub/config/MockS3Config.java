package com.iflytek.astron.console.hub.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Mock S3 configuration for minimal debug profile Prevents S3ClientUtil from trying to connect to
 * MinIO
 */
@Configuration
@Profile("minimal")
public class MockS3Config {

    /**
     * 不创建真实的 S3ClientUtil bean 让 @ConditionalOnMissingBean 生效
     */
}
