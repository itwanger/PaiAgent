package com.paiagent.service;

import com.paiagent.engine.model.WorkflowNode;
import com.paiagent.entity.LLMGlobalConfig;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;

/**
 * Resolves Agent Plan capable runtime configuration from global config or node-level fields.
 */
@Service
public class AgentPlanConfigResolver {

    public static final String DEFAULT_EMBEDDING_MODEL = "doubao-embedding-vision";

    private final LLMGlobalConfigService llmGlobalConfigService;

    public AgentPlanConfigResolver(LLMGlobalConfigService llmGlobalConfigService) {
        this.llmGlobalConfigService = llmGlobalConfigService;
    }

    public ResolvedAgentPlanConfig resolve(WorkflowNode node, String capability) {
        Map<String, Object> data = node.getData();
        Long nodeConfigId = parseLong(data.get("configId"));
        LLMGlobalConfig nodeGlobalConfig = nodeConfigId != null ? llmGlobalConfigService.getById(nodeConfigId) : null;
        Long explicitAgentPlanConfigId = parseLong(data.get("agentPlanConfigId"));
        LLMGlobalConfig globalConfig = resolveAgentPlanConfig(explicitAgentPlanConfigId, nodeGlobalConfig);
        Long configId = globalConfig != null ? globalConfig.getId() : null;

        String nodeModel = trim(data.get("model"));
        String apiUrl = firstText(globalConfig != null ? globalConfig.getApiUrl() : null, trim(data.get("apiUrl")));
        String apiKey = firstText(globalConfig != null ? globalConfig.getApiKey() : null, trim(data.get("apiKey")));
        String provider = canonicalizeProvider(firstText(
                globalConfig != null ? globalConfig.getProvider() : null,
                trim(data.get("provider")),
                "volcengine_agent_plan"
        ));

        String languageModel = firstText(nodeModel, globalConfig != null ? globalConfig.getModel() : null);
        String embeddingModel = firstText(
                "embedding".equals(capability) ? nodeModel : null,
                globalConfig != null ? globalConfig.getEmbeddingModel() : null,
                DEFAULT_EMBEDDING_MODEL
        );
        String imageModel = firstText(
                "image".equals(capability) ? nodeModel : null,
                globalConfig != null ? globalConfig.getImageModel() : null,
                languageModel
        );
        String videoModel = firstText(
                "video".equals(capability) ? nodeModel : null,
                globalConfig != null ? globalConfig.getVideoModel() : null,
                languageModel
        );

        String effectiveModel = switch (capability) {
            case "embedding", "memory", "knowledge" -> embeddingModel;
            case "image" -> imageModel;
            case "video" -> videoModel;
            default -> languageModel;
        };

        boolean memoryEnabled = globalConfig != null && globalConfig.getMemoryEnabled() != null
                && globalConfig.getMemoryEnabled() == 1;

        return new ResolvedAgentPlanConfig(
                configId,
                provider,
                apiUrl,
                apiKey,
                effectiveModel,
                embeddingModel,
                imageModel,
                videoModel,
                memoryEnabled
        );
    }

    public ResolvedAgentPlanConfig resolveImageConfig(WorkflowNode node) {
        Map<String, Object> data = node.getData();
        Long nodeConfigId = parseLong(data.get("configId"));
        LLMGlobalConfig globalConfig = nodeConfigId != null ? llmGlobalConfigService.getById(nodeConfigId) : null;

        String configuredProvider = canonicalizeProvider(trim(data.get("provider")));
        if (globalConfig == null) {
            globalConfig = resolveDefaultImageConfig(configuredProvider);
        }

        String nodeModel = trim(data.get("model"));
        String apiUrl = firstText(globalConfig != null ? globalConfig.getApiUrl() : null, trim(data.get("apiUrl")));
        String apiKey = firstText(globalConfig != null ? globalConfig.getApiKey() : null, trim(data.get("apiKey")));
        String provider = canonicalizeProvider(firstText(
                globalConfig != null ? globalConfig.getProvider() : null,
                configuredProvider,
                "volcengine_agent_plan"
        ));
        String languageModel = firstText(nodeModel, globalConfig != null ? globalConfig.getModel() : null);
        String imageModel = firstText(
                nodeModel,
                globalConfig != null ? globalConfig.getImageModel() : null,
                languageModel
        );

        return new ResolvedAgentPlanConfig(
                globalConfig != null ? globalConfig.getId() : null,
                provider,
                apiUrl,
                apiKey,
                imageModel,
                globalConfig != null ? globalConfig.getEmbeddingModel() : null,
                imageModel,
                globalConfig != null ? globalConfig.getVideoModel() : null,
                globalConfig != null && globalConfig.getMemoryEnabled() != null && globalConfig.getMemoryEnabled() == 1
        );
    }

    private LLMGlobalConfig resolveDefaultImageConfig(String configuredProvider) {
        if (StringUtils.hasText(configuredProvider)) {
            LLMGlobalConfig providerDefault = llmGlobalConfigService.getDefaultConfig(configuredProvider);
            if (providerDefault != null) {
                return providerDefault;
            }
        }

        LLMGlobalConfig defaultAgentPlan = llmGlobalConfigService.getDefaultConfig("volcengine_agent_plan");
        if (defaultAgentPlan != null) {
            return defaultAgentPlan;
        }
        return llmGlobalConfigService.getDefaultConfig("step");
    }

    private LLMGlobalConfig resolveAgentPlanConfig(Long explicitAgentPlanConfigId, LLMGlobalConfig nodeGlobalConfig) {
        if (explicitAgentPlanConfigId != null) {
            LLMGlobalConfig explicit = llmGlobalConfigService.getById(explicitAgentPlanConfigId);
            if (explicit != null) {
                return explicit;
            }
        }

        if (nodeGlobalConfig != null) {
            return nodeGlobalConfig;
        }

        return llmGlobalConfigService.getDefaultConfig("volcengine_agent_plan");
    }

    public ResolvedAgentPlanConfig resolveKnowledgeConfig(Long configId, String modelOverride) {
        LLMGlobalConfig globalConfig = configId != null
                ? llmGlobalConfigService.getById(configId)
                : llmGlobalConfigService.getDefaultConfig("volcengine_agent_plan");
        String embeddingModel = firstText(
                modelOverride,
                globalConfig != null ? globalConfig.getEmbeddingModel() : null,
                DEFAULT_EMBEDDING_MODEL
        );
        return new ResolvedAgentPlanConfig(
                globalConfig != null ? globalConfig.getId() : configId,
                canonicalizeProvider(firstText(globalConfig != null ? globalConfig.getProvider() : null, "volcengine_agent_plan")),
                globalConfig != null ? globalConfig.getApiUrl() : null,
                globalConfig != null ? globalConfig.getApiKey() : null,
                embeddingModel,
                embeddingModel,
                globalConfig != null ? globalConfig.getImageModel() : null,
                globalConfig != null ? globalConfig.getVideoModel() : null,
                globalConfig != null && globalConfig.getMemoryEnabled() != null && globalConfig.getMemoryEnabled() == 1
        );
    }

    public void validateApiConfig(ResolvedAgentPlanConfig config, String nodeName) {
        if (!StringUtils.hasText(config.apiUrl()) || !StringUtils.hasText(config.apiKey())
                || !StringUtils.hasText(config.model())) {
            throw new IllegalArgumentException(nodeName + " 节点缺少有效的 Agent Plan 配置，请选择全局配置或填写 API 信息");
        }
    }

    private Long parseLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Long.parseLong(text);
        }
        return null;
    }

    private String trim(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String canonicalizeProvider(String provider) {
        if (provider == null) {
            return null;
        }
        return switch (provider.trim().toLowerCase(Locale.ROOT)) {
            case "volcengine", "ark", "agent_plan", "agent plan", "火山方舟" -> "volcengine_agent_plan";
            case "open ai" -> "openai";
            case "deep seek" -> "deepseek";
            case "通义千问" -> "qwen";
            case "stepfun", "阶跃星辰" -> "step";
            case "智谱" -> "zhipu";
            case "ai ping" -> "ai_ping";
            case "agnes", "agnes ai" -> "agnes";
            default -> provider.trim().toLowerCase(Locale.ROOT);
        };
    }
}
