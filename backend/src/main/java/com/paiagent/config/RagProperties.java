package com.paiagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "paiagent.rag")
public class RagProperties {
    private boolean migrationEnabled = true;
    private final Datasource datasource = new Datasource();
    private final Embedding embedding = new Embedding();
    private final Limits limits = new Limits();
    private final Stream stream = new Stream();

    @Data public static class Datasource {
        private String url = "jdbc:postgresql://localhost:5432/paiagent_vector";
        private String username = "paiagent";
        // 密码必须通过配置注入，代码中不提供可用默认值。
        private String password = "";
    }
    @Data public static class Embedding {
        private Long configId;
        private String model = "doubao-embedding-vision";
        private int dimension = 1024;
        private int batchSize = 10;
    }
    @Data public static class Limits {
        private long maxFileBytes = 10 * 1024 * 1024L;
        private int maxTextChars = 2_000_000;
        private int maxChunks = 5_000;
        private int maxContextChars = 32_000;
    }
    @Data public static class Stream {
        private boolean enabled = true;
        private String key = "paiagent:rag:index";
        private String group = "paiagent-rag-indexers";
        private String consumer = "paiagent-local";
        private long pollDelayMs = 1_000;
    }
}
