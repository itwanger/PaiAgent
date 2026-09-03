package com.paiagent.service.rag;

import com.paiagent.config.RagProperties;
import com.paiagent.service.AgentPlanConfigResolver;
import com.paiagent.service.ResolvedAgentPlanConfig;
import com.paiagent.service.VolcengineAgentPlanClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RagEmbeddingModelTest {
    @Test
    void sendsMultipleChunksInOneEmbeddingRequestAndPreservesOrder() throws Exception {
        RagProperties properties=new RagProperties();properties.getEmbedding().setDimension(3);
        AgentPlanConfigResolver resolver=mock(AgentPlanConfigResolver.class);
        VolcengineAgentPlanClient client=mock(VolcengineAgentPlanClient.class);
        ResolvedAgentPlanConfig config=new ResolvedAgentPlanConfig(1L,"volcengine_agent_plan","http://embedding","key","model","model",null,null,false);
        when(resolver.resolveKnowledgeConfig(null,properties.getEmbedding().getModel())).thenReturn(config);
        when(client.createEmbeddings(config,List.of("a","b"))).thenReturn(List.of(List.of(1d,2d,3d),List.of(4d,5d,6d)));

        var response=new RagEmbeddingModel(properties,resolver,client).call(new EmbeddingRequest(List.of("a","b"),null));

        assertEquals(2,response.getResults().size());
        assertArrayEquals(new float[]{4,5,6},response.getResults().get(1).getOutput());
        verify(client).createEmbeddings(config,List.of("a","b"));
    }

    @Test
    void rejectsEmbeddingWithUnexpectedDimension() throws Exception {
        RagProperties properties=new RagProperties();properties.getEmbedding().setDimension(3);
        AgentPlanConfigResolver resolver=mock(AgentPlanConfigResolver.class);
        VolcengineAgentPlanClient client=mock(VolcengineAgentPlanClient.class);
        ResolvedAgentPlanConfig config=new ResolvedAgentPlanConfig(1L,"volcengine_agent_plan","http://embedding","key","model","model",null,null,false);
        when(resolver.resolveKnowledgeConfig(null,properties.getEmbedding().getModel())).thenReturn(config);
        when(client.createEmbeddings(config,List.of("a"))).thenReturn(List.of(List.of(1d,2d)));
        assertThrows(IllegalStateException.class,()->new RagEmbeddingModel(properties,resolver,client).call(new EmbeddingRequest(List.of("a"),null)));
    }
}
