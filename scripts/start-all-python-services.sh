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
RED='\033[0;31m'
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
    echo ""
    echo -e "${CYAN}等待服务启动 (10秒)...${NC}"
    sleep 10
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
    exit 0
fi

# 检查服务状态
echo -e "${CYAN}检查服务状态:${NC}"
PORT_CHECK=("18888:Link" "18668:AITools" "7880:Workflow" "17870:Agent")
ALL_RUNNING=true

for port_service in "${PORT_CHECK[@]}"; do
    port="${port_service%%:*}"
    service="${port_service##*:}"
    if lsof -i ":$port" &> /dev/null; then
        echo -e "  ${GREEN}✓ $service 正在运行 (端口 $port)${NC}"
    else
        echo -e "  ${RED}✗ $service 未运行 (端口 $port)${NC}"
        ALL_RUNNING=false
    fi
done

echo ""
if [ "$ALL_RUNNING" = true ]; then
    echo -e "${GREEN}✓ 所有服务已成功启动！${NC}"
else
    echo -e "${YELLOW}⚠️  部分服务启动失败,请检查日志${NC}"
    echo -e "${CYAN}提示: 在 tmux 中切换到对应窗口查看日志${NC}"
fi

echo ""
echo -e "${GREEN}服务端口:${NC}"
echo -e "  - Link:     http://localhost:18888"
echo -e "  - AITools:  http://localhost:18668"
echo -e "  - Workflow: http://localhost:7880"
echo -e "  - Agent:    http://localhost:17870"
echo ""
echo -e "${CYAN}管理命令:${NC}"
echo -e "  - 查看服务状态: ${YELLOW}./scripts/check-python-services.sh${NC}"
echo -e "  - 停止所有服务: ${YELLOW}./scripts/stop-python-services.sh${NC}"
