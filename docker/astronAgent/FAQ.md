# 常见问题 FAQ

## 目录
- [部署与启动问题](#部署与启动问题)
- [认证与登录问题](#认证与登录问题)
- [数据库问题](#数据库问题)
- [网络与连接问题](#网络与连接问题)
- [服务异常问题](#服务异常问题)

---

## 部署与启动问题

### Q1: 如何一键启动所有服务（包括 Casdoor OAuth 认证）？

**推荐方法：使用启动脚本**
```bash
cd docker/astronAgent
./start-with-auth.sh
```

**手动启动：**
```bash
cd docker/astronAgent
docker compose -f docker-compose-with-auth.yaml up -d
```

**启动后访问：**
- 🌐 前端应用: http://localhost
- 🔐 Casdoor 控制台: http://localhost:8000
- 👤 默认账号: admin / 123

**首次启动配置：**
1. 访问 http://localhost:8000
2. 使用 admin / 123 登录 Casdoor
3. 确认 astronAgent 应用已创建
4. 验证回调地址: http://localhost/callback

---

### Q2: 如何启动不带 Casdoor 的简化版本（本地开发）？

**适用场景：** 本地开发调试，不需要 OAuth 认证

```bash
cd docker/astronAgent
docker compose up -d
```

此模式使用 `MockUserFilter` 自动注入 admin 用户，**无需登录**。

---

### Q3: 如何选择正确的重启方案？

参考 `RESTART-GUIDE.md` 文档，根据不同场景选择：

- **方案1**（推荐）：代码或配置修改后使用 `docker compose restart`
- **方案2**：docker-compose.yaml 服务配置变更后使用 `docker compose down && docker compose up -d`
- **方案3**（慎用）：数据库初始化脚本变更后使用 `docker compose down -v && docker compose up -d`

### Q2: 修改了 schema.sql 后需要做什么？

**完整步骤：**

1. 停止并删除所有容器和数据卷
```bash
cd /Users/itwanger/Documents/GitHub/PaiAgent/docker/astronAgent
docker compose down -v
```

2. 重新启动所有服务
```bash
docker compose up -d
```

3. 等待所有服务启动完成（约2-3分钟）
```bash
docker compose ps
```

4. **重要：必须重启nginx刷新DNS缓存**
```bash
docker compose restart nginx
```

5. 验证数据库表数量
```bash
docker exec astron-agent-mysql mysql -uroot -proot123 -e "SELECT COUNT(*) as table_count FROM information_schema.tables WHERE table_schema='astron_console';" 2>&1 | grep -v Warning
```
应显示 145 个表

---

## 数据库问题

### Q3: 出现 "Table 'astron_console.xxx' doesn't exist" 错误怎么办？

**原因：** 数据库初始化失败或表结构不完整

**解决方案：**

1. 检查 MySQL 初始化日志
```bash
docker logs astron-agent-mysql 2>&1 | grep -i "error\|fail"
```

2. 检查 schema.sql 语法是否正确
```bash
grep -n "ERROR\|FAIL" docker/astronAgent/mysql/schema.sql
```

3. 如果发现 SQL 语法错误，修复后重建数据库（方案3）

### Q4: schema.sql 出现 "Duplicate column name" 错误？

**常见原因：** schema.sql 中同一表定义了重复字段

**排查方法：**
```bash
# 查找重复的 type 字段定义
grep -n '`type`.*int.*DEFAULT.*COMMENT.*Workflow type' docker/astronAgent/mysql/schema.sql
```

**解决方案：**
1. 从 Git 恢复正确版本的 schema.sql
2. 或手动删除重复的字段定义
3. 执行方案3重建数据库

### Q5: MySQL 初始化后表数量不正确？

**正常表数量：**
- `astron_console`: 145 个表
- `spark-link`: 若干个表（工具相关）
- `agent`: 若干个表

**如果表数量为 0 或过少：**
1. 检查是否执行了 `docker compose down -v`（会删除所有数据）
2. 检查 schema.sql 是否有语法错误
3. 使用方案3重建数据库

---

## 网络与连接问题

### Q6: 前端显示 "服务器开小差了~稍后再试" 或 502 Bad Gateway？

**最常见原因：** nginx DNS 缓存问题，连接了旧的容器IP地址

**诊断方法：**

1. 查看 nginx 错误日志
```bash
docker logs astron-agent-nginx 2>&1 | tail -30
```

2. 如果看到类似错误：
```
connect() failed (113: Host is unreachable) while connecting to upstream
upstream: "http://172.19.0.12:8080/..."
```

3. 检查 console-hub 实际IP地址
```bash
docker inspect astron-agent-console-hub --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}'
```

4. 如果IP地址不匹配，说明是DNS缓存问题

**解决方案：**
```bash
cd /Users/itwanger/Documents/GitHub/PaiAgent/docker/astronAgent
docker compose restart nginx
```

等待5-10秒后刷新浏览器页面。

**预防措施：**

⚠️ **重要：每次重建容器后（docker compose down -v 或 docker compose down），必须重启nginx**

```bash
# 标准流程
docker compose down -v
docker compose up -d
sleep 30  # 等待服务启动
docker compose restart nginx  # 刷新nginx DNS缓存
```

### Q7: 后端服务启动正常但前端无法访问？

**检查步骤：**

1. 验证后端健康检查
```bash
curl http://localhost/console-api/actuator/health
```
应返回 `{"status":"UP"}`

2. 如果返回 502 错误，重启nginx
```bash
docker compose restart nginx
```

3. 检查所有服务状态
```bash
docker compose ps
```
确保所有服务都是 `healthy` 状态

---

## 服务异常问题

### Q8: Redis 报 "NOSCRIPT No matching script" 错误？

**错误日志示例：**
```
org.redisson.client.RedisException: NOSCRIPT No matching script. Please use EVAL
```

**原因：** Redis 重启后 Lua 脚本缓存被清空，Redisson 首次使用 EVALSHA 失败

**是否需要处理：**
- ❌ **这是正常现象**，Redisson 会自动重试使用 EVAL 命令
- ✅ 只是一次性的 ERROR 日志，不影响功能
- ✅ 后续请求会自动恢复正常

**如果想避免此日志（可选）：**
```bash
cd /Users/itwanger/Documents/GitHub/PaiAgent/docker/astronAgent
docker exec astron-agent-redis redis-cli FLUSHALL
docker compose restart console-hub
```

### Q9: 如何查看各服务的日志？

**常用日志命令：**

```bash
# 查看后端服务日志
docker logs astron-agent-console-hub --tail 50

# 查看 MySQL 日志
docker logs astron-agent-mysql --tail 50

# 查看 nginx 日志
docker logs astron-agent-nginx --tail 50

# 查看工作流引擎日志
docker logs astron-agent-core-workflow --tail 50

# 查看工具插件日志
docker logs astron-agent-core-aitools --tail 50

# 实时跟踪日志
docker logs -f astron-agent-console-hub

# 查看所有服务日志
docker compose logs --tail 20
```

### Q10: 服务状态显示 "unhealthy" 怎么办？

**检查步骤：**

1. 查看具体哪个服务 unhealthy
```bash
docker compose ps
```

2. 查看该服务的健康检查日志
```bash
docker inspect <容器名> --format='{{json .State.Health}}'
```

3. 查看服务日志排查原因
```bash
docker logs <容器名> --tail 100
```

4. 常见原因和解决方案：
   - **MySQL**: 数据库初始化失败 → 检查 schema.sql
   - **console-hub**: 数据库连接失败 → 重启服务
   - **nginx**: 无法连接上游服务 → 重启 nginx
   - **Redis/Postgres**: 端口冲突 → 检查端口占用

---

## 配置修改问题

### Q11: 如何修改工作流节点显示？

**方法：修改 SPACE_SWITCH_NODE 配置**

1. 查看当前配置
```bash
docker exec astron-agent-mysql mysql -uroot -proot123 astron_console -e "SELECT id, category, value FROM config_info WHERE category='SPACE_SWITCH_NODE';" 2>&1 | grep -v Warning
```

2. 理解过滤逻辑（**注意：是黑名单，不是白名单**）
   - `value` 中列出的节点会被**隐藏**
   - 未列出的节点会**显示**

3. 修改配置（两种方式）

**方式1：直接修改数据库**
```bash
docker exec astron-agent-mysql mysql -uroot -proot123 astron_console -e "
UPDATE config_info 
SET value = 'ifly-code,knowledge-base,flow,decision-making,if-else,iteration,node-variable,extractor-parameter,text-joiner,message,agent,question-answer,database,rpa,knowledge-pro-base' 
WHERE category = 'SPACE_SWITCH_NODE';
"
docker compose restart console-hub
```

**方式2：修改 schema.sql 后重建**
```bash
# 1. 编辑 docker/astronAgent/mysql/schema.sql
# 找到 SPACE_SWITCH_NODE 这一行，修改 value 字段

# 2. 重建数据库
docker compose down -v
docker compose up -d
docker compose restart nginx
```

4. 刷新浏览器查看效果

### Q12: 修改配置文件后不生效怎么办？

**配置文件类型和对应重启方案：**

| 配置文件 | 位置 | 重启方案 |
|---------|------|----------|
| `docker-compose.yaml` | docker/astronAgent/ | 方案2 |
| `nginx.conf` | docker/astronAgent/nginx/ | 重启nginx |
| `schema.sql` | docker/astronAgent/mysql/ | 方案3 |
| `config.env` | docker/astronAgent/config/*/  | 方案1 |
| 后端代码 | console/backend/ | 重新build + 方案2 |
| 前端代码 | console/frontend/ | 重新build + 方案2 |

---

## 数据备份与恢复

### Q13: 如何备份数据？

**备份 MySQL 数据：**
```bash
docker exec astron-agent-mysql mysqldump -uroot -proot123 --all-databases > backup-$(date +%Y%m%d).sql
```

**备份 MinIO 对象存储：**
```bash
docker exec astron-agent-minio mc mirror /data/console-oss ./backup-minio-$(date +%Y%m%d)/
```

**备份 PostgreSQL 数据：**
```bash
docker exec astron-agent-postgres pg_dumpall -U postgres > backup-postgres-$(date +%Y%m%d).sql
```

### Q14: 如何恢复数据？

**恢复 MySQL：**
```bash
docker exec -i astron-agent-mysql mysql -uroot -proot123 < backup-20251118.sql
docker compose restart console-hub
```

**恢复后建议重启所有服务：**
```bash
docker compose restart
```

---

## 性能优化

### Q15: 如何提高 Docker 构建速度？

**使用构建缓存：**
```bash
# 正常构建（使用缓存，1-3分钟）
docker compose build

# 清除缓存重新构建（15-30分钟）
docker compose build --no-cache
```

**只重建特定服务：**
```bash
docker compose build console-hub
docker compose up -d console-hub
```

### Q16: 如何减少容器占用的磁盘空间？

**清理未使用的镜像和容器：**
```bash
# 清理停止的容器
docker container prune

# 清理未使用的镜像
docker image prune -a

# 清理未使用的数据卷（⚠️ 慎用，会删除数据）
docker volume prune

# 一键清理所有未使用资源
docker system prune -a --volumes
```

---

## 故障排查流程

### Q17: 遇到问题应该按什么顺序排查？

**标准排查流程：**

1️⃣ **查看服务状态**
```bash
docker compose ps
```

2️⃣ **查看具体服务日志**
```bash
docker logs astron-agent-console-hub --tail 100
docker logs astron-agent-nginx --tail 50
docker logs astron-agent-mysql --tail 50
```

3️⃣ **检查网络连接**
```bash
docker inspect astron-agent-console-hub --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}'
curl http://localhost/console-api/actuator/health
```

4️⃣ **检查数据库状态**
```bash
docker exec astron-agent-mysql mysql -uroot -proot123 -e "SHOW DATABASES;"
docker exec astron-agent-mysql mysql -uroot -proot123 astron_console -e "SHOW TABLES;" | wc -l
```

5️⃣ **尝试重启相关服务**
```bash
# 先尝试重启出问题的服务
docker compose restart console-hub

# 如果不行，重启 nginx
docker compose restart nginx

# 最后才考虑重启所有服务
docker compose restart
```

6️⃣ **如果以上都不行，考虑重建**
```bash
docker compose down
docker compose up -d
docker compose restart nginx
```

---

## 联系与反馈

如果以上方案都无法解决您的问题，请：

1. 收集以下信息：
   - 错误截图或日志
   - `docker compose ps` 输出
   - 相关服务的日志（最近100行）
   - 您执行的操作步骤

2. 提交 Issue 或联系技术支持

---

## 快速参考

**🚀 推荐：使用智能重启脚本**

一键解决所有重启问题（自动等待服务启动 + 自动刷新nginx DNS）：

```bash
cd /Users/itwanger/Documents/GitHub/PaiAgent/docker/astronAgent

# 快速重启（最常用）
./smart-restart.sh

# 重建容器
./smart-restart.sh rebuild

# 完全重建（删除数据）
./smart-restart.sh full
```

---

**最常用的命令（手动操作）：**

```bash
# 查看所有服务状态
docker compose ps

# 查看服务日志
docker logs astron-agent-console-hub --tail 50

# 重启单个服务
docker compose restart console-hub

# 重启所有服务（保留数据）⚠️ 之后必须重启nginx
docker compose restart
sleep 15
docker compose restart nginx

# 重建所有服务（保留数据）⚠️ 之后必须重启nginx
docker compose down && docker compose up -d
sleep 20
docker compose restart nginx

# 完全重建（⚠️ 删除所有数据）⚠️ 之后必须重启nginx
docker compose down -v && docker compose up -d
sleep 30
docker compose restart nginx

# 检查后端健康
curl http://localhost/console-api/actuator/health

# 检查数据库表数量
docker exec astron-agent-mysql mysql -uroot -proot123 -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='astron_console';" 2>&1 | grep -v Warning
```

**记住：修改 schema.sql 或使用 docker compose down -v 后，一定要重启 nginx！**
