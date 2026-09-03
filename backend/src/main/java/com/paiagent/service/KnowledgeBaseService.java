package com.paiagent.service;

import com.paiagent.config.RagProperties;
import com.paiagent.dto.*;
import com.paiagent.entity.KnowledgeBase;
import com.paiagent.entity.KnowledgeDocument;
import com.paiagent.entity.KnowledgeIndexTask;
import com.paiagent.service.rag.*;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Compatibility facade: public PaiAgent APIs backed exclusively by the PostgreSQL RAG module. */
@Service
public class KnowledgeBaseService {
    private static final int DEFAULT_CHUNK_SIZE = 800;
    private static final int DEFAULT_CHUNK_OVERLAP = 100;
    private final RagRepository repository;
    private final DocumentParseService parser;
    private final KnowledgeBaseVectorService vectorService;
    private final PgVectorStore vectorStore;
    private final RagProperties properties;
    private final MinioService minioService;
    private final TransactionTemplate transaction;

    public KnowledgeBaseService(RagRepository repository, DocumentParseService parser,
                                KnowledgeBaseVectorService vectorService, PgVectorStore vectorStore,
                                RagProperties properties, MinioService minioService,
                                @org.springframework.beans.factory.annotation.Qualifier("ragTransactionManager")
                                PlatformTransactionManager transactionManager) {
        this.repository=repository; this.parser=parser; this.vectorService=vectorService;
        this.vectorStore=vectorStore; this.properties=properties; this.minioService=minioService;
        this.transaction=new TransactionTemplate(transactionManager);
    }

    public List<Map<String,Object>> listKnowledgeBases() { return repository.listBases().stream().map(this::toBaseMap).toList(); }
    public Map<String,Object> getKnowledgeBase(Long id) {
        KnowledgeBase base=requireBase(id); Map<String,Object> out=toBaseMap(base);
        out.put("documents",listDocuments(id)); out.put("recentTasks",repository.recentTasks(id).stream().map(this::toTaskMap).toList());
        return out;
    }
    public void deleteKnowledgeBase(Long id) {
        requireBase(id); transaction.executeWithoutResult(s->{vectorStore.deleteKnowledgeBase(id);repository.deleteBase(id);});
    }
    public Map<String,Object> createKnowledgeBase(KnowledgeBaseRequest request) {
        KnowledgeBase base=new KnowledgeBase(); base.setName(request.getName().trim());
        base.setDescription(trim(request.getDescription())); base.setConfigId(properties.getEmbedding().getConfigId());
        base.setEmbeddingModel(properties.getEmbedding().getModel()); base.setChunkSize(normalizeSize(request.getChunkSize()));
        base.setChunkOverlap(normalizeOverlap(request.getChunkOverlap(),base.getChunkSize())); base.setStatus("DRAFT");
        long id=repository.insertBase(base); return toBaseMap(requireBase(id));
    }
    public Map<String,Object> importText(Long baseId, KnowledgeTextImportRequest request) {
        requireBase(baseId); String text=parser.cleanText(request.getContent());
        long documentId=transaction.execute(s->{long id=repository.insertDocument(baseId,first(request.getTitle(),"未命名文本"),
                "TEXT",null,null,null,"text/plain",null,text,trim(request.getTags())); queueIndex(baseId,id);repository.refreshBaseStats(baseId);return id;});
        return toDocumentMap(repository.findDocument(documentId));
    }
    public Map<String,Object> uploadTextFile(Long baseId, MultipartFile file) throws Exception {
        requireBase(baseId); var parsed=parser.parse(file);
        KnowledgeDocument duplicate=repository.findDocumentByHash(baseId,parsed.sha256());
        if(duplicate!=null) return toDocumentMap(duplicate);
        String key="knowledge/"+baseId+"/"+UUID.randomUUID()+"/"+parsed.fileName();
        String url=minioService.uploadFromBytes(parsed.bytes(),key,parsed.mediaType());
        long documentId=transaction.execute(s->{long id=repository.insertDocument(baseId,parsed.fileName(),"FILE",url,
                parsed.fileName(),key,parsed.mediaType(),parsed.sha256(),parsed.text(),null);queueIndex(baseId,id);repository.refreshBaseStats(baseId);return id;});
        return toDocumentMap(repository.findDocument(documentId));
    }
    public List<Map<String,Object>> listDocuments(Long baseId) { requireBase(baseId); return repository.listDocuments(baseId).stream().map(this::toDocumentMap).toList(); }
    public List<Map<String,Object>> previewChunks(Long baseId,Long documentId,KnowledgePreviewRequest request) {
        KnowledgeBase base=requireBase(baseId); KnowledgeDocument source=requireDocument(baseId,documentId);
        KnowledgeBase preview=new KnowledgeBase(); preview.setChunkSize(normalizeSize(request==null?base.getChunkSize():request.getChunkSize()));
        List<Document> chunks=vectorService.split(preview,source); List<Map<String,Object>> out=new ArrayList<>();
        for(int i=0;i<chunks.size();i++) out.add(Map.of("chunkIndex",i,"content",chunks.get(i).getText(),"charCount",chunks.get(i).getText().length()));
        return out;
    }
    public Map<String,Object> indexDocument(Long baseId,Long documentId) {
        requireDocument(baseId,documentId); long taskId=transaction.execute(s->queueIndex(baseId,documentId));
        return toTaskMap(repository.findTask(taskId));
    }
    public Map<String,Object> search(Long baseId,KnowledgeSearchRequest request) { requireBase(baseId); return retrieve(String.valueOf(baseId),request.getQuery(),List.of(),request.getTopK()==null?5:request.getTopK(),request.getScoreThreshold()==null?0.2:request.getScoreThreshold()); }
    public Map<String,Object> searchRuntime(String baseId,String query,int topK,double threshold) { return retrieve(baseId,query,List.of(),topK,threshold); }
    public Map<String,Object> retrieve(String baseId,String query,List<Double> ignored,int topK,double threshold) {
        long id=resolveBase(baseId).getId(); int safeTopK=Math.max(1,Math.min(20,topK)); double safeThreshold=threshold<=0?0.000001:Math.min(1,threshold);
        List<Document> docs=vectorStore.similaritySearch(id,SearchRequest.builder().query(query).topK(safeTopK).similarityThreshold(safeThreshold).build());
        List<Map<String,Object>> chunks=docs.stream().map(this::toMatch).toList(); StringBuilder context=new StringBuilder();
        for(Map<String,Object> chunk:chunks){String line="[不可信参考资料 #"+chunk.get("chunkId")+"] "+chunk.get("content")+"\n";if(context.length()+line.length()>properties.getLimits().getMaxContextChars())break;context.append(line);}
        Map<String,Object> out=new LinkedHashMap<>();out.put("chunks",chunks);out.put("citations",chunks.stream().map(v->v.get("chunkId")).toList());out.put("context",context.toString().trim());return out;
    }
    public Map<String,Object> upsert(String baseId,String title,String content,String sourceUrl,List<String> tags,List<Double> ignored,String ignoredModel) {
        KnowledgeBase base=resolveBaseForWrite(baseId);String text=parser.cleanText(content);
        long documentId=transaction.execute(s->{long id=repository.insertDocument(base.getId(),first(title,"工作流写入"),"WORKFLOW",sourceUrl,null,null,"text/plain",null,text,tags==null?null:String.join(",",tags));return id;});
        long taskId=transaction.execute(s->queueIndex(base.getId(),documentId));
        KnowledgeIndexTask task;
        do { vectorService.processTask(taskId); task=repository.findTask(taskId); }
        while("QUEUED".equals(task.getStatus()) && repository.taskRetryCount(taskId)<=3);
        Map<String,Object> out=new LinkedHashMap<>();out.put("knowledgeBaseId",base.getId());out.put("contentId",documentId);out.put("chunkCount",task.getTotalChunks());out.put("indexed","SUCCESS".equals(task.getStatus()));if(task.getErrorMessage()!=null)out.put("errorMessage",task.getErrorMessage());return out;
    }

    private long queueIndex(long baseId,long documentId){String version=UUID.randomUUID().toString();long taskId=repository.createTask(baseId,documentId,version);repository.updateDocumentStatus(documentId,"QUEUED",null);repository.addOutbox(taskId);return taskId;}
    private KnowledgeBase resolveBase(String value){if(StringUtils.hasText(value)&&!"default".equalsIgnoreCase(value)){try{return requireBase(Long.parseLong(value));}catch(NumberFormatException ignored){KnowledgeBase named=repository.findBaseByName(value);if(named!=null)return named;}}return resolveBaseForWrite("default");}
    private KnowledgeBase resolveBaseForWrite(String value){if(StringUtils.hasText(value)&&!"default".equalsIgnoreCase(value))return resolveBase(value);KnowledgeBase existing=repository.findBaseByName("默认知识库");if(existing!=null)return existing;KnowledgeBaseRequest request=new KnowledgeBaseRequest();request.setName("默认知识库");request.setDescription("工作流运行时自动写入的默认知识库");return requireBase(((Number)createKnowledgeBase(request).get("id")).longValue());}
    private KnowledgeBase requireBase(long id){KnowledgeBase v=repository.findBase(id);if(v==null)throw new IllegalArgumentException("知识库不存在");return v;}
    private KnowledgeDocument requireDocument(long baseId,long id){KnowledgeDocument v=repository.findDocument(id);if(v==null||!Long.valueOf(baseId).equals(v.getKnowledgeBaseId()))throw new IllegalArgumentException("知识文档不存在");return v;}
    private Map<String,Object> toMatch(Document d){Map<String,Object> out=new LinkedHashMap<>();out.put("chunkId",Long.parseLong(d.getId()));out.put("knowledgeBaseId",Long.parseLong(String.valueOf(d.getMetadata().get("knowledgeBaseId"))));out.put("documentId",Long.parseLong(String.valueOf(d.getMetadata().get("documentId"))));out.put("title",d.getMetadata().get("title"));out.put("content",d.getText());out.put("sourceUrl",d.getMetadata().get("sourceUrl"));out.put("tags",d.getMetadata().get("tags"));out.put("score",d.getScore());return out;}
    private Map<String,Object> toBaseMap(KnowledgeBase v){Map<String,Object> o=new LinkedHashMap<>();o.put("id",v.getId());o.put("name",v.getName());o.put("description",v.getDescription());o.put("configId",v.getConfigId());o.put("embeddingModel",v.getEmbeddingModel());o.put("chunkSize",v.getChunkSize());o.put("chunkOverlap",v.getChunkOverlap());o.put("status",v.getStatus());o.put("documentCount",v.getDocumentCount());o.put("chunkCount",v.getChunkCount());o.put("charCount",v.getCharCount());o.put("createdAt",v.getCreatedAt());o.put("updatedAt",v.getUpdatedAt());return o;}
    private Map<String,Object> toDocumentMap(KnowledgeDocument v){Map<String,Object> o=new LinkedHashMap<>();o.put("id",v.getId());o.put("knowledgeBaseId",v.getKnowledgeBaseId());o.put("title",v.getTitle());o.put("sourceType",v.getSourceType());o.put("sourceUrl",v.getSourceUrl());o.put("fileName",v.getFileName());o.put("tags",v.getTags());o.put("status",v.getStatus());o.put("charCount",v.getCharCount());o.put("errorMessage",v.getErrorMessage());o.put("createdAt",v.getCreatedAt());o.put("updatedAt",v.getUpdatedAt());return o;}
    private Map<String,Object> toTaskMap(KnowledgeIndexTask v){Map<String,Object> o=new LinkedHashMap<>();o.put("id",v.getId());o.put("knowledgeBaseId",v.getKnowledgeBaseId());o.put("documentId",v.getDocumentId());o.put("status",v.getStatus());o.put("progress",v.getProgress());o.put("totalChunks",v.getTotalChunks());o.put("finishedChunks",v.getFinishedChunks());o.put("errorMessage",v.getErrorMessage());o.put("createdAt",v.getCreatedAt());o.put("updatedAt",v.getUpdatedAt());return o;}
    private int normalizeSize(Integer v){return Math.max(100,Math.min(4000,v==null?DEFAULT_CHUNK_SIZE:v));}
    private int normalizeOverlap(Integer v,int size){return Math.max(0,Math.min(size-1,v==null?DEFAULT_CHUNK_OVERLAP:v));}
    private String first(String... values){for(String v:values)if(StringUtils.hasText(v))return v.trim();return null;}
    private String trim(String value){return StringUtils.hasText(value)?value.trim():null;}
}
