package com.paiagent.service.rag.benchmark;

/**
 * 基准测试凭据读取器，只接受 JVM 参数或环境变量，避免在测试代码中保存可用密码。
 */
final class BenchmarkCredentials {
    private BenchmarkCredentials() {
    }

    static String require(String propertyName, String environmentName) {
        String value = System.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            value = System.getenv(environmentName);
        }
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "缺少基准测试凭据，请设置 JVM 参数 " + propertyName + " 或环境变量 " + environmentName);
        }
        return value;
    }
}
