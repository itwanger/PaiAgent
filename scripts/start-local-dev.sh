#!/bin/bash

# 本地开发环境一键启动脚本
# 用途: 按正确顺序启动所有服务,支持 Debug 调试
# 顺序: 基础设施 → Python 服务 → Console Hub (手动) → 前端 (手动)

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
MAGENTA='\033[0;35m'
NC='\033[0m'

clear
echo -e "${GREEN}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║                                                            ║${NC}"
echo -e "${GREEN}║        🚀 AI Podcast Workshop 本地开发环境启动器           ║${NC}"
echo -e "${GREEN}║                                                            ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""

# ============================================================================
# 步骤 1: 检查本地基础设施服务
# ============================================================================
echo -e "${MAGENTA}┌────────────────────────────────────────────────────────────┐${NC}"
echo -e "${MAGENTA}│  步骤 1/5: 检查本地基础设施服务                           │${NC}"
echo -e "${MAGENTA}└────────────────────────────────────────────────────────────┘${NC}"
echo ""

echo -e "${CYAN}请确保以下服务已在本地启动:${NC}"
echo -e "  ${YELLOW}• MySQL${NC}  - 端口 3306 (用户: root, 密码: 123456)"
echo -e "  ${YELLOW}• Redis${NC}  - 端口 6379"
echo -e "  ${YELLOW}• MinIO${NC}  - 端口 9000 (用户: minioadmin, 密码: minioadmin)"
echo ""

# 测试 MySQL 连接
if command -v mysql &> /dev/null; then
    if mysql -h localhost -P 3306 -u root -p123456 -e "SELECT 1" &> /dev/null; then
        echo -e "${GREEN}✓ MySQL 连接成功${NC}"
    else
        echo -e "${YELLOW}⚠ MySQL 连接失败,请检查:${NC}"
        echo -e "  ${CYAN}mysql -h localhost -P 3306 -u root -p123456${NC}"
    fi
else
    echo -e "${YELLOW}⚠ mysql 命令未找到,跳过连接测试${NC}"
fi

# 测试 Redis 连接
if command -v redis-cli &> /dev/null; then
    if redis-cli -h localhost -p 6379 ping &> /dev/null; then
        echo -e "${GREEN}✓ Redis 连接成功${NC}"
    else
        echo -e "${YELLOW}⚠ Redis 连接失败,请检查:${NC}"
        echo -e "  ${CYAN}redis-cli -h localhost -p 6379 ping${NC}"
    fi
else
    echo -e "${YELLOW}⚠ redis-cli 命令未找到,跳过连接测试${NC}"
fi

# 测试 MinIO 连接
if curl -s http://localhost:9000/minio/health/live > /dev/null 2>&1; then
    echo -e "${GREEN}✓ MinIO 连接成功${NC}"
else
    echo -e "${YELLOW}⚠ MinIO 连接失败,请检查:${NC}"
    echo -e "  ${CYAN}curl http://localhost:9000/minio/health/live${NC}"
fi

echo ""
echo -e "${YELLOW}按 Enter 继续,或 Ctrl+C 退出...${NC}"
read -r
echo ""

# ============================================================================
# 步骤 2: 检查端口占用
# ============================================================================
echo -e "${MAGENTA}┌────────────────────────────────────────────────────────────┐${NC}"
echo -e "${MAGENTA}│  步骤 2/5: 检查 Python 服务端口占用                       │${NC}"
echo -e "${MAGENTA}└────────────────────────────────────────────────────────────┘${NC}"
echo ""

# 检查关键端口是否被占用
PORT_CHECK=("17870:Agent" "7880:Workflow" "18888:Link" "18668:AITools")
for port_service in "${PORT_CHECK[@]}"; do
    port="${port_service%%:*}"
    service="${port_service##*:}"
    if lsof -i ":$port" &> /dev/null; then
        echo -e "${YELLOW}⚠ 端口 $port ($service) 已被占用${NC}"
        echo -e "  ${CYAN}查看占用进程: lsof -i :$port${NC}"
        echo -e "  ${CYAN}杀死进程: kill -9 \$(lsof -ti :$port)${NC}"
    else
        echo -e "${GREEN}✓ 端口 $port ($service) 可用${NC}"
    fi
done

echo ""

# ============================================================================
# 步骤 3: 检查并生成 Python 服务配置
# ============================================================================
echo -e "${MAGENTA}┌────────────────────────────────────────────────────────────┐${NC}"
echo -e "${MAGENTA}│  步骤 3/5: 检查 Python 服务配置文件                       │${NC}"
echo -e "${MAGENTA}└────────────────────────────────────────────────────────────┘${NC}"
echo ""

CONFIG_FILES=(
    "$PROJECT_ROOT/core/agent/config.env"
    "$PROJECT_ROOT/core/workflow/config.env"
    "$PROJECT_ROOT/core/plugin/link/config.env"
    "$PROJECT_ROOT/core/plugin/aitools/config.env"
)

NEED_SETUP=false
for config in "${CONFIG_FILES[@]}"; do
    if [ ! -f "$config" ]; then
        echo -e "${YELLOW}⚠ 配置文件不存在: $config${NC}"
        NEED_SETUP=true
    else
        echo -e "${GREEN}✓ $config${NC}"
    fi
done

if [ "$NEED_SETUP" = true ]; then
    echo ""
    echo -e "${CYAN}运行配置生成脚本...${NC}"
    "$SCRIPT_DIR/setup-python-local-debug.sh"
else
    echo -e "${GREEN}✓ 所有配置文件已存在${NC}"
fi
echo ""

# ============================================================================
# 步骤 4: 检查 Python 环境
# ============================================================================
echo -e "${MAGENTA}┌────────────────────────────────────────────────────────────┐${NC}"
echo -e "${MAGENTA}│  步骤 4/5: 检查 Python 开发环境                           │${NC}"
echo -e "${MAGENTA}└────────────────────────────────────────────────────────────┘${NC}"
echo ""

# 检查 Python 版本
if command -v python3 &> /dev/null; then
    PYTHON_VERSION=$(python3 --version | awk '{print $2}')
    echo -e "${GREEN}✓ Python 版本: $PYTHON_VERSION${NC}"
else
    echo -e "${RED}✗ Python 3 未安装${NC}"
    exit 1
fi

# 检查 uv
if command -v uv &> /dev/null; then
    UV_VERSION=$(uv --version | awk '{print $2}')
    echo -e "${GREEN}✓ uv 版本: $UV_VERSION${NC}"
else
    echo -e "${YELLOW}⚠ uv 未安装,正在安装...${NC}"
    curl -LsSf https://astral.sh/uv/install.sh | sh
    export PATH="$HOME/.cargo/bin:$PATH"
fi

echo ""

# ============================================================================
# 步骤 5: 显示启动指南
# ============================================================================
echo -e "${MAGENTA}┌────────────────────────────────────────────────────────────┐${NC}"
echo -e "${MAGENTA}│  步骤 5/5: 启动开发服务                                   │${NC}"
echo -e "${MAGENTA}└────────────────────────────────────────────────────────────┘${NC}"
echo ""

echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}  本地开发环境准备完成!请按以下顺序启动服务:${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"
echo ""

echo -e "${YELLOW}【启动顺序】${NC}"
echo ""
echo -e "${CYAN}1. 启动本地基础设施${NC}"
echo "   - MySQL (端口 3306)"
echo "   - Redis (端口 6379)"
echo "   - MinIO (端口 9000)"
echo ""
echo -e "${CYAN}2. 运行本脚本检查环境${NC}"
echo "   ./scripts/start-local-dev.sh"
echo ""
echo -e "${CYAN}3. 启动 Python 服务 (4个服务,按顺序)${NC}"
echo "   - Link → AITools → Workflow → Agent"
echo ""
echo -e "${CYAN}4. 启动 Console Hub (Java)${NC}"
echo "   - 在 IDEA 中 Debug 启动"
echo ""
echo -e "${CYAN}5. 启动前端${NC}"
echo "   - cd console/frontend && npm run dev"
echo ""
echo -e "${YELLOW}【方式 1: IDE 调试 - 推荐】${NC}"
echo ""
echo -e "${BLUE}┌─ Python 服务 (PyCharm/VSCode) ────────────────────────┐${NC}"
echo -e "│  ${GREEN}1. Link 服务${NC}     - 端口 18888"
echo "│     脚本: core/plugin/link/main.py"
echo "│     工作目录: core/plugin/link"
echo "│"
echo -e "│  ${GREEN}2. AITools 服务${NC}  - 端口 18668"
echo "│     脚本: core/plugin/aitools/main.py"
echo "│     工作目录: core/plugin/aitools"
echo "│"
echo -e "│  ${GREEN}3. Workflow 服务${NC} - 端口 7880"
echo "│     脚本: core/workflow/main.py"
echo "│     工作目录: core/workflow"
echo "│"
echo -e "│  ${GREEN}4. Agent 服务${NC}    - 端口 17870"
echo "│     脚本: core/agent/main.py"
echo "│     工作目录: core/agent"
echo "│"
echo -e "${BLUE}└────────────────────────────────────────────────────────┘${NC}"
echo ""

echo -e "${BLUE}┌─ Console Hub (IntelliJ IDEA) ─────────────────────────┐${NC}"
echo "│"
echo -e "│  ${GREEN}5. Console Hub${NC}   - 端口 8080"
echo "│     主类: com.iflytek.astron.console.hub.HubApplication"
echo "│     工作目录: console/backend/hub"
echo "│"
echo -e "│  ${YELLOW}环境变量 (在 IDEA 中配置):${NC}"
echo "│     MYSQL_URL=jdbc:mysql://localhost:3306/astron_console"
echo "│     MYSQL_USER=root"
echo "│     MYSQL_PASSWORD=123456"
echo "│     REDIS_HOST=localhost"
echo "│     REDIS_PORT=6379"
echo "│     (更多环境变量见 console/backend/hub/.env.local)"
echo "│"
echo -e "${BLUE}└────────────────────────────────────────────────────────┘${NC}"
echo ""

echo -e "${BLUE}┌─ Frontend (VSCode/终端) ──────────────────────────────┐${NC}"
echo "│"
echo -e "│  ${GREEN}6. 前端服务${NC}      - 端口 3000"
echo "│     cd console/frontend"
echo "│     npm install           # 首次运行"
echo "│     npm run dev"
echo "│"
echo -e "${BLUE}└────────────────────────────────────────────────────────┘${NC}"
echo ""

echo -e "${YELLOW}【方式 2】命令行快速启动${NC}"
echo ""
echo -e "${CYAN}# 启动所有 Python 服务 (使用 tmux)${NC}"
echo "tmux"
echo "./scripts/start-all-python-services.sh"
echo ""
echo -e "${CYAN}# 或手动在 4 个终端启动 Python 服务${NC}"
echo "cd core/plugin/link && uv run python main.py"
echo "cd core/plugin/aitools && uv run python main.py"
echo "cd core/workflow && uv run python main.py"
echo "cd core/agent && uv run python main.py"
echo ""
echo -e "${CYAN}# 启动 Console Hub (Maven)${NC}"
echo "cd console/backend"
echo "mvn spring-boot:run -pl hub"
echo ""
echo -e "${CYAN}# 启动前端${NC}"
echo "cd console/frontend"
echo "npm run dev"
echo ""

echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}  服务启动后的访问地址:${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "  ${GREEN}前端:${NC}          http://localhost:3000"
echo -e "  ${GREEN}Console Hub:${NC}   http://localhost:8080"
echo -e "  ${GREEN}Agent:${NC}         http://localhost:17870"
echo -e "  ${GREEN}Workflow:${NC}      http://localhost:7880"
echo -e "  ${GREEN}Link:${NC}          http://localhost:18888"
echo -e "  ${GREEN}AITools:${NC}       http://localhost:18668"
echo ""
echo -e "  ${GREEN}MySQL:${NC}         localhost:3306 (本地服务)"
echo -e "  ${GREEN}Redis:${NC}         localhost:6379 (本地服务)"
echo -e "  ${GREEN}MinIO:${NC}         http://localhost:9000 (本地服务)"
echo ""

echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}  调试提示:${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${YELLOW}1.${NC} Python 服务在 IDE 中设置断点后,点击 Debug 按钮即可调试"
echo -e "${YELLOW}2.${NC} Console Hub 在 IDEA 中以 Debug 模式启动,可以断点调试 Java 代码"
echo -e "${YELLOW}3.${NC} 前端使用 Chrome DevTools 调试,支持热重载 (HMR)"
echo -e "${YELLOW}4.${NC} 查看日志:"
echo "     - Python 服务: 控制台输出"
echo "     - Console Hub: IDEA 控制台"
echo "     - 前端: 浏览器控制台"
echo -e "${YELLOW}5.${NC} 停止服务:"
echo "     - Python: Ctrl+C 或 IDE 停止按钮"
echo "     - Console Hub: IDEA 停止按钮"
echo "     - 前端: Ctrl+C"
echo ""

echo -e "${GREEN}✨ 祝开发愉快!${NC}"
echo ""

# 询问是否自动在终端中启动 Python 服务
echo -e "${YELLOW}是否要在当前终端启动所有 Python 服务? (y/n)${NC}"
read -r response
if [[ "$response" =~ ^[Yy]$ ]]; then
    echo ""
    echo -e "${CYAN}正在启动 Python 服务...${NC}"
    echo -e "${YELLOW}提示: 使用 Ctrl+C 可以停止所有服务${NC}"
    echo ""
    
    # 创建临时启动脚本
    TEMP_SCRIPT=$(mktemp)
    cat > "$TEMP_SCRIPT" << 'SCRIPTEOF'
#!/bin/bash
trap 'echo ""; echo "停止所有 Python 服务..."; kill 0; exit' INT TERM

cd "$(dirname "$0")/../core/plugin/link"
echo "🔵 启动 Link 服务 (端口 18888)..."
uv run python main.py &

cd "$(dirname "$0")/../core/plugin/aitools"
echo "🟢 启动 AITools 服务 (端口 18668)..."
uv run python main.py &

cd "$(dirname "$0")/../core/workflow"
echo "🟡 启动 Workflow 服务 (端口 7880)..."
uv run python main.py &

cd "$(dirname "$0")/../core/agent"
echo "🟣 启动 Agent 服务 (端口 17870)..."
uv run python main.py &

wait
SCRIPTEOF
    
    chmod +x "$TEMP_SCRIPT"
    cd "$PROJECT_ROOT"
    exec "$TEMP_SCRIPT"
else
    echo ""
    echo -e "${CYAN}请按照上面的指南手动启动服务${NC}"
    echo ""
fi
