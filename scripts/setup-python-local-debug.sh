#!/bin/bash

# Python 服务本地调试环境准备脚本
# 用途: 配置 core 下的 Python 服务(agent、workflow、link、aitools)的本地调试环境
# 前提: Docker 中的基础设施服务(MySQL、PostgreSQL、Redis、MinIO)已启动

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DOCKER_DIR="$PROJECT_ROOT/docker/astronAgent"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Python 服务本地调试环境准备${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 检查 Docker 基础设施服务是否运行
echo -e "${YELLOW}[1/5] 检查 Docker 基础设施服务...${NC}"

REQUIRED_SERVICES=("astron-agent-mysql" "astron-agent-postgres" "astron-agent-redis" "astron-agent-minio")
MISSING_SERVICES=()

for service in "${REQUIRED_SERVICES[@]}"; do
    if docker ps | grep -q "$service"; then
        echo -e "${GREEN}✓ $service 正在运行${NC}"
    else
        echo -e "${RED}✗ $service 未运行${NC}"
        MISSING_SERVICES+=("$service")
    fi
done

if [ ${#MISSING_SERVICES[@]} -ne 0 ]; then
    echo -e "${RED}错误: 以下基础设施服务未运行:${NC}"
    for service in "${MISSING_SERVICES[@]}"; do
        echo -e "${RED}  - $service${NC}"
    done
    echo ""
    echo -e "${YELLOW}请先启动基础设施服务:${NC}"
    echo -e "${CYAN}cd $DOCKER_DIR${NC}"
    echo -e "${CYAN}docker compose up -d mysql postgres redis minio${NC}"
    exit 1
fi
echo ""

# 检查并暴露数据库端口
echo -e "${YELLOW}[2/5] 确保数据库端口已暴露到 localhost...${NC}"

# 检查 MySQL 端口
if docker ps | grep astron-agent-mysql | grep -q "3306->3306"; then
    echo -e "${GREEN}✓ MySQL 端口 3306 已映射${NC}"
else
    echo -e "${YELLOW}⚠️  MySQL 端口 3306 未映射,需要添加端口映射${NC}"
    echo -e "${CYAN}  在 docker-compose.yaml 的 mysql 服务中添加:${NC}"
    echo -e "${CYAN}  ports:${NC}"
    echo -e "${CYAN}    - \"3306:3306\"${NC}"
fi

# 检查 PostgreSQL 端口
if docker ps | grep astron-agent-postgres | grep -q "5432->5432"; then
    echo -e "${GREEN}✓ PostgreSQL 端口 5432 已映射${NC}"
else
    echo -e "${YELLOW}⚠️  PostgreSQL 端口 5432 未映射,需要添加端口映射${NC}"
    echo -e "${CYAN}  在 docker-compose.yaml 的 postgres 服务中添加:${NC}"
    echo -e "${CYAN}  ports:${NC}"
    echo -e "${CYAN}    - \"5432:5432\"${NC}"
fi

# 检查 Redis 端口
if docker ps | grep astron-agent-redis | grep -q "6379->6379"; then
    echo -e "${GREEN}✓ Redis 端口 6379 已映射${NC}"
else
    echo -e "${YELLOW}⚠️  Redis 端口 6379 未映射,需要添加端口映射${NC}"
    echo -e "${CYAN}  在 docker-compose.yaml 的 redis 服务中添加:${NC}"
    echo -e "${CYAN}  ports:${NC}"
    echo -e "${CYAN}    - \"6379:6379\"${NC}"
fi

# 检查 MinIO 端口
if docker ps | grep astron-agent-minio | grep -q "9000->9000"; then
    echo -e "${GREEN}✓ MinIO 端口 9000 已映射${NC}"
else
    echo -e "${YELLOW}⚠️  MinIO 端口 9000 未映射(默认应该已暴露)${NC}"
fi
echo ""

# 停止 Docker 中的 Python 服务
echo -e "${YELLOW}[3/5] 停止 Docker 中的 Python 服务...${NC}"
cd "$DOCKER_DIR"

PYTHON_SERVICES=("core-agent" "core-workflow" "core-link" "core-aitools")
for service in "${PYTHON_SERVICES[@]}"; do
    if docker ps | grep -q "$service"; then
        echo -e "${CYAN}停止 $service...${NC}"
        docker compose stop "$service" 2>/dev/null || true
        docker compose rm -f "$service" 2>/dev/null || true
    fi
done
echo -e "${GREEN}✓ Python 服务已停止${NC}"
echo ""

# 生成 Python 服务的配置文件
echo -e "${YELLOW}[4/5] 生成本地配置文件...${NC}"

# Agent 配置
AGENT_CONFIG="$PROJECT_ROOT/core/agent/config.env"
cat > "$AGENT_CONFIG" << 'EOF'
# Agent 本地调试环境配置
PYTHONUNBUFFERED=1

# 运行环境
RUN_ENVIRON=dev
USE_POLARIS=false

# 服务配置
SERVICE_NAME=Agent
SERVICE_SUB=sag
SERVICE_LOCATION=hf
SERVICE_HOST=0.0.0.0
SERVICE_PORT=17870
SERVICE_WORKERS=1
SERVICE_RELOAD=false
SERVICE_WS_PING_INTERVAL=false
SERVICE_WS_PING_TIMEOUT=false

# Redis 配置 (本地 Docker)
REDIS_ADDR=localhost:6379
REDIS_PASSWORD=
REDIS_EXPIRE=3600

# MySQL 配置 (本地 Docker)
MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_USER=root
MYSQL_PASSWORD=root123
MYSQL_DB=agent

# OTLP 配置 (禁用)
OTLP_ENDPOINT=127.0.0.1:4317
OTLP_ENABLE=0
UPLOAD_NODE_TRACE=false
UPLOAD_METRICS=false

# Kafka 配置 (禁用)
KAFKA_ENABLE=0
KAFKA_SERVERS=localhost:9092
KAFKA_TIMEOUT=60
KAFKA_TOPIC=spark-agent-builder

# Link 服务地址 (本地启动)
GET_LINK_URL=http://localhost:18888/api/v1/tools
VERSIONS_LINK_URL=http://localhost:18888/api/v1/tools/versions
RUN_LINK_URL=http://localhost:18888/api/v1/tools/http_run

# Workflow 服务地址 (本地启动)
GET_WORKFLOWS_URL=http://localhost:7880/sparkflow/v1/protocol/get
WORKFLOW_SSE_BASE_URL=http://localhost:7880/workflow/v1

# MCP 插件地址
LIST_MCP_PLUGIN_URL=http://localhost:18888/api/v1/mcp/tool_list
RUN_MCP_PLUGIN_URL=http://localhost:18888/api/v1/mcp/call_tool

# App 认证配置
APP_AUTH_HOST=localhost
APP_AUTH_ROUTER=/api-services/v2/app/details
APP_AUTH_PROT=http
APP_AUTH_API_KEY=YOUR_APP_AUTH_API_KEY
APP_AUTH_SECRET=YOUR_APP_AUTH_SECRET

# SSL 验证
SKIP_SSL_VERIFY=false
EOF

echo -e "${GREEN}✓ Agent 配置已生成: ${CYAN}$AGENT_CONFIG${NC}"

# Workflow 配置
WORKFLOW_CONFIG="$PROJECT_ROOT/core/workflow/config.env"
cat > "$WORKFLOW_CONFIG" << 'EOF'
# Workflow 本地调试环境配置
PYTHONUNBUFFERED=1

# 运行环境
RUNTIME_ENV=dev
SERVICE_PORT=7880

# MySQL 配置
MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_USER=root
MYSQL_PASSWORD=root123
MYSQL_DB=workflow

# Redis 配置
REDIS_ADDR=localhost:6379
REDIS_PASSWORD=
REDIS_EXPIRE=3600

# MinIO 配置
OSS_TYPE=s3
OSS_ENDPOINT=http://localhost:9000
OSS_ACCESS_KEY_ID=minioadmin
OSS_ACCESS_KEY_SECRET=minioadmin123
OSS_BUCKET_NAME=workflow
OSS_DOWNLOAD_HOST=http://localhost:9000
OSS_TTL=157788000

# OTLP 配置 (禁用)
OTLP_ENDPOINT=127.0.0.1:4317
OTLP_ENABLE=0

# Kafka 配置 (禁用)
KAFKA_ENABLE=0
KAFKA_SERVERS=localhost:9092

# 服务地址
PLUGIN_BASE_URL=http://localhost:18888
WORKFLOW_BASE_URL=http://localhost:7880
AGENT_BASE_URL=http://localhost:17870
EOF

echo -e "${GREEN}✓ Workflow 配置已生成: ${CYAN}$WORKFLOW_CONFIG${NC}"

# Link 配置
LINK_CONFIG="$PROJECT_ROOT/core/plugin/link/config.env"
cat > "$LINK_CONFIG" << 'EOF'
# Link 本地调试环境配置
SERVICE_PORT=18888

# MySQL 配置
MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_USER=root
MYSQL_PASSWORD=root123
MYSQL_DB=spark-link

# Redis 配置
REDIS_IS_CLUSTER=false
REDIS_ADDR=localhost:6379
REDIS_PASSWORD=

# OTLP 配置 (禁用)
OTLP_ENDPOINT=127.0.0.1:4317
OTLP_ENABLE=0

# Kafka 配置 (禁用)
KAFKA_ENABLE=0
KAFKA_SERVERS=localhost:9092
EOF

echo -e "${GREEN}✓ Link 配置已生成: ${CYAN}$LINK_CONFIG${NC}"

# AITools 配置
AITOOLS_CONFIG="$PROJECT_ROOT/core/plugin/aitools/config.env"
cat > "$AITOOLS_CONFIG" << 'EOF'
# AITools 本地调试环境配置
SERVICE_PORT=18668

# MinIO 配置
OSS_TYPE=s3
OSS_ENDPOINT=http://localhost:9000
OSS_ACCESS_KEY_ID=minioadmin
OSS_ACCESS_KEY_SECRET=minioadmin123
OSS_BUCKET_NAME=aitools
OSS_DOWNLOAD_HOST=http://localhost:9000
OSS_TTL=157788000

# 讯飞 AI 配置 (需要替换为实际值)
AI_APP_ID=your-app-id
AI_API_KEY=your-api-key
AI_API_SECRET=your-api-secret

# Kafka 配置 (禁用)
KAFKA_ENABLE=0
KAFKA_SERVERS=localhost:9092
EOF

echo -e "${GREEN}✓ AITools 配置已生成: ${CYAN}$AITOOLS_CONFIG${NC}"
echo ""

# 生成启动脚本
echo -e "${YELLOW}[5/5] 生成 Python 服务启动脚本...${NC}"

# 启动所有 Python 服务的脚本
START_ALL_SCRIPT="$SCRIPT_DIR/start-all-python-services.sh"
cat > "$START_ALL_SCRIPT" << 'SCRIPTEOF'
#!/bin/bash

# 启动所有 Python 服务
# 用途: 在 tmux 或多个终端窗口中启动所有 Python 服务

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  启动所有 Python 服务${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 检查是否安装了 uv
if ! command -v uv &> /dev/null; then
    echo -e "${YELLOW}⚠️  uv 未安装,正在安装...${NC}"
    curl -LsSf https://astral.sh/uv/install.sh | sh
    export PATH="$HOME/.cargo/bin:$PATH"
fi

# 检查是否在 tmux 中
if [ -n "$TMUX" ]; then
    echo -e "${CYAN}检测到 tmux 环境,将在新窗口中启动服务${NC}"
    
    # 在 tmux 新窗口中启动各个服务
    tmux new-window -n "link" -d "cd $PROJECT_ROOT/core/plugin/link && uv run python main.py"
    tmux new-window -n "aitools" -d "cd $PROJECT_ROOT/core/plugin/aitools && uv run python main.py"
    tmux new-window -n "workflow" -d "cd $PROJECT_ROOT/core/workflow && uv run python main.py"
    tmux new-window -n "agent" -d "cd $PROJECT_ROOT/core/agent && uv run python main.py"
    
    echo -e "${GREEN}✓ 所有服务已在 tmux 窗口中启动${NC}"
    echo -e "${CYAN}使用 Ctrl+B, W 查看所有窗口${NC}"
else
    echo -e "${YELLOW}建议在 tmux 中运行此脚本,以便管理多个服务${NC}"
    echo -e "${CYAN}或者手动在不同终端窗口中运行以下命令:${NC}"
    echo ""
    echo -e "${CYAN}# 终端 1 - Link 服务${NC}"
    echo "cd $PROJECT_ROOT/core/plugin/link && uv run python main.py"
    echo ""
    echo -e "${CYAN}# 终端 2 - AITools 服务${NC}"
    echo "cd $PROJECT_ROOT/core/plugin/aitools && uv run python main.py"
    echo ""
    echo -e "${CYAN}# 终端 3 - Workflow 服务${NC}"
    echo "cd $PROJECT_ROOT/core/workflow && uv run python main.py"
    echo ""
    echo -e "${CYAN}# 终端 4 - Agent 服务${NC}"
    echo "cd $PROJECT_ROOT/core/agent && uv run python main.py"
fi

echo ""
echo -e "${GREEN}服务端口:${NC}"
echo -e "  - Link:     http://localhost:18888"
echo -e "  - AITools:  http://localhost:18668"
echo -e "  - Workflow: http://localhost:7880"
echo -e "  - Agent:    http://localhost:17870"
SCRIPTEOF

chmod +x "$START_ALL_SCRIPT"
echo -e "${GREEN}✓ 启动脚本已生成: ${CYAN}$START_ALL_SCRIPT${NC}"
echo ""

# 显示使用指南
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  配置完成!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${CYAN}📝 使用指南:${NC}"
echo ""
echo -e "${BLUE}方案 1: 使用 PyCharm/VSCode 调试单个服务${NC}"
echo "  1. 用 PyCharm/VSCode 打开项目"
echo "  2. 配置 Python 解释器 (推荐使用 uv: uv venv)"
echo "  3. 创建运行配置,指向各服务的 main.py:"
echo "     - core/agent/main.py"
echo "     - core/workflow/main.py"
echo "     - core/plugin/link/main.py"
echo "     - core/plugin/aitools/main.py"
echo "  4. 设置工作目录为对应的服务目录"
echo "  5. 点击 Debug 按钮启动"
echo ""
echo -e "${BLUE}方案 2: 使用命令行启动所有服务${NC}"
echo "  运行: ${CYAN}$START_ALL_SCRIPT${NC}"
echo ""
echo -e "${BLUE}方案 3: 手动在不同终端启动各服务${NC}"
echo "  ${CYAN}# 终端 1 - Link 服务${NC}"
echo "  cd $PROJECT_ROOT/core/plugin/link"
echo "  uv run python main.py"
echo ""
echo "  ${CYAN}# 终端 2 - AITools 服务${NC}"
echo "  cd $PROJECT_ROOT/core/plugin/aitools"
echo "  uv run python main.py"
echo ""
echo "  ${CYAN}# 终端 3 - Workflow 服务${NC}"
echo "  cd $PROJECT_ROOT/core/workflow"
echo "  uv run python main.py"
echo ""
echo "  ${CYAN}# 终端 4 - Agent 服务${NC}"
echo "  cd $PROJECT_ROOT/core/agent"
echo "  uv run python main.py"
echo ""
echo -e "${YELLOW}⚠️  注意事项:${NC}"
echo "1. 确保已安装 Python 3.11+ 和 uv (pip install uv)"
echo "2. 确保 Docker 中的基础设施服务正在运行"
echo "3. 确保数据库端口已暴露 (MySQL:3306, PostgreSQL:5432, Redis:6379)"
echo "4. 配置文件中的数据库密码等敏感信息已自动填充"
echo "5. AITools 服务需要配置讯飞 AI 的 AppID、APIKey 等信息"
echo ""
echo -e "${CYAN}🔧 配置文件位置:${NC}"
echo "  - Agent:    $AGENT_CONFIG"
echo "  - Workflow: $WORKFLOW_CONFIG"
echo "  - Link:     $LINK_CONFIG"
echo "  - AITools:  $AITOOLS_CONFIG"
echo ""
echo -e "${CYAN}📖 启动脚本:${NC}"
echo "  - 启动所有服务: $START_ALL_SCRIPT"
echo ""
