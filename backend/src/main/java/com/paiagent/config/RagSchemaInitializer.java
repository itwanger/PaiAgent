package com.paiagent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Slf4j
@Component
@Order(1)
public class RagSchemaInitializer implements ApplicationRunner {
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final RagProperties properties;

    public RagSchemaInitializer(@Qualifier("ragDataSource") DataSource dataSource,
                                @Qualifier("ragJdbcTemplate") JdbcTemplate jdbcTemplate,
                                RagProperties properties) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-pgvector.sql"));
        }
        Integer dimension = jdbcTemplate.queryForObject("""
                SELECT atttypmod FROM pg_attribute
                WHERE attrelid = 'vector_store'::regclass AND attname = 'embedding'
                """, Integer.class);
        if (dimension != null && dimension != properties.getEmbedding().getDimension()) {
            throw new IllegalStateException("RAG embedding dimension mismatch: database=" + dimension
                    + ", config=" + properties.getEmbedding().getDimension());
        }
        log.info("PostgreSQL RAG schema initialized, embedding dimension={}", dimension);
    }
}
