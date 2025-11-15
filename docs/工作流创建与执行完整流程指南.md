# 工作流创建与执行完整流程指南

本文档详细梳理从访问工作流编排页面到完成调试执行的完整技术流程，涵盖前端、后端、Nginx、数据库等各个层面。

---

## 📋 目录

- [1. 工作流页面访问流程](#1-工作流页面访问流程)
- [2. 添加节点流程](#2-添加节点流程)
- [3. 节点参数配置流程](#3-节点参数配置流程)
- [4. 工作流调试执行流程](#4-工作流调试执行流程)
- [5. 完整数据流图](#5-完整数据流图)
- [6. 关键代码位置索引](#6-关键代码位置索引)

---

## 1. 工作流页面访问流程

### 1.1 请求入口

**用户访问**: `http://localhost/work_flow/184710/arrange?botId=40`

### 1.2 Nginx 层处理

**配置文件**: `docker/astronAgent/nginx/nginx.conf`

```nginx
# Line 131-142: 前端应用代理
location / {
    proxy_pass http://console-frontend:1881;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    
    proxy_connect_timeout 30s;
    proxy_send_timeout 30s;
    proxy_read_timeout 30s;
}
```

**处理流程**:
1. Nginx 监听 80 端口接收请求
2. 匹配 `location /` 规则（前端路由都走这里）
3. 反向代理到 `console-frontend:1881`（React 应用）
4. 添加代理头（Host、IP、Forwarded 等）

### 1.3 前端路由层

**路由配置**: `console/frontend/src/router/index.tsx`

```typescript
// Line 237-243: 工作流编排页面路由
{
  path: '/work_flow/:id/arrange',
  element: (
    <Suspense fallback={<Loading />}>
      <WorkFlow />
    </Suspense>
  ),
}
```

**路由参数**:
- `:id` - 工作流 ID (184710)
- Query: `botId=40` - 关联的 Bot ID

### 1.4 前端主组件加载

**主组件**: `console/frontend/src/pages/workflow/index.tsx`

**核心逻辑**:

```typescript
// Line 22-26: 获取路由参数
const { id } = useParams();  // 获取 workflow ID = 184710
const location = useLocation();  // 获取 query 参数

// Line 58-60: 初始化工作流数据
useEffect(() => {
  id && initFlowData(id);  // 加载工作流数据
}, [id, location]);
```

**调用的 Store**: `components/workflow/store/use-flows-manager.ts`

```typescript
// 初始化工作流数据
const initFlowData = async (flowId: string) => {
  // 1. 调用后端 API 获取工作流详情
  // 2. 加载节点数据
  // 3. 加载模型列表
  // 4. 初始化画布状态
}
```

### 1.5 后端 API 调用

**API 接口**: `GET /console-api/workflow/detail/{id}`

**Nginx 代理配置**:

```nginx
# Line 117-128: 后端 API 代理
location /console-api/ {
    proxy_pass http://console-hub:8080/;
    # ... 省略其他配置
}
```

**Controller**: `console/backend/toolkit/src/main/java/com/iflytek/astron/console/toolkit/controller/workflow/WorkflowController.java`

```java
// 获取工作流详情
@GetMapping("/detail/{id}")
public WorkflowVo detail(@PathVariable Long id) {
    return workflowService.getWorkflowDetail(id);
}
```

**Service**: `console/backend/toolkit/src/main/java/com/iflytek/astron/console/toolkit/service/workflow/WorkflowService.java`

**数据库查询**: 
- 表: `astron_console.workflow`
- 字段: `id`, `name`, `data` (JSON格式的节点和边数据), `app_id`, `uid`, `space_id` 等

**返回数据结构**:
```json
{
  "id": 184710,
  "name": "自定义工作流",
  "data": "{\"nodes\":[...],\"edges\":[...]}",
  "appId": "680ab54f",
  "uid": "b55d5545-d432-405d-9ddf-3c21276512f2",
  "spaceId": 123,
  "createdTime": "2025-11-14 14:44:43",
  "updatedTime": "2025-11-14 15:09:56"
}
```

### 1.6 前端渲染画布

**画布组件**: `console/frontend/src/pages/workflow/components/flow-container/index.tsx`

**核心依赖**: 
- `react-flow-renderer` - 可视化流程图库
- `zustand` - 状态管理

**渲染流程**:
1. 解析 `data` 字段中的 JSON
2. 转换为 React Flow 节点和边
3. 渲染到画布上

**主要子组件**:
- `NodeList` - 左侧节点列表
- `FlowHeader` - 顶部工具栏
- `BtnGroups` - 保存、调试、发布按钮
- `FlowContainer` - 画布容器

---

## 2. 添加节点流程

### 2.1 节点列表组件

**组件**: `console/frontend/src/pages/workflow/components/node-list/index.tsx`

**节点分类**:
```typescript
const nodeCategories = [
  { name: '基础节点', items: ['开始', '结束', '大模型', '知识库', ...] },
  { name: '工具', items: ['超拟人合成', '文生图', 'OCR', ...] },
  { name: '逻辑控制', items: ['条件分支', '循环', ...] },
  // ...
]
```

**节点拖拽**:
```typescript
// 使用 react-dnd 库实现拖拽
const [{ isDragging }, drag] = useDrag(() => ({
  type: 'node',
  item: { nodeType: 'spark-llm' },  // 大模型节点
  collect: (monitor) => ({
    isDragging: monitor.isDragging(),
  }),
}))
```

### 2.2 大模型节点添加

**节点类型**: `spark-llm`

**拖拽到画布**:

1. **前端处理**: `FlowContainer` 组件监听 `onDrop` 事件
2. **生成节点数据**:
```typescript
const newNode = {
  id: `spark-llm::${uuid()}`,  // 例: spark-llm::348ce48c-0148-485f-9f3f-d64f38ed5eab
  type: 'custom',
  position: { x: dropX, y: dropY },
  data: {
    label: '大模型_1',
    nodeType: '基础节点',
    aliasName: '大模型',
    icon: 'https://oss-beijing-m8.openstorage.cn/.../largeModelIcon.png',
    inputs: [{
      id: uuid(),
      name: 'input',
      schema: { type: 'string', value: { type: 'ref', content: {} } }
    }],
    outputs: [{
      id: uuid(),
      name: 'output',
      schema: { type: 'string', default: '' }
    }],
    nodeParam: {
      template: '',  // Prompt 模板
      llmId: null,   // 模型 ID
      domain: '',    // 模型名称
      // ...
    }
  }
}
```

3. **更新状态**:
```typescript
// 添加节点到 Zustand store
setNodes((nodes) => [...nodes, newNode])
```

4. **保存到后端**: 
   - API: `POST /console-api/workflow/protocol/update/{id}`
   - Body: `{ nodes: [...], edges: [...] }`

**Controller**:
```java
@PostMapping("/protocol/update/{id}")
public void updateWorkflow(@PathVariable Long id, @RequestBody WorkflowUpdateDto dto) {
    workflowService.updateWorkflowData(id, dto);
}
```

**Service 逻辑**:
```java
// 1. 验证工作流是否存在
// 2. 序列化 nodes 和 edges 为 JSON
// 3. 更新数据库 workflow.data 字段
```

**SQL**:
```sql
UPDATE astron_console.workflow
SET data = '{"nodes":[...],"edges":[...]}',
    updated_time = NOW()
WHERE id = 184710;
```

### 2.3 超拟人合成节点添加

**节点类型**: `plugin`

**特殊之处**: 需要先从工具库选择工具

#### 2.3.1 打开工具选择弹窗

**触发**: 点击节点列表中的"超拟人合成"

**弹窗组件**: `console/frontend/src/components/workflow/modal/add-plugin/index.tsx`

**API 调用**: `GET /console-api/tool-box/list`

**返回数据**:
```json
[
  {
    "toolId": "tool@8b2262bef821000",
    "name": "超拟人合成",
    "description": "用户上传一段话，选择特色发音人，生成一段更拟人的语音",
    "version": "V1.0",
    "appId": "680ab54f",
    "operationId": "超拟人合成-46EXFdLW"
  }
]
```

#### 2.3.2 获取工具详情

**API**: `GET /console-api/tool-box/detail?toolId=tool@8b2262bef821000&version=V1.0&appId=680ab54f`

**后端逻辑**:
1. 调用 `core-link` 服务获取工具 schema
2. URL: `http://core-link:18888/api/v1/tools/versions?tool_ids=tool@8b2262bef821000&versions=V1.0&app_id=680ab54f`

**core-link 处理**:

**文件**: `core/plugin/link/service/community/tools/http/management_server.py`

```python
@router.get("/tools/versions")
async def read_tools(
    tool_ids: str,
    versions: str,
    app_id: str
):
    # 1. 查询 MySQL spark-link.tools_schema 表
    # 2. 返回 open_api_schema
```

**SQL**:
```sql
SELECT tool_id, name, version, app_id, open_api_schema
FROM `spark-link`.tools_schema
WHERE tool_id = 'tool@8b2262bef821000'
  AND version = 'V1.0'
  AND app_id = '680ab54f'
  AND is_deleted = 0;
```

**返回的 open_api_schema**:
```json
{
  "openapi": "3.1.0",
  "paths": {
    "/aitools/v1/smarttts": {
      "post": {
        "operationId": "超拟人合成-46EXFdLW",
        "requestBody": {
          "content": {
            "application/json": {
              "schema": {
                "properties": {
                  "vcn": {
                    "type": "string",
                    "default": "x5_lingfeiyi_flow",
                    "description": "特色发音人"
                  },
                  "text": {
                    "type": "string",
                    "description": "需要合成的文本"
                  },
                  "speed": {
                    "type": "integer",
                    "default": 50,
                    "description": "语速"
                  }
                },
                "required": ["vcn", "text", "speed"]
              }
            }
          }
        },
        "responses": {
          "200": {
            "content": {
              "application/json": {
                "schema": {
                  "properties": {
                    "code": { "type": "integer" },
                    "message": { "type": "string" },
                    "sid": { "type": "string" },
                    "data": {
                      "type": "object",
                      "properties": {
                        "voice_url": {
                          "type": "string",
                          "description": "音频下载url"
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  },
  "servers": [
    {
      "url": "http://core-aitools:18668"
    }
  ]
}
```

#### 2.3.3 生成 Plugin 节点

**前端解析 OpenAPI Schema**:

```typescript
// 从 schema 提取输入参数
const inputs = [
  { id: uuid(), name: 'vcn', type: 'string', default: 'x5_lingfeiyi_flow', required: true },
  { id: uuid(), name: 'text', type: 'string', required: true },
  { id: uuid(), name: 'speed', type: 'integer', default: 50, required: true }
]

// 从 schema 提取输出参数
const outputs = [
  { id: uuid(), name: 'code', type: 'integer' },
  { id: uuid(), name: 'message', type: 'string' },
  { id: uuid(), name: 'sid', type: 'string' },
  { 
    id: uuid(), 
    name: 'data', 
    type: 'object',
    properties: [
      { id: uuid(), name: 'voice_url', type: 'string' }
    ]
  }
]
```

**生成节点数据**:
```typescript
const pluginNode = {
  id: `plugin::${uuid()}`,  // 例: plugin::49b22062-a159-43f7-891a-016a1a22db74
  type: 'custom',
  position: { x, y },
  data: {
    label: '超拟人合成_1',
    nodeType: '工具',
    aliasName: '工具',
    icon: 'https://oss-beijing-m8.openstorage.cn/.../tool-icon.png',
    inputs: inputs,
    outputs: outputs,
    nodeParam: {
      appId: '680ab54f',
      pluginId: 'tool@8b2262bef821000',
      operationId: '超拟人合成-46EXFdLW',
      version: 'V1.0',
      toolDescription: '用户上传一段话，选择特色发音人，生成一段更拟人的语音'
    }
  }
}
```

---

## 3. 节点参数配置流程

### 3.1 打开节点配置面板

**触发**: 单击画布上的节点

**事件处理**:
```typescript
// FlowContainer 组件
const onNodeClick = (event, node) => {
  setSelectedNode(node);  // 设置当前选中节点
  setShowPanel(true);     // 显示右侧配置面板
}
```

**配置面板组件**: `console/frontend/src/components/workflow/panel/index.tsx`

### 3.2 大模型节点配置

**配置项**:

#### 3.2.1 输入参数配置

**组件**: `components/workflow/nodes/components/inputs/index.tsx`

**配置内容**:
```typescript
{
  name: 'input',
  type: 'string',
  value: {
    type: 'ref',  // 引用类型
    content: {
      nodeId: 'node-start::d61b0f71-87ee-475e-93ba-f1607f0ce783',
      name: 'AGENT_USER_INPUT',
      id: '0918514b-72a8-4646-8dd9-ff4a8fc26d44'
    }
  }
}
```

**UI 渲染**:
- 下拉选择框，列出所有可引用的前置节点输出
- 选择"开始节点" → "AGENT_USER_INPUT"

#### 3.2.2 Prompt 模板配置

**组件**: `components/workflow/ui/flow-template-editor.tsx`

**模板示例**:
```
# 角色
你是沉默王二，一个嘴上贫、心里明白的技术博主。

# 任务
把用户提供的原始内容改编成适合单口相声或播客节目风格的逐字稿。

# 原始内容：{{input}}
```

**模板变量**: `{{input}}` 引用输入参数

#### 3.2.3 模型选择

**组件**: `components/workflow/nodes/components/model-select/index.tsx`

**API**: `GET /console-api/llm/list?appId=680ab54f`

**返回数据**:
```json
[
  {
    "llmId": 593066024,
    "modelName": "DeepSeek",
    "domain": "deepseek-chat",
    "serviceId": "deepseek-chat",
    "url": "https://api.deepseek.com/v1/chat/completions",
    "source": "openai",
    "modelEnabled": true
  }
]
```

**配置结果**:
```typescript
nodeParam: {
  llmId: 593066024,
  domain: 'deepseek-chat',
  modelName: 'DeepSeek',
  url: 'https://api.deepseek.com/v1/chat/completions',
  template: '# 角色...',
  maxTokens: 2048,
  temperature: 1,
  // ...
}
```

#### 3.2.4 模型参数配置

**组件**: `components/workflow/nodes/components/model-params/index.tsx`

**参数**:
- `maxTokens`: 2048
- `temperature`: 1
- `topK`: 4
- `respFormat`: 0 (纯文本)

### 3.3 超拟人合成节点配置

**配置面板**: `components/workflow/nodes/plugin/index.tsx`

**通用节点组件**: `components/workflow/nodes/node-common/index.tsx`

#### 3.3.1 输入参数配置

**参数 1: vcn (发音人)**
```typescript
{
  name: 'vcn',
  type: 'string',
  value: {
    type: 'literal',  // 字面值
    content: 'x5_lingfeiyi_flow'
  },
  required: true
}
```

**UI**: 文本输入框，默认值 `x5_lingfeiyi_flow`

**参数 2: text (合成文本)**
```typescript
{
  name: 'text',
  type: 'string',
  value: {
    type: 'ref',  // 引用类型
    content: {
      nodeId: 'spark-llm::348ce48c-0148-485f-9f3f-d64f38ed5eab',
      name: 'output',
      id: '5c58073a-bfd7-404f-98e6-06544af8e821'
    }
  },
  required: true
}
```

**UI**: 下拉选择框，选择"大模型_1" → "output"

**参数 3: speed (语速)**
```typescript
{
  name: 'speed',
  type: 'integer',
  value: {
    type: 'literal',
    content: 50
  },
  required: true
}
```

**UI**: 数字输入框，默认值 50

#### 3.3.2 输出参数展示

**只读展示**:
```typescript
outputs: [
  { name: 'code', type: 'integer' },
  { name: 'message', type: 'string' },
  { name: 'sid', type: 'string' },
  { name: 'data.voice_url', type: 'string' }  // 最终音频 URL
]
```

### 3.4 节点连线

**拖拽连线**:
1. 从"开始节点"的输出点拖拽到"大模型_1"的输入点
2. 从"大模型_1"的输出点拖拽到"超拟人合成_1"的输入点
3. 从"超拟人合成_1"的输出点拖拽到"结束节点"的输入点

**生成边数据**:
```typescript
edges: [
  {
    id: 'reactflow__edge-node-start::xxx-spark-llm::xxx',
    source: 'node-start::d61b0f71-87ee-475e-93ba-f1607f0ce783',
    target: 'spark-llm::348ce48c-0148-485f-9f3f-d64f38ed5eab',
    type: 'customEdge',
    markerEnd: { type: 'arrow', color: '#275EFF' },
    data: { edgeType: 'curve' }
  },
  // ...
]
```

### 3.5 保存工作流

**触发**: 点击顶部"保存"按钮

**API**: `POST /console-api/workflow/protocol/update/184710`

**Request Body**:
```json
{
  "nodes": [
    {
      "id": "node-start::d61b0f71-87ee-475e-93ba-f1607f0ce783",
      "type": "custom",
      "position": { "x": -25, "y": 521 },
      "data": {
        "label": "开始",
        "outputs": [
          {
            "id": "0918514b-72a8-4646-8dd9-ff4a8fc26d44",
            "name": "AGENT_USER_INPUT",
            "schema": { "type": "string", "default": "用户本轮对话输入内容" }
          }
        ]
      }
    },
    {
      "id": "spark-llm::348ce48c-0148-485f-9f3f-d64f38ed5eab",
      "data": {
        "label": "大模型_1",
        "inputs": [{ /* ... */ }],
        "outputs": [{ /* ... */ }],
        "nodeParam": {
          "llmId": 593066024,
          "template": "# 角色...",
          // ...
        }
      }
    },
    {
      "id": "plugin::49b22062-a159-43f7-891a-016a1a22db74",
      "data": {
        "label": "超拟人合成_1",
        "nodeParam": {
          "pluginId": "tool@8b2262bef821000",
          "version": "V1.0"
        }
      }
    },
    {
      "id": "node-end::cda617af-551e-462e-b3b8-3bb9a041bf88",
      "data": { "label": "结束" }
    }
  ],
  "edges": [/* ... */]
}
```

**后端处理**:
```java
@PostMapping("/protocol/update/{id}")
public void updateWorkflow(@PathVariable Long id, @RequestBody String data) {
    // 序列化 JSON
    String jsonData = objectMapper.writeValueAsString(data);
    
    // 更新数据库
    workflow.setData(jsonData);
    workflow.setUpdatedTime(new Date());
    workflowMapper.updateById(workflow);
}
```

**SQL**:
```sql
UPDATE astron_console.workflow
SET data = '{"nodes":[...],"edges":[...]}',
    updated_time = '2025-11-14 15:09:56'
WHERE id = 184710;
```

---

## 4. 工作流调试执行流程

### 4.1 触发调试

**点击**: 顶部"调试"按钮

**组件**: `pages/workflow/components/btn-groups/index.tsx`

**前置检查**:
```typescript
// 1. 检查节点是否全部配置完成
const validateNodes = () => {
  for (const node of nodes) {
    // 检查必填参数是否已配置
    // 检查节点连线是否正确
  }
}

// 2. 构建工作流
const buildWorkflow = async () => {
  await API.post(`/workflow/protocol/build/${workflowId}`, {
    nodes,
    edges
  });
}
```

### 4.2 构建工作流

**API**: `POST /console-api/workflow/protocol/build/184710`

**Controller**: `WorkflowController.buildWorkflow()`

**后端逻辑**:
1. 验证工作流结构
2. 检查节点依赖关系
3. 生成执行计划（DAG 有向无环图）
4. 调用 `core-workflow` 服务注册工作流

**调用 core-workflow**:

**URL**: `POST http://core-workflow:7880/workflow/v1/protocol/build/{workflow_id}`

**core-workflow 处理**:

**文件**: `core/workflow/api/v1/protocol/workflow.py`

```python
@router.post("/protocol/build/{workflow_id}")
async def build_workflow(workflow_id: str, body: dict):
    # 1. 解析节点和边
    nodes = body['nodes']
    edges = body['edges']
    
    # 2. 构建执行 DAG
    dag = WorkflowDAG()
    for node in nodes:
        dag.add_node(node)
    for edge in edges:
        dag.add_edge(edge['source'], edge['target'])
    
    # 3. 拓扑排序，检测循环依赖
    execution_order = dag.topological_sort()
    
    # 4. 存储到 PostgreSQL workflow_python.workflow 表
    await db.save_workflow(workflow_id, dag)
    
    return {"status": "success"}
```

**PostgreSQL 存储**:
```sql
INSERT INTO workflow_python.workflow (workflow_id, dag_data, created_at)
VALUES ('184710', '{"nodes":[...],"edges":[...]}', NOW())
ON CONFLICT (workflow_id) DO UPDATE SET dag_data = EXCLUDED.dag_data;
```

### 4.3 开始调试（输入参数）

**触发**: 构建成功后，弹出输入框

**输入内容**: "沉默王二"

**API**: `POST /workflow/v1/debug/chat/completions`

**注意**: 这个接口直接走 core-workflow，不经过 console-hub

**Nginx 配置**:
```nginx
# Line 61-86: 工作流调试接口（SSE）
location /workflow/v1/chat/completions {
    proxy_pass http://core-workflow-java:7881/workflow/v1/chat/completions;
    
    # SSE 配置
    proxy_buffering off;
    proxy_cache off;
    proxy_set_header Connection '';
    proxy_http_version 1.1;
    chunked_transfer_encoding on;
    
    proxy_read_timeout 1800s;  # 30 分钟超时
}
```

**Request Body**:
```json
{
  "workflow_id": "184710",
  "user_input": "沉默王二",
  "stream": true,
  "app_id": "680ab54f",
  "uid": "b55d5545-d432-405d-9ddf-3c21276512f2"
}
```

### 4.4 工作流执行引擎

**文件**: `core/workflow/engine/workflow_executor.py`

**执行流程**:

#### 4.4.1 开始节点执行

**节点类型**: `node-start`

**Executor**: `core/workflow/engine/nodes/start_node.py`

```python
class StartNodeExecutor:
    async def execute(self, inputs):
        # 获取用户输入
        user_input = inputs['AGENT_USER_INPUT']  # "沉默王二"
        
        # 返回输出
        return {
            'AGENT_USER_INPUT': user_input
        }
```

**SSE 推送**:
```json
data: {"workflow_step":{"node":{"id":"node-start::xxx","alias_name":"开始","finish_reason":"stop","outputs":{"AGENT_USER_INPUT":"沉默王二"},"executed_time":0.001},"progress":0.0}}
```

#### 4.4.2 大模型节点执行

**节点类型**: `spark-llm`

**Executor**: `core/workflow/engine/nodes/llm_node.py`

```python
class LLMNodeExecutor:
    async def execute(self, node_config, inputs):
        # 1. 获取输入
        input_text = inputs['input']  # "沉默王二"
        
        # 2. 渲染 Prompt 模板
        template = node_config['template']
        prompt = template.replace('{{input}}', input_text)
        # 结果: "# 角色\n你是沉默王二...\n# 原始内容：沉默王二"
        
        # 3. 调用 DeepSeek API
        response = await self.call_llm(
            url=node_config['url'],  # https://api.deepseek.com/v1/chat/completions
            model=node_config['domain'],  # deepseek-chat
            messages=[{"role": "user", "content": prompt}],
            max_tokens=node_config['maxTokens'],  # 2048
            temperature=node_config['temperature']  # 1
        )
        
        # 4. 返回结果
        return {
            'output': response['choices'][0]['message']['content']
        }
```

**DeepSeek API 调用**:

**Request**:
```json
{
  "model": "deepseek-chat",
  "messages": [
    {
      "role": "user",
      "content": "# 角色\n你是沉默王二...\n# 原始内容：沉默王二"
    }
  ],
  "max_tokens": 2048,
  "temperature": 1,
  "stream": false
}
```

**Response**:
```json
{
  "choices": [
    {
      "message": {
        "role": "assistant",
        "content": "欢迎收听王二电台，我是那个嘴上停不下来、心里却门儿清的技术博主——沉默王二..."
      }
    }
  ],
  "usage": {
    "prompt_tokens": 234,
    "completion_tokens": 357,
    "total_tokens": 591
  }
}
```

**SSE 推送**:
```json
data: {"workflow_step":{"node":{"id":"spark-llm::xxx","alias_name":"大模型_1","finish_reason":"stop","outputs":{"output":"欢迎收听王二电台..."},"executed_time":16.053,"usage":{"total_tokens":591}},"progress":0.25}}
```

#### 4.4.3 超拟人合成节点执行

**节点类型**: `plugin`

**Executor**: `core/workflow/engine/nodes/plugin_node.py`

```python
class PluginNodeExecutor:
    async def execute(self, node_config, inputs):
        # 1. 获取输入参数
        vcn = inputs['vcn']  # "x5_lingfeiyi_flow"
        text = inputs['text']  # "欢迎收听王二电台..."（来自大模型输出）
        speed = inputs['speed']  # 50
        
        # 2. 调用 core-link 服务
        result = await self.call_plugin(
            app_id=node_config['appId'],  # "680ab54f"
            tool_id=node_config['pluginId'],  # "tool@8b2262bef821000"
            operation_id=node_config['operationId'],  # "超拟人合成-46EXFdLW"
            version=node_config['version'],  # "V1.0"
            params={
                'vcn': vcn,
                'text': text,
                'speed': speed
            }
        )
        
        return result
```

**调用 core-link**:

**URL**: `POST http://core-link:18888/api/v1/tools/http_run`

**Request Body**:
```json
{
  "header": {
    "app_id": "680ab54f"
  },
  "parameter": {
    "tool_id": "tool@8b2262bef821000",
    "operation_id": "超拟人合成-46EXFdLW",
    "version": "V1.0"
  },
  "payload": {
    "message": {
      "body": "base64({"vcn":"x5_lingfeiyi_flow","text":"欢迎收听...","speed":50})"
    }
  }
}
```

**core-link 处理**:

**文件**: `core/plugin/link/service/community/tools/http/execution_server.py`

```python
@router.post("/tools/http_run")
async def http_run(request: ToolExecutionRequest):
    # 1. 从数据库获取工具配置
    tool = await db.query(
        "SELECT open_api_schema FROM tools_schema WHERE tool_id=? AND version=?",
        (request.parameter.tool_id, request.parameter.version)
    )
    
    # 2. 解析 OpenAPI Schema
    schema = json.loads(tool['open_api_schema'])
    server_url = schema['servers'][0]['url']  # http://core-aitools:18668
    path = list(schema['paths'].keys())[0]  # /aitools/v1/smarttts
    
    # 3. 解码参数
    body = base64.b64decode(request.payload.message.body)
    params = json.loads(body)  # {"vcn":"x5_lingfeiyi_flow","text":"...","speed":50}
    
    # 4. 调用真实的 AI 工具服务
    url = f"{server_url}{path}"  # http://core-aitools:18668/aitools/v1/smarttts
    response = await aiohttp.post(url, json=params)
    
    return response.json()
```

**调用 core-aitools**:

**URL**: `POST http://core-aitools:18668/aitools/v1/smarttts`

**Request**:
```json
{
  "vcn": "x5_lingfeiyi_flow",
  "text": "欢迎收听王二电台，我是那个嘴上停不下来、心里却门儿清的技术博主——沉默王二...",
  "speed": 50
}
```

**core-aitools 处理**:

**文件**: `core/plugin/aitools/api/v1/smarttts.py`

```python
@router.post("/aitools/v1/smarttts")
async def smart_tts(request: SmartTTSRequest):
    # 1. 调用讯飞 Spark 语音合成 SDK
    from iflytek_spark_sdk import VoiceSynthesizer
    
    synthesizer = VoiceSynthesizer(
        app_id=os.getenv('PLATFORM_APP_ID'),  # f740451b
        api_key=os.getenv('PLATFORM_API_KEY'),
        api_secret=os.getenv('PLATFORM_API_SECRET')
    )
    
    # 2. 合成语音
    audio_data = await synthesizer.synthesize(
        text=request.text,
        vcn=request.vcn,
        speed=request.speed
    )
    
    # 3. 上传到 MinIO
    from minio_client import upload_file
    
    audio_url = await upload_file(
        bucket='workflow',
        file_data=audio_data,
        content_type='audio/mpeg'
    )
    
    # 4. 返回结果
    return {
        "code": 0,
        "message": "Success",
        "sid": generate_sid(),
        "data": {
            "voice_url": audio_url  # http://localhost:18999/workflow/xxx.mp3
        }
    }
```

**讯飞 SDK 调用流程**:
1. WebSocket 连接到 `wss://spark-api.xf-yun.com/v1/tts`
2. 发送鉴权信息（app_id, api_key, api_secret）
3. 发送文本和参数（vcn, speed）
4. 接收音频流（base64 编码的 PCM/MP3）
5. 解码并拼接完整音频

**MinIO 上传**:
```python
minio_client.put_object(
    bucket_name='workflow',
    object_name=f'{uuid.uuid4()}.mp3',
    data=audio_data,
    length=len(audio_data),
    content_type='audio/mpeg'
)
```

**core-aitools 返回**:
```json
{
  "code": 0,
  "message": "Success",
  "sid": "tts000c0015@dx19a8127adbec000782",
  "data": {
    "voice_url": "http://localhost:18999/workflow/abc123.mp3"
  }
}
```

**core-link 返回给 workflow**:
```json
{
  "code": 0,
  "message": "Success",
  "sid": "tts000c0015@dx19a8127adbec000782",
  "data": {
    "voice_url": "http://localhost:18999/workflow/abc123.mp3"
  }
}
```

**SSE 推送**:
```json
data: {"workflow_step":{"node":{"id":"plugin::xxx","alias_name":"超拟人合成_1","finish_reason":"stop","outputs":{"code":0,"message":"Success","sid":"tts...","data":{"voice_url":"http://localhost:18999/workflow/abc123.mp3"}},"executed_time":3.5},"progress":0.5}}
```

#### 4.4.4 结束节点执行

**节点类型**: `node-end`

**Executor**: `core/workflow/engine/nodes/end_node.py`

```python
class EndNodeExecutor:
    async def execute(self, inputs):
        # 获取输入（从超拟人合成节点）
        output = inputs['output']  # {"voice_url": "http://..."}
        
        # 渲染输出模板
        template = '<audio preload="none" controls><source src="{{output}}" type="audio/mpeg"></audio>'
        html = template.replace('{{output}}', output['voice_url'])
        
        return {
            'output': html
        }
```

**SSE 推送（完成）**:
```json
data: {"code":0,"message":"Success","workflow_step":{"progress":1.0},"choices":[{"delta":{"role":"assistant","content":"<audio...>"},"finish_reason":"stop"}],"usage":{"total_tokens":591}}

data: [DONE]
```

### 4.5 前端接收 SSE 流

**组件**: `components/workflow/hooks/use-workflow-debug.tsx`

```typescript
const eventSource = new EventSource('/workflow/v1/debug/chat/completions');

eventSource.onmessage = (event) => {
  const data = JSON.parse(event.data);
  
  if (data.workflow_step) {
    const { node, progress } = data.workflow_step;
    
    // 更新节点状态
    setNode(node.id, (old) => ({
      ...old,
      data: {
        ...old.data,
        status: node.finish_reason === 'stop' ? 'success' : 'running',
        outputs: node.outputs
      }
    }));
    
    // 更新进度条
    setProgress(progress * 100);
  }
  
  if (event.data === '[DONE]') {
    eventSource.close();
    // 显示最终结果
    setFinalResult(data.choices[0].delta.content);
  }
};
```

### 4.6 展示结果

**右侧聊天结果面板**:
- 显示音频播放器（HTML audio 标签）
- 音频 URL: `http://localhost:18999/workflow/abc123.mp3`

**点击播放**:
1. 浏览器请求 `http://localhost:18999/workflow/abc123.mp3`
2. Nginx 代理到 MinIO（如果配置了反向代理）
3. 或直接从 MinIO 服务获取文件

---

## 5. 完整数据流图

```
┌─────────────────────────────────────────────────────────────────────┐
│                        工作流创建与执行完整流程                           │
└─────────────────────────────────────────────────────────────────────┘

1. 页面访问
   User Browser
       │
       │ http://localhost/work_flow/184710/arrange?botId=40
       │
       ▼
   Nginx :80
       │
       │ location / { proxy_pass http://console-frontend:1881; }
       │
       ▼
   React App (console-frontend:1881)
       │
       │ Router: /work_flow/:id/arrange → <WorkFlow />
       │
       ▼
   useEffect(() => initFlowData(id))
       │
       │ GET /console-api/workflow/detail/184710
       │
       ▼
   Nginx :80
       │
       │ location /console-api/ { proxy_pass http://console-hub:8080/; }
       │
       ▼
   Spring Boot (console-hub:8080)
       │
       │ @GetMapping("/workflow/detail/{id}")
       │
       ▼
   MySQL (astron_console.workflow)
       │
       │ SELECT * FROM workflow WHERE id = 184710
       │
       └──────────────────────────────────────────────────────┐
                                                               │
   ┌───────────────────────────────────────────────────────────┘
   │
   │ Return: { id, name, data: '{"nodes":[...],"edges":[...]}' }
   │
   ▼
   React: 解析 JSON → 渲染画布

2. 添加节点
   User: 拖拽"大模型"节点到画布
       │
       ▼
   FlowContainer.onDrop()
       │
       │ 生成节点数据: { id: "spark-llm::uuid", data: {...} }
       │
       ▼
   zustand: setNodes([...nodes, newNode])
       │
       │ POST /console-api/workflow/protocol/update/184710
       │ Body: { nodes: [...], edges: [...] }
       │
       ▼
   console-hub:8080
       │
       │ UPDATE workflow SET data = '...' WHERE id = 184710
       │
       ▼
   MySQL

   User: 点击"添加工具" → 选择"超拟人合成"
       │
       │ GET /console-api/tool-box/detail?toolId=tool@8b2262bef821000
       │
       ▼
   console-hub:8080
       │
       │ GET http://core-link:18888/api/v1/tools/versions
       │
       ▼
   core-link (Python FastAPI)
       │
       │ SELECT open_api_schema FROM spark-link.tools_schema
       │   WHERE tool_id='tool@8b2262bef821000' AND version='V1.0'
       │
       ▼
   MySQL (spark-link.tools_schema)
       │
       │ Return: { open_api_schema: '{"paths":{...}}' }
       │
       └────────────────────────────────────────────┐
                                                     │
   ┌─────────────────────────────────────────────────┘
   │
   │ 解析 OpenAPI Schema → 提取 inputs/outputs
   │
   ▼
   React: 生成 Plugin 节点并添加到画布

3. 配置节点
   User: 点击"大模型_1"节点
       │
       ▼
   右侧配置面板打开
       │
       ├─ 输入参数: 选择"开始节点" → "AGENT_USER_INPUT"
       ├─ Prompt 模板: 输入 "# 角色\n..."
       ├─ 选择模型: GET /console-api/llm/list → 选择 DeepSeek
       └─ 模型参数: maxTokens=2048, temperature=1
       
   User: 点击"超拟人合成_1"节点
       │
       ▼
   右侧配置面板打开
       │
       ├─ vcn: "x5_lingfeiyi_flow" (字面值)
       ├─ text: 选择"大模型_1" → "output" (引用)
       └─ speed: 50 (字面值)
       
   User: 连线
       │
       ▼
   开始 → 大模型_1 → 超拟人合成_1 → 结束
       │
       │ POST /console-api/workflow/protocol/update/184710
       │
       ▼
   保存到数据库

4. 调试执行
   User: 点击"调试"按钮
       │
       │ 1. POST /console-api/workflow/protocol/build/184710
       │    → console-hub → POST http://core-workflow:7880/workflow/v1/protocol/build/184710
       │    → core-workflow 构建 DAG → 保存到 PostgreSQL
       │
       │ 2. 弹出输入框，输入"沉默王二"
       │
       │ 3. POST /workflow/v1/debug/chat/completions (SSE)
       │
       ▼
   Nginx :80
       │
       │ location /workflow/v1/chat/completions {
       │     proxy_pass http://core-workflow-java:7881;  # 或 core-workflow:7880
       │ }
       │
       ▼
   core-workflow (Python/Java)
       │
       │ 4.1 执行"开始"节点
       │     └─ 输出: { AGENT_USER_INPUT: "沉默王二" }
       │     └─ SSE: data: {"workflow_step":{"node":{...},"progress":0.0}}
       │
       │ 4.2 执行"大模型_1"节点
       │     ├─ 获取输入: inputs['input'] = "沉默王二"
       │     ├─ 渲染 Prompt: "# 角色...\n# 原始内容：沉默王二"
       │     ├─ 调用 DeepSeek API
       │     │   POST https://api.deepseek.com/v1/chat/completions
       │     │   Body: { model: "deepseek-chat", messages: [...] }
       │     │   Response: { choices: [{ message: { content: "欢迎收听王二电台..." } }] }
       │     ├─ 输出: { output: "欢迎收听王二电台..." }
       │     └─ SSE: data: {"workflow_step":{"node":{...},"progress":0.25}}
       │
       │ 4.3 执行"超拟人合成_1"节点
       │     ├─ 获取输入:
       │     │   vcn = "x5_lingfeiyi_flow"
       │     │   text = "欢迎收听王二电台..."（来自大模型输出）
       │     │   speed = 50
       │     │
       │     ├─ 调用 core-link
       │     │   POST http://core-link:18888/api/v1/tools/http_run
       │     │   Body: { tool_id: "tool@8b2262bef821000", params: {...} }
       │     │   │
       │     │   └─ core-link 查询数据库获取 open_api_schema
       │     │       │ SELECT open_api_schema FROM tools_schema ...
       │     │       │ 解析得到: url = "http://core-aitools:18668/aitools/v1/smarttts"
       │     │       │
       │     │       └─ 调用 core-aitools
       │     │           POST http://core-aitools:18668/aitools/v1/smarttts
       │     │           Body: { vcn: "...", text: "...", speed: 50 }
       │     │           │
       │     │           └─ core-aitools 调用讯飞 SDK
       │     │               WebSocket: wss://spark-api.xf-yun.com/v1/tts
       │     │               Send: { app_id, api_key, text, vcn, speed }
       │     │               Receive: 音频流（base64）
       │     │               │
       │     │               └─ 上传到 MinIO
       │     │                   PUT /workflow/abc123.mp3
       │     │                   Return: "http://localhost:18999/workflow/abc123.mp3"
       │     │
       │     ├─ 输出: { code: 0, data: { voice_url: "http://..." } }
       │     └─ SSE: data: {"workflow_step":{"node":{...},"progress":0.5}}
       │
       │ 4.4 执行"结束"节点
       │     ├─ 获取输入: output = "http://localhost:18999/workflow/abc123.mp3"
       │     ├─ 渲染模板: <audio src="http://..."></audio>
       │     └─ SSE: data: {"workflow_step":{...},"progress":1.0}}
       │         data: [DONE]
       │
       ▼
   React: EventSource 接收 SSE 流
       │
       ├─ 更新节点状态（running → success）
       ├─ 更新进度条（0% → 100%）
       └─ 显示最终结果（音频播放器）

5. 播放音频
   User: 点击播放按钮
       │
       │ GET http://localhost:18999/workflow/abc123.mp3
       │
       ▼
   MinIO :18999
       │
       └─ 返回音频文件流
```

---

## 6. 关键代码位置索引

### 6.1 前端代码

#### 路由和页面
- **路由配置**: `console/frontend/src/router/index.tsx:237-243`
- **工作流主页面**: `console/frontend/src/pages/workflow/index.tsx`
- **画布容器**: `console/frontend/src/pages/workflow/components/flow-container/index.tsx`
- **节点列表**: `console/frontend/src/pages/workflow/components/node-list/index.tsx`
- **头部工具栏**: `console/frontend/src/pages/workflow/components/flow-header/index.tsx`
- **按钮组**: `console/frontend/src/pages/workflow/components/btn-groups/index.tsx`

#### 状态管理
- **工作流状态**: `console/frontend/src/components/workflow/store/use-flows-manager.ts`
- **画布状态**: `console/frontend/src/components/workflow/store/use-flow-store.ts`

#### 节点组件
- **大模型节点**: `console/frontend/src/components/workflow/nodes/llm/index.tsx`
- **Plugin 节点**: `console/frontend/src/components/workflow/nodes/plugin/index.tsx`
- **通用节点**: `console/frontend/src/components/workflow/nodes/node-common/index.tsx`
- **开始节点**: `console/frontend/src/components/workflow/nodes/start/index.tsx`
- **结束节点**: `console/frontend/src/components/workflow/nodes/end/index.tsx`

#### 配置面板
- **配置面板**: `console/frontend/src/components/workflow/panel/index.tsx`
- **输入参数配置**: `console/frontend/src/components/workflow/nodes/components/inputs/index.tsx`
- **模型选择**: `console/frontend/src/components/workflow/nodes/components/model-select/index.tsx`
- **模型参数**: `console/frontend/src/components/workflow/nodes/components/model-params/index.tsx`
- **节点调试器**: `console/frontend/src/components/workflow/nodes/components/node-debugger/index.tsx`

#### 弹窗组件
- **工具选择**: `console/frontend/src/components/workflow/modal/add-plugin/index.tsx`
- **知识库选择**: `console/frontend/src/components/workflow/modal/add-knowledge/index.tsx`
- **RPA 选择**: `console/frontend/src/components/workflow/modal/add-rpa/index.tsx`

### 6.2 后端代码（Java）

#### Controller
- **工作流 Controller**: `console/backend/toolkit/src/main/java/com/iflytek/astron/console/toolkit/controller/workflow/WorkflowController.java`
  - `GET /workflow/detail/{id}` - 获取工作流详情
  - `POST /workflow/protocol/update/{id}` - 更新工作流
  - `POST /workflow/protocol/build/{id}` - 构建工作流
  - `POST /workflow/node/debug/{nodeId}` - 调试单个节点

#### Service
- **工作流 Service**: `console/backend/toolkit/src/main/java/com/iflytek/astron/console/toolkit/service/workflow/WorkflowService.java`
- **对话 Service**: `console/backend/toolkit/src/main/java/com/iflytek/astron/console/toolkit/service/workflow/TalkAgentService.java`

#### Mapper
- **工作流 Mapper**: `console/backend/toolkit/src/main/java/com/iflytek/astron/console/toolkit/mapper/workflow/WorkflowMapper.java`

#### Entity
- **工作流实体**: `console/backend/commons/src/main/java/com/iflytek/astron/console/commons/entity/workflow/Workflow.java`

### 6.3 后端代码（Python - core-workflow）

#### API 路由
- **工作流协议**: `core/workflow/api/v1/protocol/workflow.py`
  - `POST /workflow/v1/protocol/build/{workflow_id}` - 构建工作流
  - `POST /workflow/v1/debug/chat/completions` - 调试执行（SSE）

#### 执行引擎
- **工作流执行器**: `core/workflow/engine/workflow_executor.py`
- **DAG 构建器**: `core/workflow/engine/dag_builder.py`

#### 节点执行器
- **开始节点**: `core/workflow/engine/nodes/start_node.py`
- **结束节点**: `core/workflow/engine/nodes/end_node.py`
- **LLM 节点**: `core/workflow/engine/nodes/llm_node.py`
- **Plugin 节点**: `core/workflow/engine/nodes/plugin_node.py`

### 6.4 后端代码（Python - core-link）

#### API 路由
- **工具管理**: `core/plugin/link/api/v1/community/tools/http/management_server.py`
  - `GET /api/v1/tools/versions` - 获取工具详情
- **工具执行**: `core/plugin/link/api/v1/community/tools/http/execution_server.py`
  - `POST /api/v1/tools/http_run` - 执行工具

#### 数据访问
- **工具 CRUD**: `core/plugin/link/infra/tool_crud/process.py`
- **工具执行器**: `core/plugin/link/infra/tool_exector/process.py`

### 6.5 后端代码（Python - core-aitools）

#### API 路由
- **超拟人合成**: `core/plugin/aitools/api/v1/smarttts.py`
  - `POST /aitools/v1/smarttts` - 语音合成
- **文生图**: `core/plugin/aitools/api/v1/image_generate.py`
- **图片理解**: `core/plugin/aitools/api/v1/image_understanding.py`
- **OCR**: `core/plugin/aitools/api/v1/ocr.py`

#### SDK 集成
- **讯飞 SDK**: `core/plugin/aitools/sdk/iflytek_spark/` （需确认实际路径）

### 6.6 配置文件

#### Nginx
- **主配置**: `docker/astronAgent/nginx/nginx.conf`
  - Line 131-142: 前端代理
  - Line 117-128: 后端 API 代理
  - Line 61-86: 工作流 SSE 代理

#### Docker Compose
- **服务编排**: `docker/astronAgent/docker-compose-with-auth.yaml`

#### 数据库
- **MySQL 初始化**: `docker/astronAgent/mysql/schema.sql`
- **工具表初始化**: `docker/astronAgent/mysql/link.sql`

---

## 7. Core 模块详解

本工作流涉及的 `core/` 目录下的 Python 模块详细说明。

### 7.1 涉及的 Core 模块概览

```
core/
├── workflow/          ✅ 核心：工作流引擎（FastAPI）
├── plugin/
│   ├── link/         ✅ 核心：工具连接器（FastAPI）
│   ├── aitools/      ✅ 核心：AI工具服务（FastAPI）
│   └── rpa/          ❌ 未使用
├── common/           ✅ 辅助：公共工具库
├── agent/            ❌ 未使用（本流程不涉及 Agent）
├── knowledge/        ❌ 未使用（本流程不涉及知识库）
├── memory/           ❌ 未使用
└── tenant/           ❌ 未使用（Go 服务，多租户管理）
```

### 7.2 核心模块 1: `core/workflow/` - 工作流引擎

**职责**: 工作流的构建、执行、调度

**技术栈**: 
- FastAPI 0.111 (异步 Web 框架)
- PostgreSQL (工作流数据存储)
- Redis (缓存和任务队列)
- Pydantic 2.9 (数据验证)

**端口**: `7880` (Python 版本) / `7881` (Java 版本)

**主要功能**:

#### 7.2.1 工作流构建
**文件**: `core/workflow/api/v1/protocol/workflow.py`

```python
@router.post("/protocol/build/{workflow_id}")
async def build_workflow(workflow_id: str, body: dict):
    """
    构建工作流 DAG（有向无环图）
    
    1. 解析节点和边
    2. 验证节点依赖关系
    3. 拓扑排序（检测循环依赖）
    4. 存储到 PostgreSQL
    """
    nodes = body['nodes']
    edges = body['edges']
    
    dag = WorkflowDAG()
    dag.build(nodes, edges)
    
    await db.save_workflow(workflow_id, dag)
    return {"status": "success"}
```

**数据库**: `workflow_python.workflow` 表

#### 7.2.2 工作流执行
**文件**: `core/workflow/engine/workflow_executor.py`

```python
class WorkflowExecutor:
    """工作流执行器"""
    
    async def execute(self, workflow_id: str, user_input: dict):
        """
        执行工作流
        
        流程:
        1. 加载 DAG
        2. 按拓扑顺序执行节点
        3. 传递节点间的数据
        4. SSE 实时推送进度
        """
        dag = await self.load_dag(workflow_id)
        
        for node in dag.topological_order():
            # 执行节点
            result = await self.execute_node(node)
            
            # SSE 推送进度
            await self.push_progress(node, result)
```

**SSE 推送**: 通过 `/workflow/v1/debug/chat/completions` 接口

#### 7.2.3 节点执行器
**目录**: `core/workflow/engine/nodes/`

| 文件 | 节点类型 | 说明 |
|------|---------|------|
| `start_node.py` | `node-start` | 开始节点，接收用户输入 |
| `end_node.py` | `node-end` | 结束节点，输出最终结果 |
| `llm_node.py` | `spark-llm` | 大模型节点，调用 LLM API |
| `plugin_node.py` | `plugin` | 插件节点，调用工具服务 |
| `knowledge_node.py` | `knowledge` | 知识库节点（本流程未用） |
| `if_else_node.py` | `if-else` | 条件分支节点（本流程未用） |
| `iterator_node.py` | `iterator` | 循环节点（本流程未用） |

**大模型节点执行示例**:
```python
# core/workflow/engine/nodes/llm_node.py
class LLMNodeExecutor:
    async def execute(self, node_config, inputs):
        # 1. 渲染 Prompt 模板
        prompt = self.render_template(
            node_config['template'], 
            inputs
        )
        
        # 2. 调用 LLM API（DeepSeek）
        response = await self.call_llm(
            url=node_config['url'],
            model=node_config['domain'],
            messages=[{"role": "user", "content": prompt}],
            max_tokens=node_config['maxTokens'],
            temperature=node_config['temperature']
        )
        
        # 3. 返回结果
        return {"output": response['choices'][0]['message']['content']}
```

**Plugin 节点执行示例**:
```python
# core/workflow/engine/nodes/plugin_node.py
class PluginNodeExecutor:
    async def execute(self, node_config, inputs):
        # 调用 core-link 服务
        result = await self.http_client.post(
            "http://core-link:18888/api/v1/tools/http_run",
            json={
                "header": {"app_id": node_config['appId']},
                "parameter": {
                    "tool_id": node_config['pluginId'],
                    "operation_id": node_config['operationId'],
                    "version": node_config['version']
                },
                "payload": {"message": {"body": base64_encode(inputs)}}
            }
        )
        return result.json()
```

### 7.3 核心模块 2: `core/plugin/link/` - 工具连接器

**职责**: 连接外部工具服务，管理工具元数据

**技术栈**:
- FastAPI 0.111
- MySQL (工具元数据存储)
- SQLAlchemy (ORM)
- aiohttp (异步 HTTP 客户端)

**端口**: `18888`

**数据库**: `spark-link` (MySQL)

**主要功能**:

#### 7.3.1 工具元数据管理
**文件**: `core/plugin/link/api/v1/community/tools/http/management_server.py`

```python
@router.get("/tools/versions")
async def read_tools(
    tool_ids: str,      # "tool@8b2262bef821000"
    versions: str,      # "V1.0"
    app_id: str        # "680ab54f"
):
    """
    获取工具详情（OpenAPI Schema）
    
    查询 MySQL spark-link.tools_schema 表
    返回 open_api_schema 字段
    """
    tool_list = await tool_crud.get_tools(
        tool_ids=tool_ids.split(','),
        versions=versions.split(','),
        app_id=app_id
    )
    return tool_list
```

**SQL 查询**:
```sql
SELECT tool_id, name, version, app_id, open_api_schema
FROM `spark-link`.tools_schema
WHERE tool_id IN ('tool@8b2262bef821000')
  AND version IN ('V1.0')
  AND app_id = '680ab54f'
  AND is_deleted = 0;
```

**数据表结构**:
```sql
CREATE TABLE tools_schema (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    app_id VARCHAR(32),
    tool_id VARCHAR(32),
    name VARCHAR(128),
    description VARCHAR(512),
    open_api_schema TEXT,              -- OpenAPI 3.1.0 Schema (JSON)
    version VARCHAR(32) DEFAULT 'V1.0',
    is_deleted BIGINT DEFAULT 0,
    create_at DATETIME(6),
    update_at DATETIME(6),
    UNIQUE KEY unique_tool_version (tool_id, version, is_deleted)
);
```

#### 7.3.2 工具执行代理
**文件**: `core/plugin/link/api/v1/community/tools/http/execution_server.py`

```python
@router.post("/tools/http_run")
async def http_run(request: ToolExecutionRequest):
    """
    执行工具调用
    
    流程:
    1. 从数据库获取工具的 OpenAPI Schema
    2. 解析 Schema 获取目标 URL 和参数定义
    3. 转发请求到真实的工具服务（如 core-aitools）
    4. 返回执行结果
    """
    # 1. 获取工具配置
    tool = await tool_crud.get_tool(
        tool_id=request.parameter.tool_id,
        version=request.parameter.version,
        app_id=request.header.app_id
    )
    
    # 2. 解析 OpenAPI Schema
    schema = json.loads(tool.open_api_schema)
    server_url = schema['servers'][0]['url']  # "http://core-aitools:18668"
    path = list(schema['paths'].keys())[0]    # "/aitools/v1/smarttts"
    
    # 3. 解码请求参数
    body = base64.b64decode(request.payload.message.body)
    params = json.loads(body)
    
    # 4. 转发到真实服务
    url = f"{server_url}{path}"
    response = await aiohttp_client.post(url, json=params)
    
    return response.json()
```

**工具执行链路**:
```
workflow → core-link → core-aitools
                ↓ (查询 MySQL)
         tools_schema 表
```

#### 7.3.3 MCP (Model Context Protocol) 支持
**文件**: `core/plugin/link/service/community/tools/mcp/mcp_server.py`

**说明**: 支持连接 MCP Server（如 filesystem、git 等），本流程未使用。

### 7.4 核心模块 3: `core/plugin/aitools/` - AI 工具服务

**职责**: 提供 AI 能力工具（语音合成、文生图、OCR 等）

**技术栈**:
- FastAPI 0.111
- 讯飞 Spark SDK 2.1.5 (语音合成)
- MinIO (文件存储)
- aiohttp (HTTP 客户端)

**端口**: `18668`

**主要功能**:

#### 7.4.1 超拟人语音合成
**文件**: `core/plugin/aitools/api/route.py` → `service/speech_synthesis/voice_main.py`

```python
@router.post("/aitools/v1/smarttts")
async def smart_tts(request: SmartTTSRequest):
    """
    超拟人语音合成
    
    参数:
    - vcn: 发音人（x5_lingfeiyi_flow）
    - text: 需要合成的文本
    - speed: 语速（0-100，默认50）
    
    返回:
    - voice_url: 音频下载地址
    """
    # 1. 调用讯飞 Spark 语音合成 SDK
    synthesizer = VoiceSynthesizer(
        app_id=os.getenv('PLATFORM_APP_ID'),
        api_key=os.getenv('PLATFORM_API_KEY'),
        api_secret=os.getenv('PLATFORM_API_SECRET')
    )
    
    audio_data = await synthesizer.synthesize(
        text=request.text,
        vcn=request.vcn,
        speed=request.speed
    )
    
    # 2. 上传到 MinIO
    audio_url = await minio_client.upload_file(
        bucket='workflow',
        file_data=audio_data,
        content_type='audio/mpeg'
    )
    
    # 3. 返回结果
    return {
        "code": 0,
        "message": "Success",
        "sid": generate_sid(),
        "data": {"voice_url": audio_url}
    }
```

**讯飞 SDK 调用流程**:
```
aitools → WebSocket(wss://spark-api.xf-yun.com/v1/tts)
    ↓ Send: { app_id, api_key, text, vcn, speed }
    ↓ Receive: 音频流（base64 编码）
    ↓ 解码拼接
    └─ 上传到 MinIO
```

**环境变量**:
```bash
PLATFORM_APP_ID=f740451b          # 讯飞平台 APP ID
PLATFORM_API_KEY=ebaf9daded8d...   # 讯飞 API Key
PLATFORM_API_SECRET=ZGE0YjQ3...    # 讯飞 API Secret
```

#### 7.4.2 其他 AI 工具（本流程未用）
- **文生图**: `/aitools/v1/image_generate`
- **图片理解**: `/aitools/v1/image_understanding`
- **OCR**: `/aitools/v1/ocr`

### 7.5 辅助模块: `core/common/` - 公共工具库

**职责**: 提供跨服务的公共功能

**不是独立服务，是被其他模块引用的库**

**主要功能**:

#### 7.5.1 OpenTelemetry 追踪
**目录**: `core/common/otlp/trace/`

```python
# 分布式追踪，记录请求链路
from common.otlp.trace import start_span

with start_span("llm_call") as span:
    span.set_attribute("model", "deepseek-chat")
    result = await call_llm()
```

**用途**: 监控工作流执行链路，性能分析

#### 7.5.2 日志管理
**目录**: `core/common/logger/`

```python
from loguru import logger

logger.info("Workflow started", workflow_id=workflow_id)
logger.error("Node execution failed", node_id=node_id)
```

**配置**: 统一的日志格式、日志轮转

#### 7.5.3 配置管理
**目录**: `core/common/config/`

```python
from common.config import get_settings

settings = get_settings()
redis_url = settings.REDIS_URL
postgres_url = settings.POSTGRES_URL
```

### 7.6 未使用的模块

| 模块 | 说明 | 为何未使用 |
|------|------|-----------|
| `core/agent/` | Agent 编排服务 | 本流程是纯 Workflow，不涉及 Agent |
| `core/knowledge/` | 知识库服务（RAG） | 本流程不涉及知识库检索 |
| `core/memory/` | 记忆管理 | 本流程不需要上下文记忆 |
| `core/tenant/` | 多租户管理（Go） | 单机部署不涉及租户隔离 |
| `core/plugin/rpa/` | RPA 自动化 | 本流程不涉及 RPA 节点 |

### 7.7 Core 模块依赖关系图

```
┌─────────────────────────────────────────────────────────────┐
│                     Core 模块依赖关系                          │
└─────────────────────────────────────────────────────────────┘

console-hub (Java)
    │
    ├─ HTTP ──→ core/workflow (FastAPI :7880)
    │              │
    │              ├─ 依赖 ──→ core/common (公共库)
    │              │              ├─ otlp (追踪)
    │              │              ├─ logger (日志)
    │              │              └─ config (配置)
    │              │
    │              ├─ HTTP ──→ core/plugin/link (FastAPI :18888)
    │              │              │
    │              │              ├─ 依赖 ──→ core/common
    │              │              │
    │              │              ├─ MySQL ──→ spark-link.tools_schema
    │              │              │
    │              │              └─ HTTP ──→ core/plugin/aitools (FastAPI :18668)
    │              │                            │
    │              │                            ├─ 依赖 ──→ core/common
    │              │                            │
    │              │                            ├─ WebSocket ──→ 讯飞 Spark API
    │              │                            │
    │              │                            └─ HTTP ──→ MinIO :18999
    │              │
    │              └─ PostgreSQL ──→ workflow_python.workflow
    │
    └─ MySQL ──→ astron_console.workflow

未使用模块:
  ❌ core/agent
  ❌ core/knowledge
  ❌ core/memory
  ❌ core/tenant
  ❌ core/plugin/rpa
```

### 7.8 Core 模块通信协议

| 调用方 | 被调用方 | 协议 | 端口 | 用途 |
|-------|---------|------|------|------|
| console-hub | core-workflow | HTTP | 7880 | 构建/执行工作流 |
| console-hub | core-workflow | SSE | 7880 | 实时推送执行进度 |
| core-workflow | core-link | HTTP | 18888 | 获取工具信息、执行工具 |
| core-link | core-aitools | HTTP | 18668 | 执行 AI 工具 |
| core-aitools | 讯飞 Spark | WebSocket | 443 | 语音合成 |
| core-aitools | MinIO | HTTP | 18999 | 上传音频文件 |
| core-workflow | PostgreSQL | TCP | 5432 | 存储工作流 DAG |
| core-link | MySQL | TCP | 3306 | 查询工具元数据 |

---

## 📚 参考文档

- [本地构建部署指南](../docker/astronAgent/LOCAL-BUILD-GUIDE.md)
- [Docker 日志查看指南](../docker/astronAgent/DOCKER-LOGS-GUIDE.md)
- [项目架构说明](../AGENTS.md)

---

**最后更新**: 2025-11-14  
**维护者**: 沉默王二
