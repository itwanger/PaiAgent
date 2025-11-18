# Core 模块 - Agent 和 Memory 使用场景分析

## 概述

根据代码分析，`core/agent` 和 `core/memory` 是两个**独立的微服务模块**，在特定场景下才会被调用。

---

## 1️⃣ core/agent - Agent 编排服务

### 服务信息
- **服务名称**: `astron-agent-core-database` (实际运行的是 agent 模块)
- **启动命令**: `uv run memory/database/main.py` (容器配置有误，实际应该是 agent)
- **端口**: 未在 docker-compose 中暴露（内部服务）
- **技术栈**: FastAPI + Python 3.11

### 使用场景

#### 场景 1: 工作流中的 Agent 节点
**位置**: `core/workflow/engine/nodes/agent/agent_node.py`

**调用时机**:
- 用户在工作流编辑器中拖入 **Agent 节点**
- 工作流执行到 Agent 节点时

**功能**:
```python
class AgentNode(BaseNode):
    """
    Agent 节点 - 调用 core/agent 服务
    
    支持功能:
    - Chain-of-Thought (CoT) 推理
    - 多轮对话管理
    - 工具调用 (MCP、插件、知识库)
    - 知识库检索集成
    """
```

**请求流程**:
```
工作流引擎 (core/workflow)
    ↓ HTTP POST
core/agent 服务 (/agent/v1/completions)
    ↓ 调用
LLM 模型 (DeepSeek/讯飞星火)
    ↓ 工具调用
MCP 服务器 / Link 插件 / 知识库
    ↓ 返回
流式响应 → 工作流引擎
```

**关键代码**:
- API 入口: `core/agent/api/v1/workflow_agent.py:16`
- Agent 节点: `core/workflow/engine/nodes/agent/agent_node.py`
- 引擎: `core/agent/engine/workflow_agent_runner.py`

#### 场景 2: 开放平台 Bot API
**位置**: `core/agent/api/v1/openapi.py`

**调用时机**:
- 外部系统通过 API 调用 Bot
- 第三方集成播客工坊能力

**典型用户**:
- 企业内部系统集成
- 第三方应用接入
- 移动端应用

---

## 2️⃣ core/memory/database - 数据库操作服务

### 服务信息
- **服务名称**: `astron-agent-core-database`
- **启动命令**: `uv run memory/database/main.py`
- **端口**: 7990 (内部端口)
- **技术栈**: FastAPI + SQLAlchemy + PostgreSQL

### 使用场景

#### 场景 1: 工作流中的数据库节点
**位置**: `core/workflow/engine/nodes/pgsql/pgsql_node.py`

**调用时机**:
- 用户在工作流编辑器中拖入 **数据库节点** (`idType: "database"`)
- 工作流执行时需要操作 PostgreSQL 数据库

**支持的数据库操作**:
```python
class DBMode(Enum):
    """数据库操作模式"""
    INSERT = "insert"      # 插入数据
    UPDATE = "update"      # 更新数据
    SELECT = "select"      # 查询数据
    DELETE = "delete"      # 删除数据
    EXECUTE = "execute"    # 执行自定义 SQL
```

**请求流程**:
```
工作流引擎 (core/workflow)
    ↓ 数据库节点
PGSqlNode.run()
    ↓ HTTP 请求
core/memory/database 服务 (7990端口)
    ↓ SQL 执行
PostgreSQL 数据库
    ↓ 返回结果
工作流引擎
```

**API 端点**:
- 创建数据库: `POST /v1/database/create`
- 删除数据库: `POST /v1/database/drop`
- 执行 DDL: `POST /v1/database/exec/ddl`
- 执行 DML: `POST /v1/database/exec/dml`
- 查询数据: `POST /v1/database/query`
- 导入数据: `POST /v1/database/upload`
- 导出数据: `POST /v1/database/export`

**关键代码**:
- API 路由: `core/memory/database/api/router.py`
- 数据库客户端: `core/memory/database/repository/middleware/database/db_manager.py`
- 工作流节点: `core/workflow/engine/nodes/pgsql/pgsql_node.py`

#### 场景 2: 知识库数据持久化
**用途**: 存储向量数据库元数据、知识库索引等

---

## 📊 服务依赖关系

```
console-hub (Spring Boot)
    ↓ 调用
core/workflow (FastAPI)
    ↓ 根据节点类型调用
    ├─→ core/agent (Agent 节点)
    │       ├─→ LLM 服务
    │       ├─→ core/plugin/link (工具调用)
    │       └─→ core/knowledge (知识库)
    │
    ├─→ core/memory/database (数据库节点)
    │       └─→ PostgreSQL
    │
    ├─→ core/plugin/aitools (语音合成节点)
    └─→ core/plugin/rpa (RPA 节点)
```

---

## 🎯 判断是否需要这两个服务

### ✅ 需要 core/agent 的情况

1. **工作流中使用 Agent 节点**
   - 节点配置中有 `idType: "agent"`
   - 需要 CoT 推理能力
   - 需要多轮对话管理

2. **需要工具调用 (Tool Calling)**
   - Agent 需要调用 MCP 服务器
   - Agent 需要访问知识库
   - Agent 需要执行复杂推理

3. **开放平台 API**
   - 外部系统需要调用 Bot API
   - 第三方集成

### ✅ 需要 core/memory/database 的情况

1. **工作流中使用数据库节点**
   - 节点配置中有 `idType: "database"`
   - 需要在工作流中执行 SQL 操作
   - 需要读写 PostgreSQL 数据

2. **动态数据管理**
   - 工作流需要持久化中间结果
   - 需要跨工作流共享数据

### ❌ 不需要的情况

**如果你的工作流只包含**:
- ✅ 开始/结束节点
- ✅ LLM 节点 (大模型)
- ✅ 工具节点 (Plugin)
- ✅ 语音合成节点 (AI Tools)

**则可以禁用**:
- ❌ core/agent 服务
- ❌ core/memory/database 服务

---

## 🔧 如何禁用这些服务

### 方案 1: Docker Compose 禁用

编辑 `docker/astronAgent/docker-compose.yml`:

```yaml
# 注释掉以下服务
# core-database:
#   image: ...
#   ...
```

### 方案 2: 节点模板过滤

通过之前配置的 `SPACE_SWITCH_NODE` 过滤掉 Agent 和数据库节点:

```sql
UPDATE config_info 
SET value = 'agent,database,node-start,node-end,ifly-code,...'
WHERE category = 'SPACE_SWITCH_NODE';
```

这样前端就不会显示这两种节点，用户无法使用它们。

---

## 📝 配置验证

### 检查 Agent 节点是否被使用

```sql
-- 查询工作流中是否有 Agent 节点
SELECT w.id, w.name, w.data 
FROM workflow w 
WHERE w.data LIKE '%"idType":"agent"%';
```

### 检查数据库节点是否被使用

```sql
-- 查询工作流中是否有数据库节点
SELECT w.id, w.name, w.data 
FROM workflow w 
WHERE w.data LIKE '%"idType":"database"%';
```

---

## 🎬 总结

| 服务 | 用途 | 何时启动 | 是否必需 |
|------|------|----------|---------|
| **core/agent** | Agent 编排、CoT 推理、工具调用 | 工作流使用 Agent 节点时 | ❌ (简单场景不需要) |
| **core/memory/database** | PostgreSQL 数据库操作 | 工作流使用数据库节点时 | ❌ (不操作数据库不需要) |
| **core/workflow** | 工作流引擎 | 所有工作流执行 | ✅ (核心服务) |
| **core/plugin/aitools** | 语音合成 | 播客生成场景 | ✅ (播客必需) |
| **core/plugin/link** | 工具集成 | 工作流使用工具节点时 | ✅ (工具节点必需) |

**对于"播客工坊"项目**:
- 如果只使用 **LLM + 语音合成**，可以禁用 `core/agent` 和 `core/memory/database`
- 如果需要 **智能推理 + 工具调用**，则需要启用 `core/agent`
- 如果需要 **数据持久化**，则需要启用 `core/memory/database`
