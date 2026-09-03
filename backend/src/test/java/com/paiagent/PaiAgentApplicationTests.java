package com.paiagent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "paiagent.auth.jwt-secret=test-jwt-secret-key-for-spring-context-123456",
        "minio.endpoint=http://localhost:9000",
        "minio.accessKey=minioadmin",
        "minio.secretKey=minioadmin",
        "minio.bucketName=paiagent",
        "minio.publicUrl=http://localhost:9000",
        "spring.ai.openai.api-key=sk-test-placeholder",
        "spring.datasource.password=${MYSQL_PASSWORD:}",
        "paiagent.rag.migration-enabled=false",
        "paiagent.rag.stream.enabled=false"
})
class PaiAgentApplicationTests {

	@Test
	void contextLoads() {
	}

}
