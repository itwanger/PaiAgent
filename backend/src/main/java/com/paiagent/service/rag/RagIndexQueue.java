package com.paiagent.service.rag;

import com.paiagent.config.RagProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
public class RagIndexQueue {
    private final RagProperties properties;
    private final RagRepository repository;
    private final KnowledgeBaseVectorService vectorService;
    private final StringRedisTemplate redis;
    private volatile boolean applicationReady;

    public RagIndexQueue(RagProperties properties, RagRepository repository,
                         KnowledgeBaseVectorService vectorService, StringRedisTemplate redis) {
        this.properties=properties; this.repository=repository; this.vectorService=vectorService; this.redis=redis;
    }

    @PostConstruct
    void createGroup() {
        if (!properties.getStream().isEnabled()) return;
        try { redis.opsForStream().createGroup(properties.getStream().getKey(), ReadOffset.from("0-0"), properties.getStream().getGroup()); }
        catch (Exception ignored) { log.debug("RAG stream consumer group already exists or Redis is not ready"); }
    }

    @EventListener(ApplicationReadyEvent.class)
    void markApplicationReady() { applicationReady = true; }

    @Scheduled(fixedDelayString = "${paiagent.rag.stream.poll-delay-ms:1000}")
    public void publishOutbox() {
        if (!applicationReady || !properties.getStream().isEnabled()) return;
        try {
            repository.recoverStalledTasks();
            for (Long outboxId : repository.pendingOutboxIds()) {
                try {
                    long taskId=repository.outboxTaskId(outboxId);
                    redis.opsForStream().add(properties.getStream().getKey(), Map.of("taskId",String.valueOf(taskId)));
                    createGroup();
                    repository.markOutboxPublished(outboxId);
                } catch(Exception e) { repository.markOutboxFailed(outboxId,e.getMessage()); }
            }
        } catch (Exception e) {
            log.debug("RAG outbox publish skipped before schema is ready: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${paiagent.rag.stream.poll-delay-ms:1000}")
    public void consume() {
        if (!applicationReady || !properties.getStream().isEnabled()) return;
        try {
            var records=redis.opsForStream().read(Consumer.from(properties.getStream().getGroup(),properties.getStream().getConsumer()),
                    StreamReadOptions.empty().count(1).block(Duration.ofMillis(100)),
                    StreamOffset.create(properties.getStream().getKey(),ReadOffset.lastConsumed()));
            if(records==null) return;
            for(MapRecord<String,Object,Object> record:records) {
                Object raw=record.getValue().get("taskId");
                if(raw!=null) vectorService.processTask(Long.parseLong(String.valueOf(raw)));
                redis.opsForStream().acknowledge(properties.getStream().getKey(),properties.getStream().getGroup(),record.getId());
            }
        } catch(Exception e) { log.debug("RAG stream poll skipped: {}",e.getMessage()); }
    }
}
