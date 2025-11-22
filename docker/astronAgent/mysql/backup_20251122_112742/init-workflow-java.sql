-- ============================================================================
-- 数据库初始化脚本 - Java Workflow 专用
-- ============================================================================

-- 创建 Java Workflow 独立数据库
CREATE DATABASE IF NOT EXISTS workflow_java CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE workflow_java;

-- ============================================================================
-- 工作流定义表
-- ============================================================================
CREATE TABLE IF NOT EXISTS workflow (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    workflow_id VARCHAR(64) NOT NULL UNIQUE COMMENT '工作流ID',
    name VARCHAR(255) NOT NULL COMMENT '工作流名称',
    description TEXT COMMENT '工作流描述',
    version VARCHAR(32) DEFAULT 'v1.0.0' COMMENT '版本号',
    dsl_data JSON NOT NULL COMMENT 'DSL 定义（包含 nodes 和 edges）',
    status TINYINT DEFAULT 1 COMMENT '状态：1-启用 0-禁用',
    created_by VARCHAR(64) COMMENT '创建人',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
    INDEX idx_workflow_id (workflow_id),
    INDEX idx_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流定义表';

-- ============================================================================
-- 工作流执行历史表
-- ============================================================================
CREATE TABLE IF NOT EXISTS workflow_execution (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    execution_id VARCHAR(64) NOT NULL UNIQUE COMMENT '执行ID',
    workflow_id VARCHAR(64) NOT NULL COMMENT '工作流ID',
    status VARCHAR(20) DEFAULT 'RUNNING' COMMENT '执行状态：RUNNING, SUCCESS, FAILED',
    input_data JSON COMMENT '输入数据',
    output_data JSON COMMENT '输出数据',
    error_message TEXT COMMENT '错误信息',
    start_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    end_time DATETIME COMMENT '结束时间',
    duration_ms BIGINT COMMENT '执行时长（毫秒）',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_workflow_id (workflow_id),
    INDEX idx_execution_id (execution_id),
    INDEX idx_start_time (start_time),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流执行历史表';

-- ============================================================================
-- 节点执行日志表
-- ============================================================================
CREATE TABLE IF NOT EXISTS node_execution_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    execution_id VARCHAR(64) NOT NULL COMMENT '工作流执行ID',
    node_id VARCHAR(128) NOT NULL COMMENT '节点ID',
    node_type VARCHAR(50) NOT NULL COMMENT '节点类型',
    input_data JSON COMMENT '节点输入数据',
    output_data JSON COMMENT '节点输出数据',
    status VARCHAR(20) DEFAULT 'RUNNING' COMMENT '节点状态：RUNNING, SUCCESS, FAILED',
    error_message TEXT COMMENT '错误信息',
    start_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    end_time DATETIME COMMENT '结束时间',
    duration_ms BIGINT COMMENT '执行时长（毫秒）',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_execution_id (execution_id),
    INDEX idx_node_id (node_id),
    INDEX idx_start_time (start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='节点执行日志表';

-- ============================================================================
-- 插入测试数据（可选）
-- ============================================================================
INSERT INTO workflow (workflow_id, name, description, version, dsl_data, created_by) 
VALUES (
    '184736',
    'AI 播客生成工作流',
    '将文本改写为播客风格并生成语音',
    'v1.0.0',
    '{
        "nodes": [
            {
                "id": "node-start::001",
                "type": "node-start",
                "data": {
                    "outputs": [{"name": "user_input", "type": "string"}]
                }
            },
            {
                "id": "node-llm::002",
                "type": "node-llm",
                "data": {
                    "nodeParam": {"modelId": 1, "prompt": "改写为播客风格：{{node-start::001.user_input}}"},
                    "outputs": [{"name": "llm_output", "type": "string"}]
                }
            },
            {
                "id": "node-plugin::003",
                "type": "node-plugin",
                "data": {
                    "nodeParam": {"pluginId": "tool@8b2262bef821000", "vcn": "x5_lingfeiyi_flow", "speed": 50},
                    "outputs": [{"name": "voice_url", "type": "string"}]
                }
            },
            {
                "id": "node-end::004",
                "type": "node-end",
                "data": {
                    "nodeParam": {
                        "outputMode": 1,
                        "template": "<audio preload=\\"none\\" controls><source src=\\"{{node-plugin::003.voice_url}}\\" type=\\"audio/mpeg\\"></audio>"
                    }
                }
            }
        ],
        "edges": [
            {"sourceNodeId": "node-start::001", "targetNodeId": "node-llm::002"},
            {"sourceNodeId": "node-llm::002", "targetNodeId": "node-plugin::003"},
            {"sourceNodeId": "node-plugin::003", "targetNodeId": "node-end::004"}
        ]
    }',
    'system'
) ON DUPLICATE KEY UPDATE updated_time = CURRENT_TIMESTAMP;
