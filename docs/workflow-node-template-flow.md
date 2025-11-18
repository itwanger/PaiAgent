# `/workflow/node-template` 完整请求流程

## 1️⃣ 前端发起请求

**位置**: `console/frontend/src/services/flow.ts:77-79`

```typescript
export async function flowsNodeTemplate(): Promise<unknown> {
  return http.get('/workflow/node-template');
}
```

## 2️⃣ 前端使用场景

**位置**: `console/frontend/src/components/workflow/store/flow-manager-function.ts:341`

- **调用时机**: 加载工作流详情时并行调用
- **使用目的**: 获取节点模板列表用于工作流编辑器左侧面板

```typescript
const [flow, nodeTemplate, ...] = await Promise.all([
  getFlowDetailAPI(id),
  flowsNodeTemplate(),  // 获取节点模板
  ...
]);
set({ nodeList: nodeTemplate });  // 存储到状态管理
```

## 3️⃣ 后端接口层

**位置**: `console/backend/toolkit/src/main/java/com/iflytek/astron/console/toolkit/controller/workflow/WorkflowController.java:269-272`

```java
@GetMapping("/node-template")
public Object getNodeTemplate(@RequestParam(required = false) Integer source) {
    return workflowService.getNodeTemplate(source);
}
```

## 4️⃣ 业务逻辑层

**位置**: `console/backend/toolkit/src/main/java/com/iflytek/astron/console/toolkit/service/workflow/WorkflowService.java:2907-2955`

### 核心逻辑

1. **查询配置表** (`config_info`):
   - 正常环境: `category='WORKFLOW_NODE_TEMPLATE'`
   - 预发布环境: `category='WORKFLOW_NODE_TEMPLATE_PRE'`
   - 条件: `is_valid=1`, `code LIKE '%0%'` (COMMON平台)

2. **空间节点过滤** (如果配置了 `SPACE_SWITCH_NODE`):
   - 从 `value` 字段解析出需要过滤的 `idType`
   - 移除匹配的节点类型

3. **数据分组与格式化**:
   - 按 `name` 字段分组 (如 "基础节点", "工具节点")
   - 每组包含多个节点配置 (`value` 字段的 JSON)

4. **返回格式**:
```json
[
  {
    "name": "基础节点",
    "nodes": [
      { "idType": "start", "label": "开始", ... },
      { "idType": "end", "label": "结束", ... }
    ]
  },
  {
    "name": "LLM节点",
    "nodes": [...]
  }
]
```

## 5️⃣ 数据库表结构

**位置**: `console/backend/toolkit/src/main/java/com/iflytek/astron/console/toolkit/entity/table/ConfigInfo.java`

### `config_info` 表字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 主键 |
| `category` | String | 配置类别 (如 `WORKFLOW_NODE_TEMPLATE`) |
| `code` | String | 配置代码 (平台标识) |
| `name` | String | 节点分组名称 (如 "基础节点") |
| `value` | String | 节点配置 JSON (包含 `idType`, `label` 等) |
| `is_valid` | Integer | 是否生效 (1=生效) |
| `remarks` | String | 备注 |
| `create_time` | LocalDateTime | 创建时间 |
| `update_time` | LocalDateTime | 更新时间 |

## 📊 完整流程图

```
前端工作流编辑器
    ↓ 
HTTP GET /workflow/node-template
    ↓
WorkflowController.getNodeTemplate()
    ↓ 
调用服务层
    ↓
WorkflowService.getNodeTemplate(source)
    ↓ 
查询数据库
    ↓
MySQL: config_info 表
    ↓ 
WHERE category='WORKFLOW_NODE_TEMPLATE' AND is_valid=1
    ↓
返回节点配置列表
    ↓ 
按 name 分组
    ↓ 
空间过滤 (可选)
    ↓
返回 JSON 数组
    ↓
前端存储到 nodeList 状态
    ↓
渲染工作流左侧节点面板
```

## 🔑 关键点

1. **配置驱动**: 节点模板通过数据库配置表管理，支持动态调整
2. **环境区分**: 支持预发布环境独立配置 (`WORKFLOW_NODE_TEMPLATE_PRE`)
3. **空间隔离**: 支持按空间过滤节点类型 (`SPACE_SWITCH_NODE`)
4. **分组展示**: 前端按分组名称渲染节点面板
5. **并行加载**: 与工作流详情等数据并行请求，优化性能

## 📝 数据库查询示例

```sql
-- 查询生效的节点模板配置
SELECT * FROM config_info 
WHERE category = 'WORKFLOW_NODE_TEMPLATE' 
  AND is_valid = 1 
  AND code LIKE '%0%'
ORDER BY name;

-- 查询空间节点过滤配置
SELECT * FROM config_info 
WHERE category = 'SPACE_SWITCH_NODE';
```

## 🎯 使用场景

- 工作流编辑器初始化时加载可用节点类型
- 用户拖拽节点到画布前展示节点列表
- 根据空间权限动态显示/隐藏特定节点
- 支持不同环境(生产/预发布)使用不同节点配置
