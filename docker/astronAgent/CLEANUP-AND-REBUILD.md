# 🚀 Docker Compose 清理和重新部署指南

## ✅ 修复完成

Docker Compose 配置已自动修复并精简，详情：

### 修复内容
1. ✅ 删除 `core-database` 服务定义
2. ✅ 注释 7 个已删除服务的环境变量引用
   - `CHUNK_QUERY_URL` (core-knowledge)
   - `APP_AUTH_HOST` (core-tenant)
   - `KNOWLEDGE_BASE_URL` (core-knowledge)
   - `KNOWLEDGE_PRO_BASE_URL` (core-knowledge)
   - `APP_MANAGE_PLAT_BASE_URL` (core-tenant)
   - `PGSQL_BASE_URL` (core-database)
   - `RPA_BASE_URL` (core-rpa)
3. ✅ 验证 `depends_on` 依赖关系
4. ✅ YAML 语法验证通过

### 备份文件
- `docker-compose.yaml.backup-20251118-114205`

---

## 🎯 保留的服务架构 (14个)

### 基础设施层 (6个)
```
postgres         - PostgreSQL 数据库
mysql            - MySQL 数据库
redis            - Redis 缓存
elasticsearch    - Elasticsearch 搜索引擎
kafka            - Kafka 消息队列
minio            - MinIO 对象存储
```

### 核心服务层 (4个)
```
core-link        - 工具集成服务 (18888端口)
core-aitools     - AI工具/语音合成 (18668端口)
core-agent       - Agent推理服务
core-workflow    - 工作流引擎 (7880端口)
```

### 前端服务层 (4个)
```
nginx            - 反向代理 (80端口)
console-frontend - React前端 (内部1881端口)
console-hub      - Spring Boot后端 (8080端口)
```

---

## 📋 部署步骤

### 步骤 1: 清理旧环境

```bash
cd /Users/itwanger/Documents/GitHub/PaiAgent/docker/astronAgent

# 停止并删除所有容器、卷
docker compose down -v

# 清理未使用的镜像、容器、网络（可选，谨慎使用）
docker system prune -a --volumes
```

**⚠️ 注意**: `docker system prune -a --volumes` 会删除所有未使用的资源，包括数据卷！

### 步骤 2: 检查源代码结构

```bash
# 验证保留的核心模块
ls -la ../../core/
# 应该看到: agent, common, workflow, plugin, logs

# 验证插件模块
ls -la ../../core/plugin/
# 应该看到: aitools, link, __init__.py

# 验证 Dockerfile 存在
ls ../../core/agent/Dockerfile
ls ../../core/workflow/Dockerfile
ls ../../core/plugin/link/Dockerfile
ls ../../core/plugin/aitools/Dockerfile
```

### 步骤 3: 构建镜像

```bash
cd /Users/itwanger/Documents/GitHub/PaiAgent/docker/astronAgent

# 方式 2: 增量构建（更快，但可能有缓存问题）
docker compose build

# 方式 3: 单独构建特定服务
docker compose build --no-cache core-workflow
docker compose build --no-cache core-agent
docker compose build --no-cache core-link
docker compose build --no-cache core-aitools
docker compose build --no-cache console-hub
docker compose build --no-cache console-frontend
```

### 步骤 4: 启动服务

```bash
# 启动所有服务
docker compose up -d

# 查看启动日志
docker compose logs -f

# 查看特定服务日志
docker compose logs -f core-workflow
docker compose logs -f console-hub
```

### 步骤 5: 验证服务状态

```bash
# 查看所有服务状态
docker compose ps

# 应该看到 14 个服务都是 running 状态
# 如果有 unhealthy 或 exited 状态，查看对应日志

# 健康检查
docker compose ps --filter "health=unhealthy"
```

### 步骤 6: 功能验证

```bash
# 1. 前端访问
curl -I http://localhost/
# 期望: HTTP/1.1 200 OK

# 2. 后端 API
curl http://localhost:8080/actuator/health
# 期望: {"status":"UP"}

# 3. 工作流引擎
curl http://localhost:7880/health
# 期望: 健康状态

# 4. 浏览器访问
open http://localhost
# 登录: admin / 123
```

---

## 🔧 常见问题排查

### 问题 1: 构建失败

```bash
# 查看构建日志
docker compose build core-workflow 2>&1 | tee build.log

# 检查 Dockerfile 路径
ls -la ../../core/workflow/Dockerfile

# 检查构建上下文
ls -la ../../core/workflow/
```

### 问题 2: 服务启动失败

```bash
# 查看失败服务的日志
docker compose logs core-workflow

# 查看容器退出原因
docker compose ps -a

# 进入容器调试
docker compose exec core-workflow bash
```

### 问题 3: 数据库连接失败

```bash
# 检查数据库服务状态
docker compose ps postgres mysql redis

# 测试数据库连接
docker compose exec mysql mysql -uroot -proot123 -e "SELECT 1"
docker compose exec postgres psql -U spark -d sparkdb_manager -c "SELECT 1"

# 检查环境变量
docker compose exec core-workflow env | grep -E "MYSQL|POSTGRES|REDIS"
```

### 问题 4: 端口冲突

```bash
# 检查端口占用
lsof -i :80
lsof -i :8080
lsof -i :7880

# 杀死占用进程
kill -9 <PID>

# 或修改端口配置（在 .env 文件中）
```

---

## 📊 服务依赖关系图

```
┌─────────────────────────────────────────────────────────┐
│                   Nginx (Port 80)                       │
│                   反向代理 & 静态文件                      │
└─────────────────┬───────────────────────────────────────┘
                  │
        ┌─────────┴─────────┐
        │                   │
        ▼                   ▼
┌───────────────┐   ┌───────────────┐
│console-frontend│   │  console-hub  │
│  React 前端     │   │ Spring Boot   │
│  Port 1881     │   │  Port 8080    │
└───────────────┘   └────────┬──────┘
                             │
                    ┌────────┴────────┐
                    ▼                 ▼
            ┌───────────────┐ ┌──────────────┐
            │ core-workflow │ │  core-agent  │
            │ FastAPI 引擎   │ │ Agent 推理    │
            │  Port 7880    │ │              │
            └───────┬───────┘ └──────────────┘
                    │
        ┌───────────┼───────────┐
        ▼           ▼           ▼
┌──────────┐ ┌──────────┐ ┌──────────┐
│core-link │ │core-aitools│ │基础设施   │
│工具集成   │ │语音合成    │ │MySQL/PG  │
│Port 18888│ │Port 18668│ │Redis/Minio│
└──────────┘ └──────────┘ └──────────┘
```

---

## 🎯 启动后验证清单

- [ ] 所有 14 个服务状态为 `running`
- [ ] Nginx 80 端口可访问
- [ ] 前端页面可以打开 (http://localhost)
- [ ] 可以登录系统 (admin / 123)
- [ ] console-hub 健康检查通过
- [ ] core-workflow 健康检查通过
- [ ] MySQL 数据库可连接
- [ ] PostgreSQL 数据库可连接
- [ ] Redis 可连接
- [ ] MinIO 对象存储可访问

---

## 📝 下一步建议

1. **数据初始化**
   - 检查 MySQL 数据库是否已初始化（`astron_console` 库）
   - 检查 PostgreSQL 数据库（工作流数据）

2. **配置调整**
   - 更新 `SPACE_SWITCH_NODE` 配置（已完成）
   - 检查节点模板是否只显示 LLM 和工具节点

3. **功能测试**
   - 创建简单工作流测试
   - 测试 LLM 节点
   - 测试语音合成节点

4. **性能优化**
   - 根据实际负载调整资源限制
   - 配置日志轮转
   - 设置监控告警

---

## 🔗 相关文档

- `DOCKER-LOGS-GUIDE.md` - Docker 日志查看指南
- `LOCAL-BUILD-GUIDE.md` - 本地构建指南
- `../../docs/workflow-node-template-flow.md` - 节点模板流程
- `../../docs/core-agent-memory-usage.md` - Agent 使用场景

---

## 💡 提示

- 首次构建可能需要 10-20 分钟，请耐心等待
- 如果遇到网络问题，可以配置 Docker 镜像加速器
- 建议使用 `docker compose logs -f` 实时监控启动过程
- 生产环境建议使用 `docker compose up -d --scale <service>=<replicas>` 进行水平扩展
