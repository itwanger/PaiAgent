package com.paiagent.service.rag;

import com.paiagent.config.RagProperties;
import com.paiagent.entity.KnowledgeBase;
import com.paiagent.entity.KnowledgeDocument;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class KnowledgeBaseVectorService {
    private final RagRepository repository;
    private final PgVectorStore vectorStore;
    private final RagProperties properties;
    private final TransactionTemplate transaction;

    public KnowledgeBaseVectorService(RagRepository repository, PgVectorStore vectorStore,
                                      RagProperties properties,
                                      @org.springframework.beans.factory.annotation.Qualifier("ragTransactionManager")
                                      PlatformTransactionManager transactionManager) {
        this.repository = repository; this.vectorStore = vectorStore; this.properties = properties;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public List<Document> split(KnowledgeBase base, KnowledgeDocument source) {
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(base.getChunkSize() == null ? 800 : base.getChunkSize())
                .withMaxNumChunks(properties.getLimits().getMaxChunks() + 1)
                .withKeepSeparator(true).build();
        List<Document> chunks = splitter.apply(List.of(new Document(source.getRawText())));
        if (chunks.size() > properties.getLimits().getMaxChunks()) throw new IllegalArgumentException("文档分片数量超过上限");
        return chunks;
    }

    public void processTask(long taskId) {
        if (!repository.claimTask(taskId)) return;
        var task = repository.findTask(taskId);
        String version = repository.taskVersion(taskId);
        KnowledgeBase base = repository.findBase(task.getKnowledgeBaseId());
        KnowledgeDocument source = repository.findDocument(task.getDocumentId());
        try {
            List<Document> split = split(base, source);
            repository.updateTaskProgress(taskId, split.size(), 0);
            int batchSize = Math.max(1, Math.min(10, properties.getEmbedding().getBatchSize()));
            for (int start = 0; start < split.size(); start += batchSize) {
                int end = Math.min(start + batchSize, split.size());
                List<Document> batch = new ArrayList<>(end - start);
                for (int i = start; i < end; i++) {
                    Map<String,Object> metadata = new java.util.LinkedHashMap<>();
                    metadata.put("knowledgeBaseId", String.valueOf(base.getId()));
                    metadata.put("documentId", String.valueOf(source.getId()));
                    metadata.put("chunkIndex", i); metadata.put("indexVersion", version);
                    metadata.put("active", false); metadata.put("title", source.getTitle());
                    if (source.getSourceUrl() != null) metadata.put("sourceUrl", source.getSourceUrl());
                    if (source.getTags() != null) metadata.put("tags", source.getTags());
                    batch.add(Document.builder().id(String.valueOf(vectorStore.nextChunkId()))
                            .text(split.get(i).getText()).metadata(metadata).build());
                }
                vectorStore.add(batch);
                repository.updateTaskProgress(taskId, split.size(), end);
            }
            transaction.executeWithoutResult(s -> {
                vectorStore.activateVersion(source.getId(), version);
                repository.completeTask(taskId, source.getId(), version);
                repository.refreshBaseStats(base.getId());
            });
        } catch (Exception error) {
            vectorStore.deleteVersion(source.getId(), version);
            String message=rootMessage(error);
            if(isTransient(error)&&repository.taskRetryCount(taskId)<3) repository.retryTask(taskId,source.getId(),message);
            else repository.failTask(taskId, source.getId(), message);
            repository.refreshBaseStats(base.getId());
        }
    }

    private String rootMessage(Throwable error) {
        Throwable value=error; while(value.getCause()!=null) value=value.getCause();
        return value.getMessage()==null?value.getClass().getSimpleName():value.getMessage();
    }
    private boolean isTransient(Throwable error) {
        String text=rootMessage(error).toLowerCase();
        return text.contains("timeout")||text.contains("timed out")||text.contains("429")||text.contains("connection reset")
                ||text.contains("http 500")||text.contains("http 502")||text.contains("http 503")||text.contains("http 504");
    }
}
