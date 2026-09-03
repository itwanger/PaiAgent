package com.paiagent.service;

import com.paiagent.config.RagProperties;
import com.paiagent.entity.KnowledgeBase;
import com.paiagent.service.rag.DocumentParseService;
import com.paiagent.service.rag.KnowledgeBaseVectorService;
import com.paiagent.service.rag.PgVectorStore;
import com.paiagent.service.rag.RagRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KnowledgeBaseServiceTest {
    @Test
    void retrieveUsesPostgresVectorStoreAndKeepsCompatibleResult() {
        RagRepository repository=mock(RagRepository.class); PgVectorStore store=mock(PgVectorStore.class);
        KnowledgeBase base=new KnowledgeBase();base.setId(1L);when(repository.findBase(1L)).thenReturn(base);
        Document hit=Document.builder().id("12").text("RAG 使用 pgvector 检索")
                .metadata(Map.of("knowledgeBaseId","1","documentId","8","title","RAG")).score(0.91).build();
        when(store.similaritySearch(eq(1L),any(SearchRequest.class))).thenReturn(List.of(hit));

        Map<String,Object> result=create(repository,store).retrieve("1","RAG 怎么检索",List.of(),5,0.2);

        @SuppressWarnings("unchecked") List<Map<String,Object>> chunks=(List<Map<String,Object>>)result.get("chunks");
        assertEquals(12L,chunks.getFirst().get("chunkId"));
        assertTrue(String.valueOf(result.get("context")).contains("不可信参考资料 #12"));
    }

    @Test
    void retrieveReturnsEmptyContextWhenNoVectorMatches() {
        RagRepository repository=mock(RagRepository.class);PgVectorStore store=mock(PgVectorStore.class);
        KnowledgeBase base=new KnowledgeBase();base.setId(1L);when(repository.findBase(1L)).thenReturn(base);
        when(store.similaritySearch(eq(1L),any(SearchRequest.class))).thenReturn(List.of());
        assertEquals("",create(repository,store).retrieve("1","无结果",List.of(),5,0.2).get("context"));
    }

    private KnowledgeBaseService create(RagRepository repository,PgVectorStore store){
        return new KnowledgeBaseService(repository,mock(DocumentParseService.class),mock(KnowledgeBaseVectorService.class),
                store,new RagProperties(),mock(MinioService.class),mock(PlatformTransactionManager.class));
    }
}
