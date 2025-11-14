# Workflow Java 版本开发完整指南

本指南详细说明如何将 Python 版本的 `core/workflow/` 翻译为 Java 版本 `core-workflow-java/`，并确保双版本平滑切换。

---

## 📋 目录

- [1. 双版本架构概述](#1-双版本架构概述)
- [2. 快速切换命令](#2-快速切换命令)
- [3. 数据库隔离策略](#3-数据库隔离策略)
- [4. Java 项目结构](#4-java-项目结构)
- [5. 开发流程](#5-开发流程)
- [6. 功能对照表](#6-功能对照表)
- [7. 关键实现细节](#7-关键实现细节)
- [8. 测试和对比](#8-测试和对比)
- [9. 故障排查](#9-故障排查)

---

## 1. 双版本架构概述

### 1.1 设计理念

```
┌─────────────────────────────────────────────────────────────┐
│              Workflow 双版本并行架构                           │
└─────────────────────────────────────────────────────────────┘

                    Nginx :80
                        │
        ┌───────────────┴───────────────┐
        │                               │
        ▼                               ▼
Python Version (Port 7880)    Java Version (Port 7881)
  [生产稳定基线]                  [开发版本]
        │                               │
        ├─ DB: workflow_python          ├─ DB: workflow_java
        ├─ 只读参考                      ├─ 可以修改
        └─ 随时可回滚                    └─ 独立开发

特点:
✅ 独立端口 - 不冲突
✅ 独立数据库 - 数据隔离
✅ 一键切换 - 秒级回滚
✅ 对比测试 - 验证一致性
```

### 1.2 关键原则

**⚠️ 黄金法则**:
1. **永远不要修改 Python 版本代码** - 它是参考基线
2. **遇到问题立即切换到 Python** - 对比学习
3. **每次修改后都要重启 Java 版本** - 使用脚本自动化
4. **定期对比两个版本的输出** - 确保一致性

**✅ 开发原则**:
- Python 版本 = 稳定参考实现
- Java 版本 = 积极开发版本
- 切换成本 < 3秒
- 数据完全隔离

---

## 2. 快速切换命令

### 2.1 版本切换脚本

| 脚本 | 功能 | 使用场景 |
|------|------|----------|
| `./scripts/switch-to-python.sh` | 切换到 Python 版本 | Java 版本出问题时 |
| `./scripts/switch-to-java.sh` | 切换到 Java 版本 | 继续开发 Java |
| `./scripts/restart-java-workflow.sh` | 重启 Java 版本 | 修改代码后 |
| `./scripts/compare-workflows.sh` | 对比两个版本 | 验证一致性 |

### 2.2 典型开发流程

```bash
# 1. 修改 Java 代码
vim core-workflow-java/src/main/java/com/iflytek/astron/workflow/...

# 2. 重启 Java 版本（自动编译、构建、重启）
./scripts/restart-java-workflow.sh

# 3. 测试 Java 版本
curl -X POST http://localhost:7881/workflow/v1/debug/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"workflow_id":"184710","user_input":"测试"}'

# 4. 如果出问题，立即切换到 Python 版本
./scripts/switch-to-python.sh

# 5. 对比 Python 版本的行为
docker logs -f astron-agent-core-workflow-python

# 6. 修复 Java 代码后再切回来
vim core-workflow-java/...
./scripts/restart-java-workflow.sh

# 7. 对比两个版本的输出
./scripts/compare-workflows.sh 184710
```

### 2.3 紧急回滚

```bash
# 一键回滚到稳定的 Python 版本
./scripts/switch-to-python.sh

# 输出示例:
# ========================================
#   切换到 Python Workflow
# ========================================
# [1/3] 停止 Java Workflow...
# ✓ Java Workflow 已停止
# [2/3] 启动 Python Workflow...
# ✓ Python Workflow 已启动
# [3/3] 更新路由配置...
# ✓ 已切换到 Python 版本
# 
# 服务信息：
#   版本: Python
#   端口: 7880
#   日志: docker logs -f astron-agent-core-workflow-python
```

---

## 3. 数据库隔离策略

### 3.1 数据库设计

| 版本 | 数据库名 | 端口 | 状态 | 说明 |
|------|---------|------|------|------|
| Python | `workflow_python` | 7880 | 只读 | 生产基线数据，不要修改 |
| Java | `workflow_java` | 7881 | 读写 | 开发数据，可以随意修改 |

### 3.2 表结构

**两个数据库的表结构完全一致**:

```sql
-- workflow 表
CREATE TABLE workflow (
    id BIGINT PRIMARY KEY,
    workflow_id VARCHAR(64) UNIQUE,
    name VARCHAR(255),
    dsl_data JSON,              -- 工作流 DSL (nodes + edges)
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- workflow_execution 表
CREATE TABLE workflow_execution (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    workflow_id VARCHAR(64),
    execution_id VARCHAR(64) UNIQUE,
    user_input JSON,
    final_output JSON,
    status VARCHAR(20),         -- running, success, failed
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    error_message TEXT
);

-- node_execution_log 表
CREATE TABLE node_execution_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    execution_id VARCHAR(64),
    node_id VARCHAR(255),
    node_type VARCHAR(50),
    inputs JSON,
    outputs JSON,
    status VARCHAR(20),
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    error_message TEXT,
    INDEX idx_execution_id (execution_id)
);
```

### 3.3 初始化数据库

```bash
# 连接到 MySQL
docker exec -it astron-agent-mysql mysql -uroot -proot123

# 创建 Java 版本数据库
CREATE DATABASE IF NOT EXISTS workflow_java CHARACTER SET utf8mb4;

# 切换数据库
USE workflow_java;

# 导入表结构（从 Python 版本复制）
# 或者运行初始化脚本
source /docker-entrypoint-initdb.d/workflow-schema.sql;

# 验证表创建成功
SHOW TABLES;
```

### 3.4 数据隔离验证

```bash
# 查看 Python 版本数据库
docker exec -it astron-agent-mysql mysql -uroot -proot123 workflow_python -e "SELECT COUNT(*) FROM workflow;"

# 查看 Java 版本数据库
docker exec -it astron-agent-mysql mysql -uroot -proot123 workflow_java -e "SELECT COUNT(*) FROM workflow;"

# 确保两个数据库独立，互不影响
```

---

## 4. Java 项目结构

### 4.1 完整目录结构

```
core-workflow-java/
├── src/
│   ├── main/
│   │   ├── java/com/iflytek/astron/workflow/
│   │   │   ├── WorkflowApplication.java           # Spring Boot 启动类
│   │   │   │
│   │   │   ├── controller/                        # REST API 层
│   │   │   │   ├── WorkflowController.java        # /workflow/v1/* 接口
│   │   │   │   └── HealthController.java          # 健康检查
│   │   │   │
│   │   │   ├── service/                           # 业务服务层
│   │   │   │   ├── WorkflowService.java           # 工作流服务
│   │   │   │   ├── ModelServiceClient.java        # 模型服务客户端
│   │   │   │   └── AIToolsClient.java             # AI工具客户端
│   │   │   │
│   │   │   ├── engine/                            # 工作流引擎核心
│   │   │   │   ├── WorkflowEngine.java            # 执行引擎
│   │   │   │   ├── VariablePool.java              # 变量池
│   │   │   │   └── StreamEmitter.java             # SSE 流推送
│   │   │   │
│   │   │   ├── engine/node/                       # 节点执行器
│   │   │   │   ├── NodeExecutor.java              # 节点执行器接口
│   │   │   │   ├── AbstractNodeExecutor.java      # 抽象基类
│   │   │   │   └── impl/
│   │   │   │       ├── StartNodeExecutor.java     # 开始节点
│   │   │   │       ├── EndNodeExecutor.java       # 结束节点
│   │   │   │       ├── LLMNodeExecutor.java       # 大模型节点
│   │   │   │       └── PluginNodeExecutor.java    # 插件节点
│   │   │   │
│   │   │   ├── domain/                            # 领域模型（DSL）
│   │   │   │   ├── WorkflowDSL.java               # 工作流 DSL
│   │   │   │   ├── Node.java                      # 节点定义
│   │   │   │   ├── Edge.java                      # 边定义
│   │   │   │   ├── NodeData.java                  # 节点数据
│   │   │   │   ├── NodeParam.java                 # 节点参数
│   │   │   │   ├── InputItem.java                 # 输入项
│   │   │   │   ├── OutputItem.java                # 输出项
│   │   │   │   └── NodeRef.java                   # 节点引用
│   │   │   │
│   │   │   ├── entity/                            # 数据库实体
│   │   │   │   ├── WorkflowEntity.java            # workflow 表
│   │   │   │   ├── WorkflowExecutionEntity.java   # workflow_execution 表
│   │   │   │   └── NodeExecutionLogEntity.java    # node_execution_log 表
│   │   │   │
│   │   │   ├── mapper/                            # MyBatis Mapper
│   │   │   │   ├── WorkflowMapper.java
│   │   │   │   ├── WorkflowExecutionMapper.java
│   │   │   │   └── NodeExecutionLogMapper.java
│   │   │   │
│   │   │   ├── dto/                               # 数据传输对象
│   │   │   │   ├── WorkflowExecuteRequest.java    # 执行请求
│   │   │   │   ├── WorkflowExecuteResponse.java   # 执行响应
│   │   │   │   └── SSEEvent.java                  # SSE 事件
│   │   │   │
│   │   │   ├── config/                            # 配置类
│   │   │   │   ├── MyBatisConfig.java
│   │   │   │   ├── RestTemplateConfig.java
│   │   │   │   └── AsyncConfig.java
│   │   │   │
│   │   │   └── exception/                         # 异常处理
│   │   │       ├── WorkflowException.java
│   │   │       └── GlobalExceptionHandler.java
│   │   │
│   │   └── resources/
│   │       ├── application.yml                    # Spring Boot 配置
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       └── mapper/                            # MyBatis XML
│   │           ├── WorkflowMapper.xml
│   │           ├── WorkflowExecutionMapper.xml
│   │           └── NodeExecutionLogMapper.xml
│   │
│   └── test/                                      # 测试代码
│       └── java/com/iflytek/astron/workflow/
│           ├── WorkflowApplicationTests.java
│           ├── engine/
│           │   └── WorkflowEngineTest.java
│           └── node/
│               ├── LLMNodeExecutorTest.java
│               └── PluginNodeExecutorTest.java
│
├── pom.xml                                        # Maven 依赖
├── Dockerfile                                     # Docker 镜像
└── README.md                                      # 项目说明
```

### 4.2 与 Python 版本对照

| Python 路径 | Java 路径 | 说明 |
|------------|----------|------|
| `core/workflow/main.py` | `WorkflowApplication.java` | 启动类 |
| `core/workflow/api/v1/protocol/` | `controller/WorkflowController.java` | API 接口 |
| `core/workflow/service/` | `service/WorkflowService.java` | 业务服务 |
| `core/workflow/engine/workflow_executor.py` | `engine/WorkflowEngine.java` | 执行引擎 |
| `core/workflow/engine/entities/variable_pool.py` | `engine/VariablePool.java` | 变量池 |
| `core/workflow/domain/entities/workflow_dsl.py` | `domain/WorkflowDSL.java` | DSL 模型 |
| `core/workflow/engine/nodes/start/` | `engine/node/impl/StartNodeExecutor.java` | 开始节点 |
| `core/workflow/engine/nodes/llm/` | `engine/node/impl/LLMNodeExecutor.java` | 大模型节点 |
| `core/workflow/engine/nodes/plugin_tool/` | `engine/node/impl/PluginNodeExecutor.java` | 插件节点 |

---

## 5. 开发流程

### 5.1 环境准备

```bash
# 1. 确保 Java 环境正确
java -version  # 应该是 Java 21

# 如果不是，使用 jenv 切换
jenv local 21
jenv versions

# 2. 确保 Maven 可用
mvn -version

# 3. 确保 Docker 运行中
docker ps

# 4. 确保 Python 版本已启动（作为参考）
docker compose -f docker-compose.workflow-dual.yml up -d core-workflow-python
```

### 5.2 Phase 1: 基础框架（Day 1-3）

#### 任务清单

- [ ] **DSL 模型**
  - [ ] `WorkflowDSL.java` - 工作流顶层结构
  - [ ] `Node.java` - 节点定义
  - [ ] `Edge.java` - 边定义
  - [ ] `NodeData.java` - 节点数据
  - [ ] `InputItem.java` / `OutputItem.java` - 输入输出

**参考 Python 代码**:
```python
# core/workflow/domain/entities/workflow_dsl.py
@dataclass
class WorkflowDSL:
    nodes: List[Node]
    edges: List[Edge]
    
@dataclass
class Node:
    id: str
    type: str
    position: dict
    data: NodeData
```

**Java 实现**:
```java
// core-workflow-java/src/main/java/com/iflytek/astron/workflow/domain/WorkflowDSL.java
@Data
public class WorkflowDSL {
    private List<Node> nodes;
    private List<Edge> edges;
}

@Data
public class Node {
    private String id;
    private String type;
    private Map<String, Object> position;
    private NodeData data;
}
```

- [ ] **变量池 (VariablePool)**
  - [ ] 存储节点输出
  - [ ] 解析变量引用 `{{node_id.output_name}}`
  - [ ] 支持字面值和引用值

**参考 Python 代码**:
```python
# core/workflow/engine/entities/variable_pool.py
class VariablePool:
    def __init__(self):
        self.variables = {}
    
    def set(self, node_id: str, outputs: dict):
        self.variables[node_id] = outputs
    
    def get(self, node_id: str, output_name: str):
        return self.variables[node_id][output_name]
    
    def resolve_value(self, value_config):
        if value_config['type'] == 'literal':
            return value_config['content']
        elif value_config['type'] == 'ref':
            ref = value_config['content']
            return self.get(ref['nodeId'], ref['name'])
```

**Java 实现**:
```java
// core-workflow-java/src/main/java/com/iflytek/astron/workflow/engine/VariablePool.java
@Component
public class VariablePool {
    private Map<String, Map<String, Object>> variables = new HashMap<>();
    
    public void set(String nodeId, Map<String, Object> outputs) {
        variables.put(nodeId, outputs);
    }
    
    public Object get(String nodeId, String outputName) {
        return variables.get(nodeId).get(outputName);
    }
    
    public Object resolveValue(Map<String, Object> valueConfig) {
        String type = (String) valueConfig.get("type");
        if ("literal".equals(type)) {
            return valueConfig.get("content");
        } else if ("ref".equals(type)) {
            Map<String, String> ref = (Map) valueConfig.get("content");
            return get(ref.get("nodeId"), ref.get("name"));
        }
        throw new IllegalArgumentException("Unknown value type: " + type);
    }
}
```

- [ ] **执行引擎 (WorkflowEngine)**
  - [ ] 解析 DSL
  - [ ] 按拓扑顺序执行节点
  - [ ] SSE 流式推送进度

**参考 Python 代码**:
```python
# core/workflow/engine/workflow_executor.py
class WorkflowExecutor:
    async def execute(self, workflow_id: str, user_input: dict):
        # 1. 加载工作流 DSL
        dsl = await self.load_workflow(workflow_id)
        
        # 2. 初始化变量池
        pool = VariablePool()
        
        # 3. 按顺序执行节点
        for node in dsl.nodes:
            executor = self.get_executor(node.type)
            outputs = await executor.execute(node, pool)
            pool.set(node.id, outputs)
            
            # SSE 推送进度
            await self.push_progress(node, outputs)
```

**Java 实现**:
```java
// core-workflow-java/src/main/java/com/iflytek/astron/workflow/engine/WorkflowEngine.java
@Service
public class WorkflowEngine {
    @Autowired
    private VariablePool variablePool;
    
    @Autowired
    private Map<String, NodeExecutor> executors;  // Spring 自动注入
    
    public void execute(String workflowId, Map<String, Object> userInput, StreamCallback callback) {
        // 1. 加载工作流 DSL
        WorkflowDSL dsl = loadWorkflow(workflowId);
        
        // 2. 初始化变量池
        variablePool.clear();
        
        // 3. 按顺序执行节点
        for (Node node : dsl.getNodes()) {
            NodeExecutor executor = executors.get(node.getType());
            Map<String, Object> outputs = executor.execute(node, variablePool);
            variablePool.set(node.getId(), outputs);
            
            // SSE 推送进度
            callback.onProgress(node, outputs);
        }
    }
}
```

### 5.3 Phase 2: 节点实现（Day 4-7）

#### 任务清单

- [ ] **开始节点 (StartNode)**

**Python 参考**:
```python
# core/workflow/engine/nodes/start/start_node.py
class StartNode:
    async def execute(self, inputs):
        return {"AGENT_USER_INPUT": inputs['user_input']}
```

**Java 实现**:
```java
// StartNodeExecutor.java
@Component("node-start")
public class StartNodeExecutor extends AbstractNodeExecutor {
    @Override
    public Map<String, Object> execute(Node node, VariablePool pool) {
        Map<String, Object> outputs = new HashMap<>();
        // 从初始输入获取用户输入
        Object userInput = pool.get("__initial__", "user_input");
        outputs.put("AGENT_USER_INPUT", userInput);
        return outputs;
    }
}
```

- [ ] **大模型节点 (LLMNode)**

**Python 参考**:
```python
# core/workflow/engine/nodes/llm/spark_llm_node.py
class LLMNode:
    async def execute(self, node, pool):
        # 1. 获取输入
        input_text = pool.resolve_value(node.data.inputs[0].value)
        
        # 2. 渲染 Prompt 模板
        template = node.data.nodeParam['template']
        prompt = template.replace('{{input}}', input_text)
        
        # 3. 调用 DeepSeek API
        response = await http_client.post(
            node.data.nodeParam['url'],
            json={
                "model": node.data.nodeParam['domain'],
                "messages": [{"role": "user", "content": prompt}],
                "max_tokens": node.data.nodeParam['maxTokens'],
                "temperature": node.data.nodeParam['temperature']
            }
        )
        
        # 4. 返回结果
        return {"output": response['choices'][0]['message']['content']}
```

**Java 实现**:
```java
// LLMNodeExecutor.java
@Component("spark-llm")
public class LLMNodeExecutor extends AbstractNodeExecutor {
    @Autowired
    private RestTemplate restTemplate;
    
    @Override
    public Map<String, Object> execute(Node node, VariablePool pool) {
        NodeData data = node.getData();
        Map<String, Object> nodeParam = data.getNodeParam();
        
        // 1. 获取输入
        Object inputValue = pool.resolveValue(data.getInputs().get(0).getValue());
        
        // 2. 渲染 Prompt 模板
        String template = (String) nodeParam.get("template");
        String prompt = template.replace("{{input}}", inputValue.toString());
        
        // 3. 调用 DeepSeek API
        Map<String, Object> request = new HashMap<>();
        request.put("model", nodeParam.get("domain"));
        request.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        request.put("max_tokens", nodeParam.get("maxTokens"));
        request.put("temperature", nodeParam.get("temperature"));
        
        String url = (String) nodeParam.get("url");
        Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
        
        // 4. 返回结果
        String output = (String) ((Map) ((Map) ((List) response.get("choices")).get(0)).get("message")).get("content");
        return Map.of("output", output);
    }
}
```

- [ ] **插件节点 (PluginNode)**

**Python 参考**:
```python
# core/workflow/engine/nodes/plugin_tool/plugin_node.py
class PluginNode:
    async def execute(self, node, pool):
        # 1. 解析输入参数
        params = {}
        for input_item in node.data.inputs:
            params[input_item.name] = pool.resolve_value(input_item.value)
        
        # 2. 调用 core-link
        body = base64.b64encode(json.dumps(params).encode()).decode()
        response = await http_client.post(
            "http://core-link:18888/api/v1/tools/http_run",
            json={
                "header": {"app_id": node.data.nodeParam['appId']},
                "parameter": {
                    "tool_id": node.data.nodeParam['pluginId'],
                    "operation_id": node.data.nodeParam['operationId'],
                    "version": node.data.nodeParam['version']
                },
                "payload": {"message": {"body": body}}
            }
        )
        
        return response.json()
```

**Java 实现**:
```java
// PluginNodeExecutor.java
@Component("plugin")
public class PluginNodeExecutor extends AbstractNodeExecutor {
    @Autowired
    private RestTemplate restTemplate;
    
    @Override
    public Map<String, Object> execute(Node node, VariablePool pool) {
        NodeData data = node.getData();
        Map<String, Object> nodeParam = data.getNodeParam();
        
        // 1. 解析输入参数
        Map<String, Object> params = new HashMap<>();
        for (InputItem input : data.getInputs()) {
            params.put(input.getName(), pool.resolveValue(input.getValue()));
        }
        
        // 2. 调用 core-link
        String body = Base64.getEncoder().encodeToString(
            new ObjectMapper().writeValueAsString(params).getBytes()
        );
        
        Map<String, Object> request = Map.of(
            "header", Map.of("app_id", nodeParam.get("appId")),
            "parameter", Map.of(
                "tool_id", nodeParam.get("pluginId"),
                "operation_id", nodeParam.get("operationId"),
                "version", nodeParam.get("version")
            ),
            "payload", Map.of("message", Map.of("body", body))
        );
        
        String url = "http://core-link:18888/api/v1/tools/http_run";
        return restTemplate.postForObject(url, request, Map.class);
    }
}
```

- [ ] **结束节点 (EndNode)**

**Python 参考**:
```python
# core/workflow/engine/nodes/end/end_node.py
class EndNode:
    async def execute(self, node, pool):
        output_value = pool.resolve_value(node.data.inputs[0].value)
        template = node.data.nodeParam.get('template', '{{output}}')
        result = template.replace('{{output}}', str(output_value))
        return {"output": result}
```

**Java 实现**:
```java
// EndNodeExecutor.java
@Component("node-end")
public class EndNodeExecutor extends AbstractNodeExecutor {
    @Override
    public Map<String, Object> execute(Node node, VariablePool pool) {
        NodeData data = node.getData();
        Object outputValue = pool.resolveValue(data.getInputs().get(0).getValue());
        
        String template = (String) data.getNodeParam().getOrDefault("template", "{{output}}");
        String result = template.replace("{{output}}", outputValue.toString());
        
        return Map.of("output", result);
    }
}
```

### 5.4 Phase 3: API 集成（Day 8-10）

- [ ] **Controller 实现**

```java
// WorkflowController.java
@RestController
@RequestMapping("/workflow/v1")
public class WorkflowController {
    @Autowired
    private WorkflowService workflowService;
    
    @PostMapping("/debug/chat/completions")
    public SseEmitter debugWorkflow(@RequestBody WorkflowExecuteRequest request) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L); // 30分钟超时
        
        workflowService.executeAsync(
            request.getWorkflowId(),
            request.getUserInput(),
            new StreamCallback() {
                @Override
                public void onProgress(Node node, Map<String, Object> outputs) {
                    try {
                        SSEEvent event = buildSSEEvent(node, outputs);
                        emitter.send(SseEmitter.event()
                            .name("message")
                            .data(event));
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                }
                
                @Override
                public void onComplete() {
                    try {
                        emitter.send(SseEmitter.event().data("[DONE]"));
                        emitter.complete();
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                }
            }
        );
        
        return emitter;
    }
}
```

- [ ] **Service 层**
- [ ] **MyBatis Mapper**
- [ ] **集成测试**

---

## 6. 功能对照表

### 6.1 核心功能清单

| 功能模块 | Python 完成度 | Java 目标 | 优先级 |
|---------|-------------|---------|--------|
| DSL 解析 | ✅ 100% | ⬜ 0% | P0 |
| 变量池 | ✅ 100% | ⬜ 0% | P0 |
| 执行引擎 | ✅ 100% | ⬜ 0% | P0 |
| 开始节点 | ✅ 100% | ⬜ 0% | P0 |
| 结束节点 | ✅ 100% | ⬜ 0% | P0 |
| 大模型节点 | ✅ 100% | ⬜ 0% | P0 |
| 插件节点 | ✅ 100% | ⬜ 0% | P0 |
| SSE 流推送 | ✅ 100% | ⬜ 0% | P0 |
| 条件分支节点 | ✅ 100% | ⬜ 0% | P1 |
| 循环节点 | ✅ 100% | ⬜ 0% | P1 |
| 知识库节点 | ✅ 100% | ⬜ 0% | P2 |

### 6.2 API 接口对照

| API | Python 路径 | Java 路径 | 说明 |
|-----|-----------|---------|------|
| 构建工作流 | `POST /workflow/v1/protocol/build/{id}` | 同左 | 解析并存储 DAG |
| 调试执行 | `POST /workflow/v1/debug/chat/completions` | 同左 | SSE 流式执行 |
| 健康检查 | `GET /health` | `GET /actuator/health` | 服务状态 |

---

## 7. 关键实现细节

### 7.1 SSE 流式推送

**Python 实现**:
```python
# Python 使用 FastAPI + sse_starlette
from sse_starlette.sse import EventSourceResponse

async def stream_events():
    for node in nodes:
        yield {"data": json.dumps(event)}
    yield {"data": "[DONE]"}

return EventSourceResponse(stream_events())
```

**Java 实现**:
```java
// Java 使用 Spring Boot SseEmitter
SseEmitter emitter = new SseEmitter(timeout);

emitter.send(SseEmitter.event()
    .name("message")
    .data(event));

emitter.send(SseEmitter.event().data("[DONE]"));
emitter.complete();
```

### 7.2 异步 HTTP 调用

**Python 实现**:
```python
# Python 使用 aiohttp
async with aiohttp.ClientSession() as session:
    async with session.post(url, json=data) as response:
        return await response.json()
```

**Java 实现**:
```java
// Java 使用 RestTemplate (同步) 或 WebClient (异步)
// 方案 1: RestTemplate (简单，适合初期)
ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
return response.getBody();

// 方案 2: WebClient (异步，后期优化)
return webClient.post()
    .uri(url)
    .bodyValue(request)
    .retrieve()
    .bodyToMono(Map.class)
    .block();
```

### 7.3 模板渲染

**Python 实现**:
```python
# Python 使用 Jinja2
from jinja2 import Template

template = Template(node.data.nodeParam['template'])
result = template.render(input=input_value)
```

**Java 实现**:
```java
// Java 使用简单字符串替换（初期）
String template = (String) nodeParam.get("template");
String result = template.replace("{{input}}", inputValue.toString());

// 后期可使用 Thymeleaf 或 FreeMarker
```

---

## 8. 测试和对比

### 8.1 单元测试

```java
// WorkflowEngineTest.java
@SpringBootTest
class WorkflowEngineTest {
    @Autowired
    private WorkflowEngine engine;
    
    @Test
    void testSimpleWorkflow() {
        // 构建简单工作流：开始 → 结束
        WorkflowDSL dsl = buildSimpleDSL();
        
        // 执行
        Map<String, Object> result = engine.execute(dsl, Map.of("user_input", "测试"));
        
        // 验证
        assertEquals("测试", result.get("output"));
    }
}
```

### 8.2 对比测试

```bash
# 使用对比脚本测试同一个工作流
./scripts/compare-workflows.sh 184710

# 输出示例:
# ========================================
#   工作流版本对比
# ========================================
# 工作流 ID: 184710
# 测试输入: 沉默王二
# 
# [1/4] 测试 Python 版本...
# ✓ Python 版本执行成功
# 
# [2/4] 测试 Java 版本...
# ✓ Java 版本执行成功
# 
# [3/4] 对比输出...
# 
# Python 输出:
# {
#   "final_output": "欢迎收听王二电台...",
#   "voice_url": "http://localhost:18999/workflow/abc.mp3"
# }
# 
# Java 输出:
# {
#   "final_output": "欢迎收听王二电台...",
#   "voice_url": "http://localhost:18999/workflow/abc.mp3"
# }
# 
# [4/4] 结果
# ✅ 两个版本输出一致！
```

### 8.3 性能对比

```bash
# Python 版本性能
ab -n 100 -c 10 -p request.json http://localhost:7880/workflow/v1/debug/chat/completions

# Java 版本性能
ab -n 100 -c 10 -p request.json http://localhost:7881/workflow/v1/debug/chat/completions
```

---

## 9. 故障排查

### 9.1 常见问题

#### 问题 1: Java 版本无法启动

**症状**:
```bash
docker logs astron-agent-core-workflow-java
# 报错: Connection refused: mysql:3306
```

**排查**:
```bash
# 检查数据库是否启动
docker ps | grep mysql

# 检查数据库连接
docker exec -it astron-agent-mysql mysql -uroot -proot123 -e "SHOW DATABASES;"

# 检查 workflow_java 数据库是否存在
docker exec -it astron-agent-mysql mysql -uroot -proot123 -e "USE workflow_java; SHOW TABLES;"
```

**解决**:
```bash
# 创建数据库
docker exec -it astron-agent-mysql mysql -uroot -proot123 -e "CREATE DATABASE IF NOT EXISTS workflow_java;"

# 重启 Java 版本
./scripts/restart-java-workflow.sh
```

#### 问题 2: 执行结果与 Python 不一致

**排查步骤**:
```bash
# 1. 切换到 Python 版本
./scripts/switch-to-python.sh

# 2. 查看 Python 日志
docker logs -f astron-agent-core-workflow-python

# 3. 对比两个版本的日志
# Python 日志
docker logs astron-agent-core-workflow-python > /tmp/python.log

# Java 日志
docker logs astron-agent-core-workflow-java > /tmp/java.log

# 对比
diff /tmp/python.log /tmp/java.log
```

#### 问题 3: SSE 流推送断开

**症状**: 前端接收 SSE 流一半就断开

**排查**:
```bash
# 检查 Nginx 配置
cat docker/astronAgent/nginx/nginx.conf | grep -A 10 "workflow/v1/chat/completions"

# 确认有以下配置:
# proxy_buffering off;
# proxy_read_timeout 1800s;
```

**解决**:
```bash
# 如果 Nginx 配置错误，修改后重启
docker compose restart nginx
```

### 9.2 调试技巧

#### 技巧 1: 进入容器调试

```bash
# 进入 Java 容器
docker exec -it astron-agent-core-workflow-java sh

# 查看日志
tail -f /app/logs/workflow.log

# 检查环境变量
env | grep MYSQL
env | grep REDIS
```

#### 技巧 2: 使用 curl 测试

```bash
# 测试 Python 版本
curl -X POST http://localhost:7880/workflow/v1/debug/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "workflow_id": "184710",
    "user_input": "测试"
  }'

# 测试 Java 版本
curl -X POST http://localhost:7881/workflow/v1/debug/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "workflow_id": "184710",
    "user_input": "测试"
  }'
```

#### 技巧 3: 查看数据库数据

```bash
# 查看 Java 版本执行记录
docker exec -it astron-agent-mysql mysql -uroot -proot123 workflow_java -e "
  SELECT execution_id, status, created_at, final_output
  FROM workflow_execution
  ORDER BY created_at DESC
  LIMIT 10;
"

# 查看 Python 版本执行记录（对比）
docker exec -it astron-agent-mysql mysql -uroot -proot123 workflow_python -e "
  SELECT execution_id, status, created_at, final_output
  FROM workflow_execution
  ORDER BY created_at DESC
  LIMIT 10;
"
```

---

## 📚 相关文档

- [工作流创建与执行完整流程指南](./WORKFLOW-CREATION-EXECUTION-GUIDE.md)
- [本地构建部署指南](../docker/astronAgent/LOCAL-BUILD-GUIDE.md)
- [AGENTS.md](../AGENTS.md) - 项目整体开发指南
- [core-workflow-java/README.md](../core-workflow-java/README.md) - Java 项目说明

---

**最后更新**: 2025-11-14  
**维护者**: 沉默王二  
**当前状态**: ✅ 双版本基础设施已就绪，Java 版本开发进行中
