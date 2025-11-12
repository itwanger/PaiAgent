# Java Workflow 开发指南

## 项目结构

```
core-workflow-java/
├── src/
│   ├── main/
│   │   ├── java/com/iflytek/astron/workflow/
│   │   │   ├── WorkflowApplication.java     # 启动类
│   │   │   ├── controller/                   # REST API
│   │   │   ├── service/                      # 业务服务
│   │   │   ├── engine/                       # 工作流引擎
│   │   │   ├── nodes/                        # 节点实现
│   │   │   ├── domain/                       # 领域模型
│   │   │   ├── entity/                       # 数据库实体
│   │   │   └── config/                       # 配置类
│   │   └── resources/
│   │       ├── application.yml               # 配置文件
│   │       └── mapper/                       # MyBatis Mapper XML
│   └── test/                                 # 测试代码
├── pom.xml                                   # Maven 配置
├── Dockerfile                                # Docker 镜像
└── README.md                                 # 本文件
```

## 快速开始

### 1. 环境准备

确保已安装：
- JDK 21（通过 jenv 管理）
- Maven 3.8+
- Docker & Docker Compose

### 2. 本地开发

```bash
# 设置 Java 版本
jenv local 21

# 编译项目
mvn clean package -DskipTests

# 本地运行（不使用 Docker）
java -jar target/workflow-java.jar

# 访问健康检查
curl http://localhost:7881/actuator/health
```

### 3. Docker 部署

```bash
# 方式 1：使用快速重启脚本（推荐）
./scripts/restart-java-workflow.sh

# 方式 2：手动构建和启动
cd docker/astronAgent
docker compose -f docker-compose.workflow-dual.yml --profile java-workflow up -d core-workflow-java

# 查看日志
docker logs -f astron-agent-core-workflow-java
```

## 开发流程

### 修改代码后快速重启

```bash
# 一键重启（自动编译、构建镜像、重启容器）
./scripts/restart-java-workflow.sh

# 如果遇到问题，立即切换到 Python 版本
./scripts/switch-to-python.sh
```

### 版本切换

```bash
# 切换到 Python 版本（稳定版）
./scripts/switch-to-python.sh

# 切换到 Java 版本（开发版）
./scripts/switch-to-java.sh

# 对比两个版本的输出
./scripts/compare-workflows.sh 184736
```

## 关键开发任务

### Phase 1: 基础框架（Day 1-3）

- [ ] DSL 解析器（WorkflowDSL, Node, Edge）
- [ ] 变量池（VariablePool）
- [ ] 节点基类（BaseNode）
- [ ] 顺序执行引擎（WorkflowEngine）

**参考 Python 代码：**
- `core/workflow/domain/entities/workflow_dsl.py`
- `core/workflow/engine/entities/variable_pool.py`
- `core/workflow/engine/dsl_engine.py`

### Phase 2: 节点实现（Day 4-7）

- [ ] StartNode（输入节点）
- [ ] EndNode（输出节点，模板渲染）
- [ ] LLMNode（调用模型管理 + DeepSeek API）
- [ ] PluginNode（调用超拟人合成工具）

**参考 Python 代码：**
- `core/workflow/engine/nodes/start/start_node.py`
- `core/workflow/engine/nodes/end/end_node.py`
- `core/workflow/engine/nodes/llm/spark_llm_node.py`
- `core/workflow/engine/nodes/plugin_tool/plugin_node.py`

### Phase 3: API 集成（Day 8-10）

- [ ] REST API Controller
- [ ] Service 层
- [ ] 数据库集成
- [ ] 测试

## 与 Python 版本对比

### 相同点
- DSL 数据结构完全一致
- 节点类型和执行逻辑相同
- API 接口规范一致

### 不同点
- 语言：Python → Java
- 框架：FastAPI → Spring Boot
- 端口：7880 → 7881
- 数据库：workflow_python → workflow_java

## 调试技巧

### 1. 查看 Python 版本实现

```bash
# 查看 Python 版本的日志
docker logs -f astron-agent-core-workflow-python

# 对比执行结果
./scripts/compare-workflows.sh 184736
```

### 2. 使用 Python 版本作为参考

当 Java 版本出现问题时：
1. 切换到 Python 版本：`./scripts/switch-to-python.sh`
2. 观察 Python 版本的行为
3. 对比日志和输出
4. 修复 Java 代码
5. 重启 Java 版本：`./scripts/restart-java-workflow.sh`

### 3. 常用命令

```bash
# 查看 Java 日志
docker logs -f astron-agent-core-workflow-java

# 查看 Python 日志
docker logs -f astron-agent-core-workflow-python

# 健康检查
curl http://localhost:7881/actuator/health  # Java
curl http://localhost:7880/health           # Python

# 进入容器调试
docker exec -it astron-agent-core-workflow-java sh
```

## 数据库

### 连接信息
- Host: localhost:3306
- Database: workflow_java
- Username: root
- Password: root123

### 重要表
- `workflow` - 工作流定义
- `workflow_execution` - 执行历史
- `node_execution_log` - 节点日志

## 注意事项

⚠️ **开发规则**
1. 始终保持 Python 版本可用（不要修改 Python 代码）
2. 遇到问题立即切换到 Python 版本参考
3. 每次代码修改后使用 `restart-java-workflow.sh` 重启
4. 定期使用 `compare-workflows.sh` 对比结果

✅ **最佳实践**
1. 先看 Python 代码实现逻辑
2. 在 Java 中实现相同逻辑
3. 使用对比脚本验证一致性
4. 发现问题查看 Python 日志

## 紧急回滚

如果 Java 版本出现严重问题：

```bash
# 一键回滚到 Python 版本
./scripts/switch-to-python.sh
```

## 相关文档

- [AGENTS.md](../AGENTS.md) - 项目整体开发指南
- [Makefile-readme.md](../docs/Makefile-readme.md) - 构建工具说明
- [Python Workflow 源码](../core/workflow/) - 参考实现
