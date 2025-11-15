# Workflow 请求流程完整分析

## 📍 路由：`http://localhost/work_flow/184736/arrange`

### 1️⃣ **前端路由处理**

#### 路由定义
```typescript
// console/frontend/src/router/index.tsx
{
  path: '/work_flow/:id/arrange',
  element: <WorkFlow />
}
```

#### 组件加载流程
```typescript
// console/frontend/src/pages/workflow/index.tsx

const Index: React.ReactElement = () => {
  const { id } = useParams();  // 获取 URL 中的 id (184736)
  const initFlowData = useFlowsManager(state => state.initFlowData);
  
  // 页面加载时初始化工作流数据
  useEffect(() => {
    id && initFlowData(id);  // 调用 initFlowData("184736")
  }, [id, location]);
  
  // ...
}
```

---

## 2️⃣ **初始化工作流数据**

### API 调用：获取工作流定义

```typescript
// console/frontend/src/components/workflow/store/flow-manager-function.ts

export const initFlowData = async (id: string, set): Promise<void> => {
  // 并行请求多个 API
  const [
    flow,                 // 工作流定义
    nodeTemplate,         // 节点模板
    textNodeConfigList,   // 文本节点配置
    agentStrategy,        // Agent 策略
    knowledgeProStrategy  // 知识库策略
  ] = await Promise.all([
    getFlowDetailAPI(id),       // GET /workflow?id=184736
    flowsNodeTemplate(),         // GET /workflow/node-template
    textNodeConfigListAPI(),     // GET /textNode/config/list
    getAgentStrategyAPI(),       // GET /agent/strategy
    getKnowledgeProStrategyAPI() // GET /knowledge-pro/strategy
  ]);
  
  set({
    currentFlow: flow,
    nodeList: nodeTemplate,
    // ... 设置状态
  });
}
```

### 关键 API：`GET /workflow?id=184736`

**请求：**
```http
GET /api/workflow?id=184736 HTTP/1.1
Host: localhost
Authorization: Bearer <token>
```

**响应：**
```json
{
  "id": 123,
  "flowId": "184736",
  "name": "AI 播客生成工作流",
  "description": "将文本改写为播客风格并生成语音",
  "appId": "680ab54f",
  "data": "{\"nodes\":[...],\"edges\":[...]}",  // JSON 字符串
  "version": "v3.0.0",
  "createTime": "2025-01-01 10:00:00"
}
```

### 解析 DSL 数据

```typescript
// data 字段是 JSON 字符串，需要解析
const dslData = JSON.parse(flow.data);

// DSL 结构
{
  "nodes": [
    {
      "id": "node-start::001",
      "type": "node-start",
      "position": { "x": 100, "y": 100 },
      "data": {
        "aliasName": "开始",
        "outputs": [
          { "name": "user_input", "type": "string" }
        ]
      }
    },
    {
      "id": "node-llm::002",
      "type": "node-llm",
      "position": { "x": 300, "y": 100 },
      "data": {
        "aliasName": "大模型",
        "nodeParam": {
          "modelId": 1,
          "prompt": "你是沉默王二...\n\n用户输入：{{node-start::001.user_input}}"
        },
        "inputs": [
          { "name": "user_input", "ref": "node-start::001.user_input" }
        ],
        "outputs": [
          { "name": "llm_output", "type": "string" }
        ]
      }
    },
    {
      "id": "node-plugin::003",
      "type": "node-plugin",
      "position": { "x": 500, "y": 100 },
      "data": {
        "aliasName": "超拟人合成",
        "nodeParam": {
          "pluginId": "tool@8b2262bef821000",
          "operationId": "超拟人合成-46EXFdLW",
          "vcn": "x5_lingfeiyi_flow",
          "speed": 50
        },
        "inputs": [
          { "name": "text", "ref": "node-llm::002.llm_output" }
        ],
        "outputs": [
          { "name": "voice_url", "type": "string" }
        ]
      }
    },
    {
      "id": "node-end::004",
      "type": "node-end",
      "position": { "x": 700, "y": 100 },
      "data": {
        "aliasName": "结束",
        "nodeParam": {
          "outputMode": 1,  // 1=格式化输出, 0=直接返回参数
          "template": "<audio preload=\"none\" controls><source src=\"{{node-plugin::003.voice_url}}\" type=\"audio/mpeg\"></audio>",
          "reasoningTemplate": ""
        },
        "inputs": [
          { "name": "voice_url", "ref": "node-plugin::003.voice_url" }
        ]
      }
    }
  ],
  "edges": [
    {
      "id": "edge-1",
      "source": "node-start::001",
      "target": "node-llm::002",
      "sourceHandle": "user_input"
    },
    {
      "id": "edge-2",
      "source": "node-llm::002",
      "target": "node-plugin::003",
      "sourceHandle": "llm_output"
    },
    {
      "id": "edge-3",
      "source": "node-plugin::003",
      "target": "node-end::004",
      "sourceHandle": "voice_url"
    }
  ]
}
```

---

## 3️⃣ **执行工作流（用户点击调试按钮）**

### 前端发起执行请求

```typescript
// console/frontend/src/components/workflow/store/flow-chat-function.ts

const runDebugger = (obj: unknown): void => {
  const { nodes, edges, get, set, enters } = obj;
  const currentFlow = useFlowsManager.getState().currentFlow;
  
  // 构建 URL
  const url = getFixedUrl('/workflow/chat');  
  // 实际 URL: http://localhost/api/workflow/chat
  
  // 准备输入数据
  const inputs = {};
  enters.forEach(params => {
    inputs[params.name] = params.default;
  });
  
  // 构建请求参数
  const params = {
    flow_id: currentFlow?.flowId,  // "184736"
    inputs: inputs,                 // { "user_input": "介绍一下 Java" }
    chatId: get().chatIdRef,        // 随机生成的 chat ID
    regen: false                    // 是否重新生成
  };
  
  // SSE 流式请求
  fetchEventSource(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': getAuthorization()
    },
    body: JSON.stringify(params),
    openWhenHidden: true,
    onmessage(e) {
      handleMessage(nodes, edges, e, get, set);
    },
    onerror() {
      controller?.abort();
    }
  });
};
```

### 后端路由（Console Hub）

```java
// console/backend/hub
// Nginx 转发到 Console Hub: http://console-hub:8080

// Console Hub 再转发到 Python Workflow 或 Java Workflow
@Service
public class WorkflowProxyService {
    
    @Value("${workflow.version:python}")
    private String workflowVersion;
    
    @Value("${workflow.python.url:http://core-workflow-python:7880}")
    private String pythonWorkflowUrl;
    
    @Value("${workflow.java.url:http://core-workflow-java:7881}")
    private String javaWorkflowUrl;
    
    public void proxyToWorkflow(HttpServletRequest request, HttpServletResponse response) {
        String targetUrl = "java".equals(workflowVersion) 
            ? javaWorkflowUrl 
            : pythonWorkflowUrl;
        
        // 转发请求到 workflow 服务
        // targetUrl + "/workflow/v1/debug/chat/completions"
    }
}
```

---

## 4️⃣ **Python Workflow 处理请求**

### API 端点

```python
# core/workflow/api/v1/chat/debug.py

@router.post("/debug/chat/completions", response_model=None)
async def chat_debug(
    x_consumer_username: Annotated[str, Header()],
    chat_vo: ChatVo,
) -> Union[StreamingResponse, JSONResponse]:
    """
    调试模式的聊天完成接口
    """
    app_id = x_consumer_username
    span = Span(app_id=app_id, uid=chat_vo.uid, chat_id=chat_vo.chat_id)
    
    with span.start(attributes={"flow_id": chat_vo.flow_id}):
        # 1. 获取工作流定义
        db_flow = flow_service.get(chat_vo.flow_id, session, span)
        
        # 2. 创建事件
        event = Event(
            flow_id=chat_vo.flow_id,
            app_id=app_id,
            event_id=str(get_id()),
            uid=chat_vo.uid,
            chat_id=chat_vo.chat_id
        )
        EventRegistry().init_event(event)
        
        # 3. 执行工作流（流式返回）
        return await Streaming.send(
            await chat_service.event_stream(
                app_id,
                event.event_id,
                db_flow.data,      # DSL JSON 字符串
                db_flow.update_at,
                chat_vo,           # 包含 inputs
                False,
                app_audit_policy,
                span_context
            ),
            StreamingResponse if chat_vo.stream else JSONResponse
        )
```

### 请求参数（ChatVo）

```python
class ChatVo(BaseModel):
    flow_id: str              # "184736"
    inputs: Dict[str, Any]    # {"user_input": "介绍一下 Java"}
    chat_id: str              # "abc123..."
    uid: str                  # 用户 ID
    stream: bool = True       # 是否流式返回
    regen: bool = False       # 是否重新生成
    version: Optional[str]    # 版本号（可选）
```

### 工作流执行引擎

```python
# core/workflow/service/chat_service.py

async def event_stream(
    app_id: str,
    event_id: str,
    dsl_data: str,  # DSL JSON 字符串
    update_at: datetime,
    chat_vo: ChatVo,
    is_open_api: bool,
    app_audit_policy: AppAuditPolicy,
    span: Span
) -> AsyncGenerator:
    """
    执行工作流并流式返回结果
    """
    # 1. 解析 DSL
    workflow_dsl = WorkflowDSL.parse_raw(dsl_data)
    
    # 2. 创建工作流引擎
    engine = WorkflowEngine.build(
        workflow_dsl=workflow_dsl,
        inputs=chat_vo.inputs,
        chat_id=chat_vo.chat_id,
        span=span
    )
    
    # 3. 执行工作流（异步生成器，逐步返回结果）
    async for message in engine.run():
        yield message
```

---

## 5️⃣ **工作流执行流程（核心）**

### 引擎初始化

```python
# core/workflow/engine/dsl_engine.py

class WorkflowEngine:
    
    @classmethod
    def build(cls, workflow_dsl: WorkflowDSL, inputs: Dict, chat_id: str, span: Span):
        """
        构建工作流引擎
        """
        # 1. 创建变量池
        variable_pool = VariablePool()
        
        # 2. 将输入放入变量池
        for key, value in inputs.items():
            variable_pool.set("node-start::001", key, value)
        
        # 3. 构建节点实例
        built_nodes = {}
        for node_dsl in workflow_dsl.nodes:
            node_instance = NodeFactory.create(node_dsl)
            built_nodes[node_dsl.id] = node_instance
        
        # 4. 构建执行链（根据 edges 确定执行顺序）
        chains = Chains.build(workflow_dsl.edges)
        
        # 5. 创建引擎实例
        return cls(
            variable_pool=variable_pool,
            built_nodes=built_nodes,
            chains=chains,
            span=span
        )
    
    async def run(self) -> AsyncGenerator:
        """
        执行工作流
        """
        # 按照 chains 顺序执行节点
        for node_id in self.execution_order:
            node = self.built_nodes[node_id]
            
            # 执行节点
            result = await node.async_execute(
                variable_pool=self.variable_pool,
                span=self.span
            )
            
            # 流式返回节点执行状态
            yield {
                "event": "node_started",
                "data": {"node_id": node_id, "status": "running"}
            }
            
            if result.status == WorkflowNodeExecutionStatus.SUCCEEDED:
                # 将输出放入变量池
                for key, value in result.outputs.items():
                    self.variable_pool.set(node_id, key, value)
                
                yield {
                    "event": "node_finished",
                    "data": {
                        "node_id": node_id,
                        "status": "success",
                        "outputs": result.outputs
                    }
                }
            else:
                yield {
                    "event": "node_finished",
                    "data": {
                        "node_id": node_id,
                        "status": "failed",
                        "error": result.error
                    }
                }
                break  # 失败则停止
        
        # 返回最终结果
        yield {
            "event": "workflow_finished",
            "data": {
                "status": "success",
                "output": self.variable_pool.get("node-end::004", "output")
            }
        }
```

### 节点执行示例

#### StartNode
```python
# core/workflow/engine/nodes/start/start_node.py

class StartNode(BaseNode):
    async def async_execute(self, variable_pool: VariablePool, span: Span):
        """
        开始节点：从变量池收集输入
        """
        outputs = {}
        for key in self.output_identifier:  # ["user_input"]
            outputs[key] = variable_pool.get(self.node_id, key, span)
        
        return NodeRunResult(
            status=WorkflowNodeExecutionStatus.SUCCEEDED,
            inputs=outputs,
            outputs={},
            node_id=self.node_id
        )
```

#### LLMNode
```python
# core/workflow/engine/nodes/llm/spark_llm_node.py

class SparkLLMNode(BaseLLMNode):
    async def async_execute(self, variable_pool: VariablePool, span: Span):
        """
        大模型节点：调用 LLM API
        """
        # 1. 获取输入（从变量池）
        user_input = variable_pool.get("node-start::001", "user_input", span)
        
        # 2. 处理 prompt 模板
        prompt = self.nodeParam.prompt  # "你是沉默王二...\n\n用户输入：{{node-start::001.user_input}}"
        prompt = variable_pool.resolve(prompt)  # 替换 {{...}} 变量
        # 结果: "你是沉默王二...\n\n用户输入：介绍一下 Java"
        
        # 3. 调用 LLM API（DeepSeek）
        model_config = get_model_config(self.nodeParam.modelId)
        response = await call_llm_api(
            url=model_config.url,
            api_key=model_config.api_key,
            messages=[{"role": "user", "content": prompt}]
        )
        
        # 4. 返回结果
        llm_output = response["choices"][0]["message"]["content"]
        
        return NodeRunResult(
            status=WorkflowNodeExecutionStatus.SUCCEEDED,
            inputs={"user_input": user_input},
            outputs={"llm_output": llm_output},  # 改写后的播客稿
            node_id=self.node_id
        )
```

#### PluginNode（超拟人合成）
```python
# core/workflow/engine/nodes/plugin_tool/plugin_node.py

class PluginNode(BaseNode):
    pluginId: str = "tool@8b2262bef821000"
    operationId: str = "超拟人合成-46EXFdLW"
    
    async def async_execute(self, variable_pool: VariablePool, span: Span):
        """
        插件节点：调用超拟人合成工具
        """
        # 1. 获取输入（LLM 的输出）
        text = variable_pool.get("node-llm::002", "llm_output", span)
        
        # 2. 构建请求参数
        params = {
            "vcn": self.nodeParam.vcn,      # "x5_lingfeiyi_flow"
            "text": text,                    # LLM 改写后的播客稿
            "speed": self.nodeParam.speed    # 50
        }
        
        # 3. 调用 core-aitools 服务
        url = "http://core-aitools:18668/aitools/v1/smarttts"
        response = await http_post(url, params)
        
        # 4. 解析响应
        voice_url = response["data"]["voice_url"]
        # 例如: "http://minio:9000/bucket/podcast_abc123.mp3"
        
        return NodeRunResult(
            status=WorkflowNodeExecutionStatus.SUCCEEDED,
            inputs={"text": text},
            outputs={"voice_url": voice_url},
            node_id=self.node_id
        )
```

#### EndNode
```python
# core/workflow/engine/nodes/end/end_node.py

class EndNode(BaseOutputNode):
    template: str  # HTML 模板
    outputMode: int  # 0=返回参数, 1=返回格式化内容
    
    async def async_execute(self, variable_pool: VariablePool, span: Span):
        """
        结束节点：渲染输出模板
        """
        if self.outputMode == 1:  # 格式化输出
            # 1. 获取模板
            template = self.template
            # "<audio preload=\"none\" controls><source src=\"{{node-plugin::003.voice_url}}\" type=\"audio/mpeg\"></audio>"
            
            # 2. 替换变量
            content = variable_pool.resolve(template)
            # 结果: "<audio preload=\"none\" controls><source src=\"http://minio:9000/bucket/podcast_abc123.mp3\" type=\"audio/mpeg\"></audio>"
            
            return NodeRunResult(
                status=WorkflowNodeExecutionStatus.SUCCEEDED,
                inputs={},
                outputs={"content": content},  # 最终输出
                node_id=self.node_id
            )
        else:  # 直接返回参数
            # 收集所有输入的输出值
            outputs = {}
            for input_ref in self.inputs:
                outputs[input_ref.name] = variable_pool.get_by_ref(input_ref.ref)
            
            return NodeRunResult(
                status=WorkflowNodeExecutionStatus.SUCCEEDED,
                inputs={},
                outputs=outputs,
                node_id=self.node_id
            )
```

---

## 6️⃣ **SSE 流式响应格式**

### 返回给前端的消息格式

```javascript
// 每条消息格式
{
  event: "message",
  data: {
    nodeId: "node-llm::002",
    nodeStatus: "ing",  // "ing" | "success" | "failed"
    nodeAnswerContent: "这是一段...",  // 节点输出内容（流式累加）
    reasoningContent: "",              // 推理内容
    inputs: {...},                     // 节点输入
    outputs: {...},                    // 节点输出
    timeCost: 1.5,                     // 耗时（秒）
    totalTokens: 500                   // Token 消耗
  }
}
```

### 前端处理流式消息

```typescript
// console/frontend/src/components/workflow/store/flow-chat-function.ts

const handleMessage = (nodes, edges, e, get, set) => {
  const responseResult = JSON.parse(e.data);
  const { nodeId, nodeStatus } = responseResult;
  
  // 更新节点状态
  const currentNode = nodes.find(node => node.id === nodeId);
  
  if (nodeStatus === 'ing') {
    // 节点运行中
    currentNode.data.status = 'running';
    currentNode.data.debuggerResult = {
      answerContent: responseResult.nodeAnswerContent,  // 累加显示
      reasoningContent: responseResult.reasoningContent
    };
  } else if (nodeStatus === 'success') {
    // 节点成功
    currentNode.data.status = 'success';
    currentNode.data.debuggerResult = {
      input: responseResult.inputs,
      output: responseResult.outputs,
      timeCost: responseResult.timeCost,
      tokenCost: responseResult.totalTokens,
      done: true
    };
  } else if (nodeStatus === 'failed') {
    // 节点失败
    currentNode.data.status = 'failed';
    currentNode.data.debuggerResult = {
      failedReason: responseResult.failedReason
    };
  }
  
  // 触发 React 重新渲染
  setNode(nodeId, currentNode);
};
```

---

## 7️⃣ **完整数据流示例**

### 输入
```json
{
  "flow_id": "184736",
  "inputs": {
    "user_input": "介绍一下 Java 和 Python 的区别"
  },
  "chat_id": "chat_abc123",
  "stream": true
}
```

### 执行过程

#### 1. StartNode 执行
```json
{
  "event": "node_finished",
  "data": {
    "nodeId": "node-start::001",
    "nodeStatus": "success",
    "inputs": {
      "user_input": "介绍一下 Java 和 Python 的区别"
    },
    "outputs": {}
  }
}
```

#### 2. LLMNode 执行（流式）
```json
// 消息 1
{
  "event": "node_started",
  "data": {
    "nodeId": "node-llm::002",
    "nodeStatus": "ing",
    "nodeAnswerContent": "大家好，"
  }
}

// 消息 2
{
  "event": "message",
  "data": {
    "nodeId": "node-llm::002",
    "nodeStatus": "ing",
    "nodeAnswerContent": "大家好，欢迎来到王二电台"
  }
}

// ... 更多流式消息

// 最终消息
{
  "event": "node_finished",
  "data": {
    "nodeId": "node-llm::002",
    "nodeStatus": "success",
    "inputs": {
      "user_input": "介绍一下 Java 和 Python 的区别"
    },
    "outputs": {
      "llm_output": "大家好，欢迎来到王二电台。今天咱们聊聊 Java 和 Python 的区别..."
    },
    "timeCost": 2.5,
    "totalTokens": 500
  }
}
```

#### 3. PluginNode 执行（超拟人合成）
```json
{
  "event": "node_finished",
  "data": {
    "nodeId": "node-plugin::003",
    "nodeStatus": "success",
    "inputs": {
      "text": "大家好，欢迎来到王二电台。今天咱们聊聊 Java 和 Python 的区别..."
    },
    "outputs": {
      "voice_url": "http://minio:9000/bucket/podcast_20250112_abc123.mp3"
    },
    "timeCost": 3.2
  }
}
```

#### 4. EndNode 执行（输出 HTML）
```json
{
  "event": "workflow_finished",
  "data": {
    "nodeId": "node-end::004",
    "nodeStatus": "success",
    "outputs": {
      "content": "<audio preload=\"none\" controls><source src=\"http://minio:9000/bucket/podcast_20250112_abc123.mp3\" type=\"audio/mpeg\"></audio>"
    }
  }
}
```

### 最终输出
```html
<audio preload="none" controls>
  <source src="http://minio:9000/bucket/podcast_20250112_abc123.mp3" type="audio/mpeg">
</audio>
```

前端将此 HTML 渲染到页面，用户即可播放生成的播客音频。

---

## 🎯 关键要点总结

### 1. DSL 结构
- **nodes**: 节点数组，每个节点包含 `id`, `type`, `data`
- **edges**: 连接数组，定义节点间的数据流向
- **data.nodeParam**: 节点配置参数
- **data.inputs/outputs**: 节点输入输出定义

### 2. 变量引用
- 格式：`{{node-id.output-name}}`
- 示例：`{{node-llm::002.llm_output}}`
- 在执行时由 VariablePool 解析和替换

### 3. 节点类型
- `node-start`: 收集用户输入
- `node-llm`: 调用大模型
- `node-plugin`: 调用工具（如超拟人合成）
- `node-end`: 输出结果（支持模板渲染）

### 4. 执行模式
- **流式执行**：通过 SSE 实时返回节点执行状态
- **顺序执行**：按照 edges 定义的依赖关系顺序执行
- **变量传递**：通过 VariablePool 在节点间传递数据

### 5. 输出模式
- **outputMode = 0**: 直接返回变量值（JSON）
- **outputMode = 1**: 使用模板渲染（HTML/文本）

---

## 📊 Java 实现需要的核心类

基于以上分析，Java 版本需要实现：

### 领域模型
- `WorkflowDSL` - DSL 定义
- `Node` - 节点定义
- `Edge` - 连接定义
- `ChatVo` - 请求参数

### 引擎核心
- `WorkflowEngine` - 工作流引擎
- `VariablePool` - 变量池（支持 `{{}}` 解析）
- `NodeFactory` - 节点工厂

### 节点实现
- `StartNode`
- `LLMNode`
- `PluginNode`
- `EndNode`

### API 接口
- `POST /workflow/v1/debug/chat/completions` - SSE 流式接口
- `GET /workflow?id={id}` - 获取工作流定义

现在您已经完全了解整个流程了！🚀
