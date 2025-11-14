# Python Workflow 完整执行流程参考文档

**记录时间:** 2025-11-14 19:13:29  
**测试输入:** "Python1"  
**Workflow ID:** 7395055411409907714  
**流程:** 开始节点 → LLM节点(DeepSeek) → 语音合成插件 → 结束节点

---

## 1. 前端点击"发送"按钮触发的请求流程

### 1.1 保存 Workflow 配置
**请求:**
```http
POST http://core-workflow:7880/workflow/v1/protocol/update/7395055411409907714
Content-Type: application/json

{
  "id": "7395055411409907714",
  "app_id": "680ab54f",
  "name": "自定义17620251114191003",
  "description": "",
  "data": {
    "data": {
      "edges": [...],
      "nodes": [...]
    }
  }
}
```

**响应:**
```json
{
  "code": 0,
  "message": "success",
  "sid": "spf00130005@hf19a8212013c0010782"
}
```

**关键点:**
- ✅ 返回格式必须是 `{"code": 0, "message": "success", "sid": "..."}`
- ✅ data 字段包含嵌套的 `data.data` 结构，需要提取内层保存到数据库

---

### 1.2 验证/构建 Workflow
**请求:**
```http
POST http://core-workflow:7880/workflow/v1/protocol/build/7395055411409907714
```

**响应 (SSE 流):**
```
data: {"end_of_stream":true,"sid":"spf00130006@hf19a821201490010782"}
```

**关键点:**
- ✅ 返回 SSE 格式
- ✅ 最终返回 `end_of_stream: true` 表示构建完成
- ⚠️ 这是一个验证步骤，确保 workflow DSL 正确

---

### 1.3 执行 Workflow (调试模式)
**请求:**
```http
POST http://core-workflow:7880/workflow/v1/debug/chat/completions
Content-Type: application/json
Headers:
  Authorization: 7b709739e8da44536127a333c7603a83:NjhmY2NmM2NkZDE4MDFlNmM5ZjcyZjMy
  X-Consumer-Username: 680ab54f

{
  "stream": true,
  "debug": true,
  "parameters": {
    "AGENT_USER_INPUT": "Python1"
  },
  "uid": "b55d5545-d432-405d-9ddf-3c21276512f2",
  "flow_id": "7395055411409907714"
}
```

---

## 2. SSE 事件流详细顺序

### 2.1 工作流开始
```json
{
  "code": 0,
  "message": "Success",
  "id": "spf00130007@hf19a821201600010782",
  "created": 1763118809,
  "workflow_step": {
    "node": {
      "id": "flow_obj",
      "alias_name": "",
      "finish_reason": "stop",
      "inputs": {},
      "outputs": {},
      "error_outputs": {},
      "executed_time": 0.0,
      "usage": {
        "completion_tokens": 0,
        "prompt_tokens": 0,
        "total_tokens": 0
      }
    },
    "seq": 0,
    "progress": 0.0
  },
  "choices": [{
    "delta": {
      "role": "assistant",
      "content": "",
      "reasoning_content": ""
    },
    "index": 0
  }]
}
```

### 2.2 开始节点 - 启动
```json
{
  "workflow_step": {
    "node": {
      "id": "node-start::d61b0f71-87ee-475e-93ba-f1607f0ce783",
      "alias_name": "开始",
      "inputs": {},
      "outputs": {},
      "error_outputs": {},
      "executed_time": 0.0
    },
    "seq": 0,
    "progress": 0.0
  }
}
```

### 2.3 开始节点 - 完成
```json
{
  "workflow_step": {
    "node": {
      "id": "node-start::d61b0f71-87ee-475e-93ba-f1607f0ce783",
      "alias_name": "开始",
      "finish_reason": "stop",
      "inputs": {
        "AGENT_USER_INPUT": "Python1"
      },
      "outputs": {},
      "error_outputs": {},
      "ext": {},
      "executed_time": 0.001
    },
    "seq": 0,
    "progress": 0.0
  }
}
```

### 2.4 LLM节点 - 启动
```json
{
  "workflow_step": {
    "node": {
      "id": "spark-llm::348ce48c-0148-485f-9f3f-d64f38ed5eab",
      "alias_name": "大模型_1",
      "inputs": {},
      "outputs": {},
      "error_outputs": {},
      "executed_time": 0.0
    },
    "seq": 0,
    "progress": 0.25
  }
}
```

**关键点:**
- ✅ `progress` 从 0.0 → 0.25 (表示25%进度，4个节点中的第1个)
- ✅ LLM节点后续会流式输出内容

### 2.5 LLM节点 - 流式输出 (省略，实际会有多个chunk)
```json
{
  "choices": [{
    "delta": {
      "role": "assistant",
      "content": "欢迎收听王二电台...",
      "reasoning_content": ""
    }
  }],
  "workflow_step": {
    "node": {
      "id": "spark-llm::348ce48c-0148-485f-9f3f-d64f38ed5eab",
      "alias_name": "大模型_1"
    },
    "progress": 0.25
  }
}
```

### 2.6 插件节点 - 语音合成
```json
{
  "workflow_step": {
    "node": {
      "id": "plugin::594e15e6-2b7a-4612-a8e0-a459c7f730d8",
      "alias_name": "超拟人合成_1",
      "inputs": {
        "vcn": "x5_lingfeiyi_flow",
        "text": "...",
        "speed": 50
      },
      "outputs": {
        "code": 0,
        "message": "success",
        "data": {
          "voice_url": "https://..."
        }
      },
      "executed_time": 2.5
    },
    "progress": 0.5
  }
}
```

### 2.7 结束节点 - 最终输出
```json
{
  "workflow_step": {
    "node": {
      "id": "node-end::cda617af-551e-462e-b3b8-3bb9a041bf88",
      "alias_name": "结束",
      "finish_reason": "stop",
      "inputs": {
        "output": "https://..."
      },
      "outputs": {
        "final_result": "<audio><source src=\"https://...\"></audio>"
      },
      "executed_time": 0.001
    },
    "progress": 1.0
  }
}
```

### 2.8 工作流完成
```json
{
  "code": 0,
  "message": "Success",
  "workflow_step": {
    "node": {
      "id": "flow_obj",
      "finish_reason": "stop"
    },
    "progress": 1.0
  },
  "end_of_stream": true
}
```

---

## 3. Java 版本需要实现的 API 列表

### ✅ 已实现
1. `POST /workflow/v1/protocol/update/{id}` - 保存workflow配置
2. `POST /workflow/v1/chat/completions` - 执行workflow (已有基础实现)

### ❌ 待完善/实现
1. `POST /workflow/v1/protocol/build/{id}` - 验证/构建workflow
   - 返回格式: SSE
   - 响应: `{"end_of_stream": true, "sid": "..."}`

2. `POST /workflow/v1/debug/chat/completions` - 执行workflow (调试模式)
   - 返回格式: SSE
   - 支持参数:
     - `stream`: true/false
     - `debug`: true/false
     - `parameters`: 输入参数Map
     - `flow_id`: workflow ID
     - `uid`: 用户ID
   - 需要发送完整的SSE事件流（包括每个节点的启动、执行、完成事件）

---

## 4. 数据库字段说明

### workflow 表重要字段
```sql
CREATE TABLE workflow (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  flow_id VARCHAR(255),           -- 业务ID，API使用此字段
  app_id VARCHAR(255),            -- 应用ID
  name VARCHAR(255),              -- workflow名称
  description VARCHAR(512),       -- 描述
  data MEDIUMTEXT,                -- 编辑中的DSL (JSON格式: {"nodes": [...], "edges": [...]})
  published_data MEDIUMTEXT,      -- 已发布的DSL
  uid VARCHAR(128),               -- 用户ID
  create_time DATETIME,
  update_time DATETIME,
  can_publish BIT(1) DEFAULT 0,   -- 是否可以发布
  status TINYINT DEFAULT -1       -- 状态
);
```

**关键点:**
- ✅ `flow_id` 是字符串类型的业务ID，API中使用
- ✅ `id` 是数据库自增主键
- ✅ `data` 字段存储格式: `{"nodes": [...], "edges": [...]}`
- ❌ **不要**存储嵌套的 `{"data": {"nodes": [...]}}`

---

## 5. SSE 响应格式规范

### 标准SSE格式
```
data: {...json...}\n\n
```

### event类型 (可选)
```
event: node_start\ndata: {...}\n\n
event: node_end\ndata: {...}\n\n
```

### 关键字段含义
- `workflow_step.node.id` - 节点ID
- `workflow_step.node.alias_name` - 节点别名
- `workflow_step.node.finish_reason` - 完成原因 ("stop" 表示正常完成)
- `workflow_step.progress` - 整体进度 (0.0 ~ 1.0)
- `workflow_step.seq` - 序列号
- `choices[].delta.content` - 流式内容 (LLM输出)
- `end_of_stream` - 流结束标记

---

## 6. 节点执行顺序管理

### 拓扑排序
1. 从 `edges` 构建依赖图
2. 找到入度为0的节点 (通常是 `node-start`)
3. 按依赖关系顺序执行
4. 每个节点执行完成后，将输出传递给下一个节点

### 变量引用解析
```json
{
  "value": {
    "type": "ref",
    "content": {
      "nodeId": "spark-llm::xxx",
      "name": "output",
      "id": "xxx"
    }
  }
}
```

**解析逻辑:**
1. 检查 `value.type` 是否为 `ref`
2. 从 `content.nodeId` 获取前置节点ID
3. 从 `content.name` 获取输出字段名
4. 从变量池中查找 `${nodeId}.${name}` 的值

---

## 7. 错误处理

### API错误响应
```json
{
  "code": 500,
  "message": "错误信息",
  "sid": "..."
}
```

### SSE错误事件
```json
{
  "code": 500,
  "message": "节点执行失败",
  "workflow_step": {
    "node": {
      "id": "xxx",
      "error_outputs": {
        "error": "详细错误信息"
      }
    }
  },
  "end_of_stream": true
}
```

---

## 8. console-hub 的角色

console-hub 作为中间层：
1. 接收前端请求
2. 保存 workflow 配置到本地数据库 (MySQL)
3. 调用 workflow 引擎的 API
4. 转发 SSE 流到前端
5. 更新 workflow 状态 (`can_publish` 等)

**重要:** console-hub 和 workflow 引擎都有各自的数据库，需要同步。

---

## 9. 关键配置

### Headers
- `Authorization`: `{api_key}:{api_secret}`
- `X-Consumer-Username`: app_id

### 环境变量
- `SPARK_API_URL`: wss://spark-api.xf-yun.com/v3.5/chat
- `SPARK_APP_ID`: iFlytek应用ID
- `SPARK_API_KEY`: API密钥
- `SPARK_API_SECRET`: API密钥

---

## 10. Java 实现关键点

### 10.1 已有的基础架构
- ✅ SSE支持 (SseEmitter)
- ✅ 节点执行器框架 (NodeExecutor接口)
- ✅ 变量池 (VariablePool)
- ✅ 工作流引擎 (WorkflowEngine)
- ✅ 直接调用 iFlyTek Spark API (SparkLLMClient)

### 10.2 需要补充的功能
1. **protocol/build 端点**
   - 验证 workflow DSL 是否正确
   - 返回 SSE 格式的构建结果

2. **完善 SSE 事件流**
   - 当前只发送 `workflow_complete` 事件
   - 需要发送每个节点的启动、执行中、完成事件
   - 需要发送 `workflow_step` 信息（包括进度）

3. **debug模式支持**
   - 添加 `/workflow/v1/debug/chat/completions` 端点
   - 或在现有端点中通过参数区分debug模式

---

## 11. 测试建议

### 11.1 单元测试
- 测试每个节点执行器
- 测试变量引用解析
- 测试拓扑排序

### 11.2 集成测试
- 使用真实的workflow DSL
- 验证SSE事件顺序
- 验证最终输出正确性

### 11.3 对比测试
- 同时运行Python和Java版本
- 对比SSE事件流
- 对比最终输出结果
- 使用 `./scripts/compare-workflows.sh` 脚本

---

**记录者:** Qoder AI  
**文档版本:** 1.0  
**最后更新:** 2025-11-14 19:15
