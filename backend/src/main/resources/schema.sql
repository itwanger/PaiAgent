-- 创建数据库
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS paiagent DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE paiagent;

-- 工作流表
CREATE TABLE IF NOT EXISTS workflow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '工作流主键 ID',
    name VARCHAR(255) NOT NULL COMMENT '工作流名称',
    description TEXT COMMENT '工作流描述',
    flow_data JSON NOT NULL COMMENT '工作流配置数据(节点和连线)',
    engine_type VARCHAR(50) DEFAULT 'dag' COMMENT '工作流引擎类型(dag/langgraph)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标识(0-未删除,1-已删除)',
    INDEX idx_created_at (created_at),
    INDEX idx_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流表';

-- 节点定义表
CREATE TABLE IF NOT EXISTS node_definition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '节点定义主键 ID',
    node_type VARCHAR(100) NOT NULL UNIQUE COMMENT '节点类型标识',
    display_name VARCHAR(255) NOT NULL COMMENT '显示名称',
    category VARCHAR(50) NOT NULL COMMENT '节点分类(LLM/TOOL)',
    icon VARCHAR(255) COMMENT '节点图标',
    input_schema JSON COMMENT '输入参数 JSON Schema',
    output_schema JSON COMMENT '输出参数 JSON Schema',
    config_schema JSON COMMENT '配置参数 JSON Schema',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标识(0-未删除,1-已删除)',
    INDEX idx_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='节点定义表';

-- 执行记录表
CREATE TABLE IF NOT EXISTS execution_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '执行记录主键 ID',
    flow_id BIGINT NOT NULL COMMENT '工作流 ID',
    input_data JSON COMMENT '输入数据',
    output_data JSON COMMENT '输出数据',
    status VARCHAR(50) NOT NULL COMMENT '执行状态(SUCCESS/FAILED)',
    node_results JSON COMMENT '每个节点的执行结果',
    error_message TEXT COMMENT '错误信息',
    duration INT COMMENT '执行耗时(毫秒)',
    executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标识(0-未删除,1-已删除)',
    INDEX idx_flow_id (flow_id),
    INDEX idx_flow_latest (flow_id, id),
    INDEX idx_executed_at (executed_at),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='执行记录表';

-- 执行快照表（断点续执行）
CREATE TABLE IF NOT EXISTS execution_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '快照主键 ID',
    execution_id BIGINT NOT NULL COMMENT '执行记录 ID',
    flow_id BIGINT NOT NULL COMMENT '工作流 ID',
    node_id VARCHAR(100) NOT NULL COMMENT '节点 ID',
    node_type VARCHAR(50) NOT NULL COMMENT '节点类型',
    node_name VARCHAR(255) COMMENT '节点名称',
    status VARCHAR(50) NOT NULL COMMENT '节点状态(PENDING/RUNNING/SUCCESS/FAILED/SKIPPED)',
    input_data JSON COMMENT '节点输入数据',
    output_data JSON COMMENT '节点输出数据',
    error_message TEXT COMMENT '错误信息',
    started_at TIMESTAMP NULL COMMENT '开始执行时间',
    completed_at TIMESTAMP NULL COMMENT '完成时间',
    duration INT COMMENT '执行耗时(毫秒)',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    execution_order INT DEFAULT 0 COMMENT '执行顺序',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_execution_id (execution_id),
    INDEX idx_flow_id (flow_id),
    INDEX idx_node_id (node_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='执行快照表';

-- 执行变量表（断点续执行）
CREATE TABLE IF NOT EXISTS execution_variable (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '变量主键 ID',
    execution_id BIGINT NOT NULL COMMENT '执行记录 ID',
    variable_name VARCHAR(100) NOT NULL COMMENT '变量名',
    variable_type VARCHAR(50) DEFAULT 'STRING' COMMENT '变量类型',
    variable_value TEXT COMMENT '变量值',
    is_modified TINYINT DEFAULT 0 COMMENT '是否被修改(0-否,1-是)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_execution_variable (execution_id, variable_name),
    INDEX idx_execution_id (execution_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='执行变量表';

-- 插入预置节点定义数据
INSERT INTO node_definition (node_type, display_name, category, icon, input_schema, output_schema, config_schema) VALUES
('input', '输入', 'IO', '📥',
 '{"type": "object", "properties": {}}',
 '{"type": "object", "properties": {"input": {"type": "string"}}}',
 '{"type": "object", "properties": {"defaultValue": {"type": "string"}}}'),

('output', '输出', 'IO', '📤',
 '{"type": "object", "properties": {"input": {"type": "string"}}}',
 '{"type": "object", "properties": {"output": {"type": "string"}}}',
 '{"type": "object", "properties": {}}'),

('openai', 'OpenAI', 'LLM', '🤖', 
 '{"type": "object", "properties": {"input": {"type": "string"}}}',
 '{"type": "object", "properties": {"output": {"type": "string"}, "tokens": {"type": "number"}}}',
 '{"type": "object", "properties": {"apiKey": {"type": "string"}, "model": {"type": "string", "default": "gpt-3.5-turbo"}, "prompt": {"type": "string"}, "temperature": {"type": "number", "default": 0.7}, "maxTokens": {"type": "number", "default": 1000}}}'),
 
('deepseek', 'DeepSeek', 'LLM', '🧠',
 '{"type": "object", "properties": {"input": {"type": "string"}}}',
 '{"type": "object", "properties": {"output": {"type": "string"}, "tokens": {"type": "number"}}}',
 '{"type": "object", "properties": {"apiKey": {"type": "string"}, "model": {"type": "string", "default": "deepseek-chat"}, "prompt": {"type": "string"}, "temperature": {"type": "number", "default": 0.7}, "maxTokens": {"type": "number", "default": 1000}}}'),
 
('qwen', '通义千问', 'LLM', '🌟',
 '{"type": "object", "properties": {"input": {"type": "string"}}}',
 '{"type": "object", "properties": {"output": {"type": "string"}, "tokens": {"type": "number"}}}',
 '{"type": "object", "properties": {"apiKey": {"type": "string"}, "model": {"type": "string", "default": "qwen-turbo"}, "prompt": {"type": "string"}, "temperature": {"type": "number", "default": 0.7}, "maxTokens": {"type": "number", "default": 1000}}}'),

('step', 'Step', 'LLM', '🟆',
 '{"type": "object", "properties": {"input": {"type": "string"}}}',
 '{"type": "object", "properties": {"output": {"type": "string"}, "tokens": {"type": "number"}}}',
 '{"type": "object", "properties": {"apiKey": {"type": "string"}, "model": {"type": "string", "default": "claude-3-5-sonnet-20241022"}, "prompt": {"type": "string"}, "temperature": {"type": "number", "default": 0.7}, "maxTokens": {"type": "number", "default": 1000}}}'),

('react_agent', 'ReAct Agent', 'LLM', 'RA',
 '{"type": "object", "properties": {"input": {"type": "string"}}}',
 '{"type": "object", "properties": {"output": {"type": "string"}, "finalAnswer": {"type": "string"}, "toolTrace": {"type": "array"}, "steps": {"type": "number"}, "tokens": {"type": "number"}}}',
 '{"type": "object", "properties": {"provider": {"type": "string"}, "configId": {"type": "number"}, "apiKey": {"type": "string"}, "model": {"type": "string"}, "prompt": {"type": "string"}, "temperature": {"type": "number", "default": 0.7}, "maxSteps": {"type": "number", "default": 5}, "tools": {"type": "array"}}}'),
 
('tts', '超拟人音频合成', 'TOOL', '🔊',
 '{"type": "object", "properties": {"text": {"type": "string"}}}',
 '{"type": "object", "properties": {"audioUrl": {"type": "string"}, "duration": {"type": "number"}, "fileSize": {"type": "number"}}}',
 '{"type": "object", "properties": {"provider": {"type": "string"}, "configId": {"type": "number"}, "apiUrl": {"type": "string"}, "apiKey": {"type": "string"}, "model": {"type": "string", "default": "qwen3-tts-flash"}, "voice": {"type": "string", "default": "Cherry"}, "languageType": {"type": "string", "default": "Auto"}, "instruction": {"type": "string"}, "speed": {"type": "number", "default": 1.0}, "volume": {"type": "number", "default": 1.0}, "sampleRate": {"type": "number", "default": 24000}}}'),

('condition', '条件分支', 'CONTROL', '🔀',
 '{"type": "object", "properties": {"input": {"type": "object"}}}',
 '{"type": "object", "properties": {"__selectedBranch__": {"type": "string"}, "__conditionNodeId__": {"type": "string"}}}',
 '{"type": "object", "properties": {"conditions": {"type": "array", "items": {"type": "object", "properties": {"id": {"type": "string"}, "field": {"type": "string"}, "operator": {"type": "string", "enum": ["eq", "neq", "gt", "gte", "lt", "lte", "contains", "notContains", "startsWith", "endsWith", "isEmpty", "isNotEmpty"]}, "value": {"type": "string"}}}}}}');

-- 全局模型配置表（LLM 与 TTS 共用）
CREATE TABLE IF NOT EXISTS llm_global_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '配置主键 ID',
    provider VARCHAR(50) NOT NULL COMMENT '提供商: openai/deepseek/qwen/step/zhipu/ai_ping/apifree',
    config_name VARCHAR(100) NOT NULL COMMENT '配置名称',
    api_url VARCHAR(255) NOT NULL COMMENT 'API地址',
    api_key TEXT NOT NULL COMMENT 'API密钥',
    model VARCHAR(100) NOT NULL COMMENT '默认LLM模型',
    tts_model VARCHAR(100) DEFAULT NULL COMMENT '默认TTS模型',
    embedding_model VARCHAR(100) DEFAULT NULL COMMENT '默认向量模型',
    image_model VARCHAR(100) DEFAULT NULL COMMENT '默认图片生成模型',
    video_model VARCHAR(100) DEFAULT NULL COMMENT '默认视频生成模型',
    memory_enabled TINYINT DEFAULT 0 COMMENT '是否启用Agent Plan记忆能力',
    temperature DECIMAL(3,2) DEFAULT 0.7 COMMENT '默认温度',
    is_default TINYINT DEFAULT 0 COMMENT '是否默认配置(0-否,1-是)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标识(0-未删除,1-已删除)',
    UNIQUE KEY uk_provider_config_name (provider, config_name),
    INDEX idx_provider (provider),
    INDEX idx_is_default (is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='全局模型配置表';

-- Agent 记忆表（MVP 持久化结构，执行器第一版可先通过服务层封装使用）
CREATE TABLE IF NOT EXISTS agent_memory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记忆主键 ID',
    scope VARCHAR(50) NOT NULL DEFAULT 'workflow' COMMENT '记忆范围(workflow/user/global)',
    memory_type VARCHAR(50) DEFAULT 'fact' COMMENT '记忆类型',
    content TEXT NOT NULL COMMENT '记忆内容',
    tags VARCHAR(500) DEFAULT NULL COMMENT '标签',
    source VARCHAR(255) COMMENT '来源',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标识',
    INDEX idx_scope (scope),
    INDEX idx_memory_type (memory_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent长期记忆表';

CREATE TABLE IF NOT EXISTS agent_memory_embedding (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记忆向量主键 ID',
    memory_id BIGINT NOT NULL COMMENT '记忆 ID',
    model VARCHAR(100) NOT NULL COMMENT '向量模型',
    dimension INT DEFAULT NULL COMMENT '向量维度',
    embedding JSON COMMENT '向量数据',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_memory_id (memory_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent记忆向量表';

CREATE TABLE IF NOT EXISTS mcp_tool_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'MCP 工具配置 ID',
    name VARCHAR(100) NOT NULL COMMENT '名称',
    description VARCHAR(500) DEFAULT NULL COMMENT '描述',
    tool_type VARCHAR(50) DEFAULT 'custom' COMMENT '工具类型',
    tool_name VARCHAR(100) NOT NULL COMMENT '暴露给 Agent 的工具名',
    transport VARCHAR(30) DEFAULT 'stdio' COMMENT '传输方式',
    command VARCHAR(500) NOT NULL COMMENT '启动命令',
    args JSON COMMENT '启动参数',
    env JSON COMMENT '环境变量',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用',
    preset TINYINT DEFAULT 0 COMMENT '是否预设',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标识',
    INDEX idx_mcp_tool_name (tool_name),
    INDEX idx_mcp_tool_type (tool_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP工具配置表';

CREATE TABLE IF NOT EXISTS knowledge_base (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '知识库 ID',
    name VARCHAR(100) NOT NULL COMMENT '知识库名称',
    description VARCHAR(500) DEFAULT NULL COMMENT '知识库描述',
    config_id BIGINT DEFAULT NULL COMMENT 'Agent Plan 全局配置 ID',
    embedding_model VARCHAR(100) DEFAULT NULL COMMENT '向量模型',
    chunk_size INT DEFAULT 800 COMMENT '分片长度',
    chunk_overlap INT DEFAULT 100 COMMENT '分片重叠长度',
    status VARCHAR(30) DEFAULT 'DRAFT' COMMENT '状态',
    document_count INT DEFAULT 0 COMMENT '文档数',
    chunk_count INT DEFAULT 0 COMMENT '分片数',
    char_count BIGINT DEFAULT 0 COMMENT '字符数',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标识',
    INDEX idx_knowledge_base_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库表';

CREATE TABLE IF NOT EXISTS knowledge_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '知识文档 ID',
    knowledge_base_id BIGINT NOT NULL COMMENT '知识库 ID',
    title VARCHAR(255) DEFAULT NULL COMMENT '文档标题',
    source_type VARCHAR(30) DEFAULT 'TEXT' COMMENT '来源类型',
    source_url VARCHAR(1024) DEFAULT NULL COMMENT '来源 URL',
    file_name VARCHAR(255) DEFAULT NULL COMMENT '文件名',
    raw_text MEDIUMTEXT COMMENT '原始文本',
    tags JSON COMMENT '标签',
    status VARCHAR(30) DEFAULT 'IMPORTED' COMMENT '状态',
    char_count BIGINT DEFAULT 0 COMMENT '字符数',
    error_message TEXT COMMENT '错误信息',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标识',
    INDEX idx_knowledge_document_base (knowledge_base_id),
    INDEX idx_knowledge_document_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档表';

CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '知识片段主键 ID',
    knowledge_base_id BIGINT NOT NULL COMMENT '知识库 ID',
    document_id BIGINT DEFAULT NULL COMMENT '知识文档 ID',
    chunk_index INT DEFAULT 0 COMMENT '分片序号',
    title VARCHAR(255) DEFAULT NULL COMMENT '标题',
    content TEXT NOT NULL COMMENT '片段内容',
    source_url VARCHAR(1024) DEFAULT NULL COMMENT '来源 URL',
    tags JSON COMMENT '标签',
    embedding_model VARCHAR(100) DEFAULT NULL COMMENT '向量模型',
    embedding JSON COMMENT '向量数据',
    status VARCHAR(30) DEFAULT 'READY' COMMENT '状态',
    char_count INT DEFAULT 0 COMMENT '字符数',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标识',
    INDEX idx_knowledge_base_id (knowledge_base_id),
    INDEX idx_knowledge_document_id (document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库片段表';

CREATE TABLE IF NOT EXISTS knowledge_index_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '索引任务 ID',
    knowledge_base_id BIGINT NOT NULL COMMENT '知识库 ID',
    document_id BIGINT NOT NULL COMMENT '文档 ID',
    status VARCHAR(30) DEFAULT 'RUNNING' COMMENT '状态',
    progress INT DEFAULT 0 COMMENT '进度',
    total_chunks INT DEFAULT 0 COMMENT '总分片数',
    finished_chunks INT DEFAULT 0 COMMENT '完成分片数',
    error_message TEXT COMMENT '错误信息',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标识',
    INDEX idx_knowledge_index_task_base (knowledge_base_id),
    INDEX idx_knowledge_index_task_document (document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库索引任务表';
