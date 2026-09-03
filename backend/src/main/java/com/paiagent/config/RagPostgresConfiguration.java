package com.paiagent.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class RagPostgresConfiguration {

    @Bean(name = "dataSource")
    @Primary
    public DataSource appDataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean(name = "jdbcTemplate")
    @Primary
    public JdbcTemplate appJdbcTemplate(@Qualifier("dataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "ragDataSource")
    public DataSource ragDataSource(RagProperties properties) {
        String password = properties.getDatasource().getPassword();
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("未配置 RAG PostgreSQL 密码，请设置 RAG_POSTGRES_PASSWORD");
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.getDatasource().getUrl());
        config.setUsername(properties.getDatasource().getUsername());
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        config.setPoolName("paiagent-rag-postgres");
        config.setMaximumPoolSize(8);
        config.setMinimumIdle(1);
        return new HikariDataSource(config);
    }

    @Bean(name = "ragJdbcTemplate")
    public JdbcTemplate ragJdbcTemplate(@Qualifier("ragDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "ragTransactionManager")
    public PlatformTransactionManager ragTransactionManager(@Qualifier("ragDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
