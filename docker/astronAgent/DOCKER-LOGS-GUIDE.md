# Docker 日志查看指南

## 🔍 查看 Docker 所有日志

### 1. 查看所有服务的实时日志
```bash
cd docker/astronAgent
docker compose -f docker-compose-with-auth.yaml logs -f
```

### 2. 查看特定服务的日志
```bash
# 查看 console-hub 日志
docker compose -f docker-compose-with-auth.yaml logs -f console-hub

# 查看最近 100 行日志
docker compose -f docker-compose-with-auth.yaml logs --tail=100 console-hub

# 查看所有错误日志
docker compose -f docker-compose-with-auth.yaml logs | grep -i error
```

### 3. 查看多个服务的日志
```bash
# 同时查看 console-hub 和 core-workflow
docker compose -f docker-compose-with-auth.yaml logs -f console-hub core-workflow
```

### 4. 查看所有服务状态
```bash
docker compose -f docker-compose-with-auth.yaml ps
```

### 5. 查看容器详细日志
```bash
# 使用容器名直接查看
docker logs -f astron-agent-console-hub

# 查看最近 50 行
docker logs --tail=50 astron-agent-console-hub

# 查看带时间戳的日志
docker logs --timestamps astron-agent-console-hub
```

### 6. 搜索特定错误
```bash
# 搜索所有包含 ERROR 的日志
docker compose -f docker-compose-with-auth.yaml logs | grep ERROR

# 搜索 SQL 错误
docker compose -f docker-compose-with-auth.yaml logs console-hub | grep -i "sql"

# 搜索 500 错误
docker compose -f docker-compose-with-auth.yaml logs nginx | grep "500"

# 搜索 NullPointerException
docker compose -f docker-compose-with-auth.yaml logs console-hub | grep -i "NullPointerException"
```

### 7. 导出日志到文件
```bash
# 导出所有日志
docker compose -f docker-compose-with-auth.yaml logs > /tmp/all-logs.txt

# 导出 console-hub 日志
docker compose -f docker-compose-with-auth.yaml logs console-hub > /tmp/console-hub.txt

# 导出错误日志
docker compose -f docker-compose-with-auth.yaml logs 2>&1 | grep -i error > /tmp/errors.txt
```

### 8. 实时监控多个服务
```bash
# 使用 tail 实时查看多个日志文件
docker compose -f docker-compose-with-auth.yaml logs -f console-hub core-workflow core-agent | grep -E "ERROR|WARN|Exception"
```

### 9. 按时间范围查看日志
```bash
# 查看最近 1 小时的日志
docker compose -f docker-compose-with-auth.yaml logs --since 1h console-hub

# 查看指定时间之后的日志
docker compose -f docker-compose-with-auth.yaml logs --since "2025-11-14T10:00:00" console-hub

# 查看最近 10 分钟的日志
docker compose -f docker-compose-with-auth.yaml logs --since 10m
```

### 10. 常用服务名称列表
```bash
# 核心服务
console-hub         # Java 后端
console-frontend    # React 前端
core-workflow       # Python 工作流引擎
core-agent          # Python Agent 服务
core-tenant         # Go 租户服务
core-aitools        # Python AI 工具服务
core-link           # Python Link 插件
core-rpa            # Python RPA 插件
core-database       # Python 数据库服务
core-knowledge      # Python 知识库服务

# 认证服务
casdoor             # OAuth2 认证服务
casdoor-mysql       # Casdoor 数据库

# 基础设施
nginx               # 反向代理
mysql               # MySQL 数据库
postgres            # PostgreSQL 数据库
redis               # Redis 缓存
minio               # MinIO 对象存储
kafka               # Kafka 消息队列
elasticsearch       # Elasticsearch 搜索引擎
```

## 📝 常见问题排查

### 问题1: 500 错误
```bash
# 查看 nginx 日志找到 500 错误
docker compose -f docker-compose-with-auth.yaml logs nginx | grep "500"

# 查看 console-hub 详细错误
docker compose -f docker-compose-with-auth.yaml logs console-hub | grep -A 20 "ERROR"
```

### 问题2: 数据库连接失败
```bash
# 查看数据库是否启动
docker compose -f docker-compose-with-auth.yaml ps mysql postgres

# 查看数据库日志
docker compose -f docker-compose-with-auth.yaml logs mysql
docker compose -f docker-compose-with-auth.yaml logs postgres
```

### 问题3: 认证失败
```bash
# 查看 Casdoor 日志
docker compose -f docker-compose-with-auth.yaml logs casdoor

# 查看 console-hub OAuth2 相关日志
docker compose -f docker-compose-with-auth.yaml logs console-hub | grep -i "oauth\|jwt\|token"
```

### 问题4: 工作流执行失败
```bash
# 查看工作流引擎日志
docker compose -f docker-compose-with-auth.yaml logs core-workflow

# 查看所有 Python 服务日志
docker compose -f docker-compose-with-auth.yaml logs core-workflow core-agent core-aitools core-link
```

### 问题5: 超拟人合成节点执行失败 (Node execution failed / Plugin node execution failed)
```bash
# 现象: 工作流中超拟人合成节点报错 "Node execution failed" 或 "Plugin node execution failed"
# 原因1: tools_schema 表中工具配置错误 (version/app_id)
# 原因2: open_api_schema 中使用了 https 而不是 http

# 1. 查看 core-link 日志
docker compose -f docker-compose-with-auth.yaml logs core-link | tail -100

# 如果看到: "Tool does not exist: tool@8b2262bef821000 V1.0 does not exist"
# 执行以下修复:
docker compose -f docker-compose-with-auth.yaml exec mysql mysql -uroot -proot123 spark-link -e \
  "UPDATE tools_schema SET version='V1.0', app_id='680ab54f' WHERE tool_id='tool@8b2262bef821000'"

# 如果看到: "Cannot connect to host core-aitools:18668 ssl:default [[SSL] record layer failure]"
# 说明 open_api_schema 中使用了 https，需要改为 http:
docker compose -f docker-compose-with-auth.yaml exec mysql mysql -uroot -proot123 spark-link -e \
  "UPDATE tools_schema SET open_api_schema = REPLACE(open_api_schema, 'https://core-aitools:18668', 'http://core-aitools:18668') WHERE tool_id='tool@8b2262bef821000'"

# 2. 检查数据库中工具配置
docker compose -f docker-compose-with-auth.yaml exec mysql mysql -uroot -proot123 spark-link -e \
  "SELECT tool_id, name, version, app_id FROM tools_schema WHERE tool_id='tool@8b2262bef821000'"

# 3. 检查缺失的表 (如果看到 "Table 'astron_console.workflow_config' doesn't exist")
docker compose -f docker-compose-with-auth.yaml exec mysql mysql -uroot -proot123 astron_console -e \
  "CREATE TABLE IF NOT EXISTS workflow_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    workflow_id BIGINT NOT NULL,
    config TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流配置表';"

# 4. 重启相关服务
docker compose -f docker-compose-with-auth.yaml restart core-link core-workflow

# 5. 刷新浏览器重新执行工作流
```

## 🛠️ 高级技巧

### 使用 jq 解析 JSON 日志
```bash
# 如果日志是 JSON 格式
docker compose -f docker-compose-with-auth.yaml logs core-workflow --tail=100 | grep "{" | jq .
```

### 使用 watch 实时监控
```bash
# 每 2 秒刷新服务状态
watch -n 2 'docker compose -f docker/astronAgent/docker-compose-with-auth.yaml ps'
```

### 快速定位最新错误
```bash
# 查看最近 5 分钟的错误
docker compose -f docker-compose-with-auth.yaml logs --since 5m | grep -i "error\|exception\|failed" | tail -50
```
