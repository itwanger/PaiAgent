# Workflow版本切换关键配置记录

## 问题总结

在切换Python/Java workflow版本时遇到的关键问题和解决方案。

## 关键配置点

### 1. 环境变量配置 (.env文件)

**文件位置**: `docker/astronAgent/.env`

**关键变量**:
```bash
CORE_WORKFLOW_PORT=7880   # Python版本
CORE_WORKFLOW_PORT=7881   # Java版本
```

所有workflow相关URL都依赖这个变量:
```bash
WORKFLOW_CHAT_URL=http://core-workflow:${CORE_WORKFLOW_PORT:-7880}/workflow/v1/chat/completions
WORKFLOW_DEBUG_URL=http://core-workflow:${CORE_WORKFLOW_PORT:-7880}/workflow/v1/debug/chat/completions
WORKFLOW_URL=http://core-workflow:${CORE_WORKFLOW_PORT:-7880}
```

### 2. 容器环境变量

**Python Workflow容器**:
- 环境变量: `SERVICE_PORT=7880`
- 容器名: `astron-agent-core-workflow`
- 网络内部端口: 7880 (不对外暴露)

**Java Workflow容器**:
- 环境变量: `MYSQL_DB=astron_console` (注意不是workflow!)
- 容器名: `astron-agent-core-workflow-java`
- 端口映射: `0.0.0.0:7881->7881/tcp`

**Console-Hub容器**:
- 环境变量从 `.env` 文件读取
- 需要重启才能生效!

### 3. 切换步骤的关键点

#### ❌ 之前错误的方式:
```bash
# 1. 修改 .env 中的 CORE_WORKFLOW_PORT
# 2. 只重启 workflow 容器
# 3. 只 restart console-hub
```

**问题**: `docker compose restart` 不会重新加载环境变量!

#### ✅ 正确的切换方式:

**切换到Python (7880)**:
```bash
# 1. 停止Java版本
docker compose -f docker-compose.workflow-dual.yml stop core-workflow-java

# 2. 启动/重启Python版本
docker compose stop core-workflow
docker compose rm -f core-workflow
docker compose up -d core-workflow

# 3. 修改.env
sed -i.bak 's/^CORE_WORKFLOW_PORT=7881/CORE_WORKFLOW_PORT=7880/' .env

# 4. 重建console-hub容器(关键!)
docker compose stop console-hub
docker compose rm -f console-hub
docker compose up -d console-hub
```

**切换到Java (7881)**:
```bash
# 1. 停止Python版本
docker compose stop core-workflow

# 2. 启动Java版本
docker compose -f docker-compose.workflow-dual.yml --profile java-workflow up -d core-workflow-java

# 3. 修改.env
sed -i.bak 's/^CORE_WORKFLOW_PORT=7880/CORE_WORKFLOW_PORT=7881/' .env

# 4. 重建console-hub容器(关键!)
docker compose stop console-hub
docker compose rm -f console-hub
docker compose up -d console-hub
```

### 4. 验证切换成功

```bash
# 检查.env配置
grep CORE_WORKFLOW_PORT docker/astronAgent/.env

# 检查console-hub环境变量
docker exec astron-agent-console-hub env | grep WORKFLOW_CHAT_URL

# 检查Python workflow端口
docker exec astron-agent-core-workflow env | grep SERVICE_PORT

# 测试连通性
docker exec astron-agent-console-hub wget -O- http://core-workflow:7880/health  # Python
docker exec astron-agent-console-hub wget -O- http://core-workflow:7881/actuator/health  # Java
```

## 教训总结

### 关键发现

1. **docker compose restart 不会重新加载 .env 文件的环境变量**
   - 必须使用 `rm -f` + `up -d` 来重建容器

2. **console-hub是关键中间层**
   - 前端 → Nginx → console-hub → workflow
   - console-hub的环境变量必须正确指向对应版本

3. **Python workflow 环境变量问题**
   - 第一次启动时 SERVICE_PORT 被错误设置为 7881
   - 需要重建容器才能正确设置为 7880

4. **脚本已修复但需注意**
   - `switch-to-python.sh` 和 `switch-to-java.sh` 已更新
   - 现在会自动修改 .env 并重建 console-hub

## 今天遇到的具体问题

**症状**: 切换到Python版本后无法执行
**原因**: Python workflow容器的 SERVICE_PORT=7881 (错误的端口)
**解决**: 
```bash
docker compose stop core-workflow
docker compose rm -f core-workflow
docker compose up -d core-workflow
# 这样重建后, SERVICE_PORT 正确读取为 7880
```

## 自动化脚本改进点

已更新的脚本:
- `scripts/switch-to-python.sh` ✅
- `scripts/switch-to-java.sh` ✅
- `scripts/restart-java-workflow.sh` ✅

关键改进:
```bash
# 重建console-hub而不是restart
docker compose stop console-hub
docker compose rm -f console-hub
docker compose up -d console-hub
```

## 数据库配置差异

**Python版本**:
- 数据库: `workflow`
- 表: `flow`
- 数据结构: `{"data": {"nodes": [...], "edges": [...]}}`

**Java版本**:
- 数据库: `astron_console`
- 表: `workflow`
- 数据结构: `{"nodes": [...], "edges": [...]}`

## 记住这个口诀

**切换workflow版本三部曲:**
1. 改 `.env` 文件的 `CORE_WORKFLOW_PORT`
2. 重建 workflow 容器 (`rm -f` + `up -d`)
3. 重建 console-hub 容器 (`rm -f` + `up -d`)

**千万不要只用 `restart`!**
