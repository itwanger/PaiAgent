# Docker 服务重启指南

## 概述

在修改代码、配置文件或数据库schema后，需要重启Docker服务以确保所有变更生效，并避免出现缓存、IP地址不匹配等问题。

## 重启方案

### 方案1: 重启所有服务（推荐，保留数据）

**适用场景：**
- 修改了应用代码
- 修改了配置文件（如 nginx.conf, docker-compose.yaml 的环境变量等）
- 服务出现异常需要重启
- 出现IP地址不匹配、DNS缓存问题

**命令：**
```bash
cd /Users/itwanger/Documents/GitHub/PaiAgent/docker/astronAgent
docker compose restart
```

**特点：**
- ✅ 保留所有数据（数据库、MinIO存储等）
- ✅ 快速重启（不重新创建容器）
- ✅ 刷新服务间的DNS解析和网络连接
- ⏱️ 耗时：约30-60秒

---

### 方案2: 完全停止再启动（保留数据）

**适用场景：**
- 方案1重启后仍有问题
- 修改了 docker-compose.yaml 的服务配置（如端口、volumes等）
- 需要重建容器但保留数据

**命令：**
```bash
cd /Users/itwanger/Documents/GitHub/PaiAgent/docker/astronAgent
docker compose down
docker compose up -d
```

**特点：**
- ✅ 保留所有数据（数据库、MinIO存储等）
- ✅ 完全重建容器和网络
- ✅ 适用于docker-compose.yaml变更
- ⏱️ 耗时：约1-2分钟

---

### 方案3: 清空数据重建（慎用！）

**适用场景：**
- 修改了数据库初始化脚本（schema.sql等）
- 数据库损坏需要重新初始化
- 需要完全清空所有数据重新开始

**命令：**
```bash
cd /Users/itwanger/Documents/GitHub/PaiAgent/docker/astronAgent
docker compose down -v
docker compose up -d
```

**特点：**
- ⚠️ **会删除所有数据**（数据库数据、MinIO文件、Redis缓存等）
- ✅ 完全重建容器、网络和数据卷
- ✅ 重新执行数据库初始化脚本
- ⏱️ 耗时：约2-3分钟（包括数据库初始化）

---

## 常见场景对应方案

| 场景 | 推荐方案 | 说明 |
|------|---------|------|
| 修改前端/后端代码 | 方案1 | 重启服务即可 |
| 修改nginx配置 | 方案1 | 重启刷新配置 |
| 修改环境变量 | 方案1或2 | 简单变量用方案1，复杂配置用方案2 |
| 修改docker-compose.yaml服务定义 | 方案2 | 需要重建容器 |
| 修改schema.sql数据库脚本 | 方案3 | ⚠️ 会清空数据 |
| 出现502/503错误 | 方案1 | 先尝试重启 |
| DNS/IP不匹配问题 | 方案1 | 重启刷新网络 |
| 数据库表缺失/损坏 | 方案3 | ⚠️ 会清空数据 |

---

## 重启后验证

重启完成后，建议执行以下检查：

### 1. 检查所有服务状态
```bash
docker compose ps
```
确保所有服务状态为 `Up` 且有 `healthy` 标识。

### 2. 检查后端服务
```bash
curl http://localhost/console-api/actuator/health
```
应返回 `{"status":"UP"}`

### 3. 检查数据库表数量
```bash
docker exec astron-agent-mysql mysql -uroot -proot123 -e "SELECT COUNT(*) as table_count FROM information_schema.tables WHERE table_schema='astron_console';" 2>&1 | grep -v Warning
```
应显示 145 个表（正常值）

### 4. 访问前端
浏览器访问 http://localhost，使用 admin / 123 登录

---

## 故障排查

### 如果重启后仍有问题：

1. **查看服务日志：**
```bash
docker logs astron-agent-console-hub --tail 50
docker logs astron-agent-nginx --tail 50
docker logs astron-agent-mysql --tail 50
```

2. **检查网络连接：**
```bash
docker exec astron-agent-nginx ping -c 3 console-hub
```

3. **检查容器IP地址：**
```bash
docker inspect astron-agent-console-hub --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}'
```

---

## 注意事项

⚠️ **重要提醒：**
- 使用方案3前请确认数据已备份或可以丢失
- 生产环境禁止使用方案3（会丢失所有数据）
- 如果不确定，优先使用方案1
- 修改数据库初始化脚本后，必须使用方案3才能生效

✅ **最佳实践：**
- 代码修改后优先使用方案1
- 遇到问题先尝试方案1，无效再用方案2
- 只在必须重建数据库时才使用方案3
