# Core RPA 模块使用场景分析

## 📌 概述

**RPA (Robotic Process Automation, 机器人流程自动化)** 是一个独立的微服务模块，用于在工作流中集成外部 RPA 系统（如讯飞"小武"RPA 平台）。

---

## 🔧 服务信息

### 基本信息
- **服务名称**: `astron-agent-core-rpa`
- **启动命令**: `uv run plugin/rpa/main.py`
- **API 端口**: 17198 (内部端口)
- **技术栈**: FastAPI + Python 3.11
- **API 端点**: `/rpa/v1/exec`

### 架构位置
```
工作流引擎 (core/workflow)
    ↓ RPA 节点执行
core/plugin/rpa 服务 (17198端口)
    ↓ HTTP 请求
外部 RPA 平台 (讯飞小武 RPA)
    ↓ 任务执行
返回自动化结果
```

---

## 🎯 使用场景

### 场景 1: 工作流中的 RPA 节点

**节点标识**: `idType: "rpa"`

**位置**: `core/workflow/engine/nodes/rpa/rpa_node.py`

**调用时机**:
- 用户在工作流编辑器中拖入 **RPA 节点**
- 工作流执行到 RPA 节点时

**典型应用**:
1. **网页自动化**
   - 自动填写表单
   - 数据抓取（Web Scraping）
   - 批量操作网页元素

2. **桌面应用自动化**
   - 自动化办公软件操作（Excel、Word）
   - 文件批量处理
   - 数据录入自动化

3. **业务流程自动化**
   - 发票处理
   - 订单审批
   - 数据迁移

4. **集成外部系统**
   - 调用讯飞"小武" RPA 平台
   - 执行预定义的 RPA 脚本
   - 获取自动化任务结果

---

## 📝 节点配置

### RPA 节点参数

```python
class RPANode(BaseNode):
    """
    RPA 节点配置
    
    参数:
    - projectId: RPA 项目 ID（在小武平台创建）
    - header: 认证头（包含 apiKey）
    - rpaParams: RPA 执行参数
        - execPosition: 执行位置（默认 "EXECUTOR"）
    - 输入变量: 传递给 RPA 脚本的参数
    - 输出变量: RPA 脚本返回的结果
    """
```

### 配置示例

**前端节点配置**:
```json
{
  "idType": "rpa",
  "nodeType": "工具节点",
  "aliasName": "RPA",
  "description": "调用RPA平台执行RPA流程",
  "data": {
    "projectId": "rpa-project-123",
    "header": {
      "apiKey": "Bearer your-token-here"
    },
    "rpaParams": {
      "execPosition": "EXECUTOR"
    },
    "inputs": [
      {
        "name": "url",
        "type": "string",
        "description": "要抓取的网页 URL"
      },
      {
        "name": "timeout",
        "type": "number",
        "description": "超时时间（秒）"
      }
    ],
    "outputs": [
      {
        "name": "result",
        "type": "object",
        "description": "抓取结果"
      },
      {
        "name": "status",
        "type": "string",
        "description": "执行状态"
      }
    ]
  }
}
```

---

## 🔄 完整请求流程

### 1. 前端触发
```
用户在工作流编辑器中配置 RPA 节点
    ↓
设置 projectId、apiKey、输入参数
    ↓
保存工作流
```

### 2. 工作流执行
```
core/workflow 引擎
    ↓
执行到 RPA 节点
    ↓
RPANode.execute()
```

### 3. RPA 服务调用
```python
# core/workflow/engine/nodes/rpa/rpa_node.py:54
url = f"{RPA_BASE_URL}/rpa/v1/exec"
req_body = {
    "project_id": self.projectId,
    "sid": span.sid,
    "exec_position": "EXECUTOR",
    "params": inputs  # 从变量池获取输入参数
}
headers = {
    "Authorization": self.header.get("apiKey")
}
```

### 4. RPA 平台执行
```
core/plugin/rpa (17198端口)
    ↓ task_monitoring()
创建 RPA 任务
    ↓ create_task()
调用外部 RPA 平台 API
    ↓
XIAOWU_RPA_TASK_CREATE_URL
    ↓
返回 task_id
    ↓ query_task_status()
轮询任务状态
    ↓
任务完成，返回结果
```

### 5. 结果返回
```
RPA 平台返回结果
    ↓ SSE 流式响应
core/plugin/rpa
    ↓
工作流引擎
    ↓
更新变量池输出
    ↓
继续执行下一个节点
```

---

## 🔑 关键代码位置

### 工作流 RPA 节点
**文件**: `core/workflow/engine/nodes/rpa/rpa_node.py:31`

```python
class RPANode(BaseNode):
    async def execute(self, variable_pool, span, event_log_node_trace):
        # 1. 获取输入参数
        inputs = {id: variable_pool.get_variable(id) for id in self.input_identifier}
        
        # 2. 调用 RPA 服务
        url = f"{RPA_BASE_URL}/rpa/v1/exec"
        async with session.post(url, headers=headers, json=req_body) as response:
            # 3. 处理流式响应
            async for line in response.content:
                frame = _StreamResponse.model_validate_json(line)
                if frame.code != 0:
                    raise CustomException(...)
                data = frame.data
        
        # 4. 设置输出变量
        outputs = {output: data.get(output) for output in self.output_identifier}
        return NodeRunResult(status=SUCCEEDED, outputs=outputs)
```

### RPA 服务核心逻辑
**文件**: `core/plugin/rpa/service/xiaowu/process.py:23`

```python
async def task_monitoring(sid, access_token, project_id, exec_position, params):
    # 1. 创建 RPA 任务
    task_id = await create_task(
        access_token=access_token,
        project_id=project_id,
        exec_position=exec_position,
        params=params
    )
    
    # 2. 轮询任务状态
    while True:
        await asyncio.sleep(task_query_interval)
        task_status = await query_task_status(access_token, task_id)
        
        if task_status == "completed":
            yield RPAExecutionResponse(code=0, data=result)
            break
        elif task_status == "failed":
            yield RPAExecutionResponse(code=-1, message=error)
            break
        else:
            yield "ping"  # 保持连接
```

### 任务创建接口
**文件**: `core/plugin/rpa/infra/xiaowu/tasks.py:16`

```python
async def create_task(access_token, project_id, version, exec_position, params):
    task_create_url = os.getenv("XIAOWU_RPA_TASK_CREATE_URL")
    
    response = await client.post(task_create_url, headers={
        "Authorization": f"Bearer {access_token}"
    }, json={
        "project_id": project_id,
        "exec_position": exec_position,
        "params": params
    })
    
    task_id = response.json()["data"]["task_id"]
    return task_id
```

---

## ⚙️ 环境配置

### 必需的环境变量

```bash
# core/plugin/rpa/config.env

# RPA 平台 API 地址
XIAOWU_RPA_TASK_CREATE_URL=https://rpa-api.iflytek.com/task/create
XIAOWU_RPA_TASK_QUERY_URL=https://rpa-api.iflytek.com/task/query

# 服务端口
SERVICE_PORT=17198

# 超时配置
TASK_QUERY_INTERVAL=10        # 任务查询间隔（秒）
TASK_TIMEOUT=3600             # 任务超时时间（秒）
```

### 工作流引擎配置

```bash
# core/workflow/config.env

# RPA 服务地址
RPA_BASE_URL=http://core-rpa:17198
```

---

## 🎬 典型工作流示例

### 示例 1: 网页数据抓取 + 播客生成

```
[开始节点]
    ↓
[RPA 节点] - 抓取新闻网站内容
    ↓ 输出: articleContent
[LLM 节点] - 生成播客脚本
    ↓ 输入: articleContent
    ↓ 输出: podcastScript
[工具节点 - 语音合成] - 生成音频
    ↓ 输入: podcastScript
    ↓ 输出: audioUrl
[结束节点]
```

### 示例 2: Excel 数据处理

```
[开始节点]
    ↓
[RPA 节点] - 读取 Excel 文件
    ↓ 输出: excelData
[LLM 节点] - 数据分析
    ↓ 输入: excelData
    ↓ 输出: analysis
[RPA 节点] - 写回 Excel
    ↓ 输入: analysis
[结束节点]
```

---

## 🚨 是否需要 RPA 模块？

### ✅ 需要 RPA 的情况

1. **工作流使用 RPA 节点**
   - 节点配置中有 `idType: "rpa"`
   - 需要自动化操作网页、桌面应用
   - 需要集成讯飞小武 RPA 平台

2. **需要外部系统集成**
   - 自动化数据录入
   - 批量文件处理
   - 遗留系统自动化

### ❌ 不需要 RPA 的情况

**如果你的工作流只包含**:
- ✅ LLM 节点（大模型）
- ✅ 工具节点（Plugin）
- ✅ 语音合成节点
- ✅ 条件分支、循环等逻辑节点

**则可以禁用 RPA 服务**

---

## 🔧 如何禁用 RPA 服务

### 方案 1: 节点模板过滤

在之前配置的基础上，将 `rpa` 添加到过滤列表:

```sql
-- 当前配置已经包含 rpa 过滤
UPDATE config_info 
SET value = '...,rpa'
WHERE category = 'SPACE_SWITCH_NODE';
```

### 方案 2: Docker Compose 禁用

编辑 `docker-compose.yml`:

```yaml
# 注释掉 RPA 服务
# core-rpa:
#   image: ...
#   ...
```

---

## 📊 服务依赖关系

```
播客工坊系统
    │
    ├─ ✅ 必需服务
    │   ├─ console-hub (后端 API)
    │   ├─ console-frontend (前端界面)
    │   ├─ core/workflow (工作流引擎)
    │   ├─ core/plugin/aitools (语音合成)
    │   └─ core/plugin/link (工具集成)
    │
    └─ ❌ 可选服务（按需启用）
        ├─ core/agent (Agent 推理节点)
        ├─ core/memory/database (数据库节点)
        └─ core/plugin/rpa (RPA 自动化节点) ← 本文档
```

---

## 📝 配置验证

### 检查工作流是否使用 RPA 节点

```sql
-- 查询包含 RPA 节点的工作流
SELECT w.id, w.name 
FROM workflow w 
WHERE w.data LIKE '%"idType":"rpa"%';
```

### 测试 RPA 服务是否运行

```bash
# 检查容器状态
docker ps --filter "name=rpa"

# 测试健康检查（如果有）
curl http://localhost:17198/health

# 查看日志
docker logs astron-agent-core-rpa --tail 50
```

---

## 🎯 总结

| 特性 | 说明 |
|------|------|
| **用途** | 集成外部 RPA 平台，实现流程自动化 |
| **典型场景** | 网页抓取、Excel 处理、桌面应用自动化 |
| **外部依赖** | 讯飞"小武" RPA 平台 API |
| **是否必需** | ❌ 简单播客场景不需要 |
| **端口** | 17198 (内部服务) |
| **超时** | 默认 24 小时（长时间任务） |
| **通信方式** | HTTP + SSE 流式响应 |

**对于"播客工坊"项目**:
- 如果只需要 **文本转播客**（LLM + 语音合成），RPA 服务**不是必需的**
- 如果需要 **自动抓取新闻/文章** → 生成播客，则需要启用 RPA
- 可以通过 `SPACE_SWITCH_NODE` 配置控制节点显示（已配置过滤）
