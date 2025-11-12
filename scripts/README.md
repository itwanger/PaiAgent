# 核心脚本使用指南

## 快速参考

### 🚀 Java Workflow 开发

```bash
# 修改代码后快速重启（自动编译、构建镜像、重启容器）
./scripts/restart-java-workflow.sh

# 功能：
# 1. 检查 jenv 和 JDK 21
# 2. 使用 Maven 编译打包
# 3. 重新构建 Docker 镜像
# 4. 重启容器
# 5. 健康检查
```

**适用场景：**
- 修改了 Java 代码
- 修改了配置文件
- 需要查看最新效果

---

### 🔄 版本切换

```bash
# 切换到 Python 版本（稳定）
./scripts/switch-to-python.sh

# 切换到 Java 版本（开发中）
./scripts/switch-to-java.sh
```

**使用时机：**
- Java 版本出错时，切换到 Python 版本继续工作
- 需要参考 Python 版本的实现
- 对比两个版本的行为

---

### 📊 对比测试

```bash
# 同时测试两个版本，对比输出
./scripts/compare-workflows.sh 184736

# 参数：workflow_id（工作流ID）
```

**输出文件：**
- `/tmp/python-workflow-response.json` - Python 版本响应
- `/tmp/java-workflow-response.json` - Java 版本响应

---

## 典型开发流程

### 场景 1: 开发新功能

```bash
# 1. 查看 Python 版本实现（参考）
./scripts/switch-to-python.sh
docker logs -f astron-agent-core-workflow-python

# 2. 在 core-workflow-java/ 中编写 Java 代码

# 3. 编译并重启 Java 版本
./scripts/restart-java-workflow.sh

# 4. 测试新功能
curl -X POST http://localhost:7881/workflow/v1/execute \
  -H "Content-Type: application/json" \
  -d '{"workflowId": "184736", "inputs": {...}}'

# 5. 对比两个版本
./scripts/compare-workflows.sh 184736
```

---

### 场景 2: 调试错误

```bash
# 1. Java 版本出错
./scripts/restart-java-workflow.sh
# 发现错误...

# 2. 立即切换到 Python 版本
./scripts/switch-to-python.sh

# 3. 观察 Python 版本的正确行为
docker logs -f astron-agent-core-workflow-python

# 4. 修复 Java 代码

# 5. 重新测试
./scripts/restart-java-workflow.sh
```

---

### 场景 3: 性能对比

```bash
# 1. 启动两个版本（同时运行）
cd docker/astronAgent
docker compose -f docker-compose.workflow-dual.yml up -d core-workflow-python
docker compose -f docker-compose.workflow-dual.yml --profile java-workflow up -d core-workflow-java

# 2. 对比测试
./scripts/compare-workflows.sh 184736

# 3. 查看详细响应
cat /tmp/python-workflow-response.json
cat /tmp/java-workflow-response.json
```

---

## 脚本详解

### restart-java-workflow.sh

**完整流程：**
1. ✅ 检查 jenv 和 JDK 21
2. 🧹 清理旧的构建产物（`mvn clean`）
3. 📦 编译打包（`mvn package -DskipTests`）
4. 🛑 停止旧容器
5. 🏗️ 重新构建 Docker 镜像
6. 🚀 启动新容器
7. ❤️ 健康检查（最多等待 60 秒）

**输出示例：**
```
[1/6] 检查 Java 环境...
✓ jenv 已安装
✓ 已设置 JDK 21
✓ Java 版本: openjdk version "21.0.1"

[2/6] 清理旧的构建产物...
✓ 清理完成

[3/6] 编译打包 Java 项目...
正在执行: mvn package -DskipTests
✓ 编译成功: target/workflow-java.jar

[4/6] 停止旧的 Java Workflow 容器...
✓ 旧容器已停止

[5/6] 重新构建 Docker 镜像...
✓ 镜像构建成功

[6/6] 启动新的 Java Workflow 容器...
等待服务启动...
✓ 服务启动成功！

========================================
  Java Workflow 已成功重启！
========================================

服务信息：
  端口: 7881
  健康检查: http://localhost:7881/actuator/health
  日志: docker logs -f astron-agent-core-workflow-java
```

**错误处理：**
- 编译失败 → 提示切换到 Python 版本
- 容器启动失败 → 显示紧急回滚命令

---

### switch-to-python.sh

**功能：**
1. 停止 Java Workflow
2. 启动 Python Workflow
3. 更新环境变量

**使用时机：**
- Java 版本出错需要参考
- 需要使用稳定版本
- 对比 Python 实现

---

### switch-to-java.sh

**功能：**
1. 停止 Python Workflow
2. 启动 Java Workflow
3. 更新环境变量

**使用时机：**
- 切换回 Java 开发
- 测试 Java 新功能

---

### compare-workflows.sh

**功能：**
1. 检查两个服务状态
2. 发送相同请求到两个版本
3. 保存响应到文件
4. 使用 diff 对比差异

**参数：**
- `workflow_id` - 要测试的工作流 ID

**输出文件位置：**
```bash
/tmp/python-workflow-response.json
/tmp/java-workflow-response.json
```

---

## 环境要求

### JDK 配置

```bash
# 安装 jenv（如果未安装）
brew install jenv

# 添加 JDK 21 到 jenv
jenv add /path/to/jdk-21

# 在项目目录设置 Java 21
cd core-workflow-java
jenv local 21

# 验证版本
java -version
# 输出: openjdk version "21..."
```

### Maven 配置

确保使用 JDK 21 编译：
```xml
<!-- pom.xml 中已配置 -->
<properties>
    <java.version>21</java.version>
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
</properties>
```

---

## 故障排查

### 问题 1: 编译失败

**错误信息：**
```
✗ 编译失败！请检查代码错误
```

**解决方法：**
1. 查看完整错误信息：`mvn package`
2. 检查代码语法错误
3. 确认 JDK 版本：`java -version`
4. 临时使用 Python 版本：`./scripts/switch-to-python.sh`

---

### 问题 2: 容器启动失败

**错误信息：**
```
✗ 服务启动失败或超时！
```

**解决方法：**
```bash
# 查看容器日志
docker logs astron-agent-core-workflow-java

# 检查端口占用
lsof -i :7881

# 紧急回滚
./scripts/switch-to-python.sh
```

---

### 问题 3: 健康检查失败

**错误信息：**
```
⏳ 等待服务启动... (30/30)
✗ 服务启动失败或超时！
```

**排查步骤：**
```bash
# 1. 查看日志
docker logs astron-agent-core-workflow-java

# 2. 手动测试健康检查
curl http://localhost:7881/actuator/health

# 3. 检查数据库连接
docker exec -it astron-agent-core-workflow-java sh
# 在容器内：curl http://mysql:3306

# 4. 回滚到 Python
./scripts/switch-to-python.sh
```

---

## 最佳实践

### ✅ 推荐做法

1. **每次修改代码后都重启**
   ```bash
   # 修改代码...
   ./scripts/restart-java-workflow.sh
   ```

2. **遇到问题立即切换到 Python**
   ```bash
   # Java 出错...
   ./scripts/switch-to-python.sh
   # 查看 Python 实现...
   # 修复 Java 代码...
   ./scripts/restart-java-workflow.sh
   ```

3. **定期对比两个版本**
   ```bash
   ./scripts/compare-workflows.sh 184736
   ```

### ❌ 避免做法

1. ❌ 不要直接修改 Python 代码（保持参考基线）
2. ❌ 不要手动编译和重启（使用脚本自动化）
3. ❌ 不要忽略健康检查失败（可能导致数据不一致）

---

## 快速命令速查

```bash
# 开发
./scripts/restart-java-workflow.sh        # 重启 Java 版本

# 切换
./scripts/switch-to-python.sh            # 切到 Python
./scripts/switch-to-java.sh              # 切到 Java

# 对比
./scripts/compare-workflows.sh 184736    # 对比测试

# 日志
docker logs -f astron-agent-core-workflow-java    # Java 日志
docker logs -f astron-agent-core-workflow-python  # Python 日志

# 健康检查
curl http://localhost:7881/actuator/health  # Java
curl http://localhost:7880/health           # Python
```

---

## 总结

这套脚本的核心价值：

✅ **快速开发** - 一键重启，代码立即生效  
✅ **安全可靠** - Python 版本始终可用作为参考  
✅ **方便对比** - 随时对比两个版本的行为  
✅ **自动化** - 编译、构建、重启全自动  
✅ **故障恢复** - 遇到问题立即回滚  

**记住这个原则：Java 版本可以随便改，Python 版本永远是稳定的参考基线！** 🚀
