#!/bin/bash

# 工作流请求监控脚本
# 用途: 同时监控 Nginx、console-hub、Java Workflow 的日志

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  工作流请求链路监控${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${YELLOW}监控以下服务的日志:${NC}"
echo -e "  ${CYAN}[1] Nginx${NC} - 请求路由"
echo -e "  ${BLUE}[2] Console-Hub${NC} - 后端代理"
echo -e "  ${GREEN}[3] Java Workflow${NC} - 工作流引擎"
echo ""
echo -e "${YELLOW}提示:${NC}"
echo -e "  - 在浏览器打开 ${CYAN}http://localhost/work_flow/184742/arrange?botId=57${NC}"
echo -e "  - 点击 ${GREEN}\"调试\"${NC} 按钮"
echo -e "  - 观察下方日志输出"
echo -e "  - 按 ${RED}Ctrl+C${NC} 停止监控"
echo ""
echo -e "${GREEN}========================================${NC}"
echo ""

# 使用 tmux 分屏监控 (如果安装了 tmux)
if command -v tmux &> /dev/null; then
    echo -e "${BLUE}检测到 tmux，启动分屏监控...${NC}"
    
    # 创建新的 tmux 会话
    tmux new-session -d -s workflow-monitor
    
    # 分割窗口为 3 个窗格
    tmux split-window -h -t workflow-monitor
    tmux split-window -v -t workflow-monitor:0.0
    
    # 在每个窗格中运行日志监控
    tmux send-keys -t workflow-monitor:0.0 "docker logs -f astron-agent-nginx 2>&1 | grep -E 'workflow|chat'" C-m
    tmux send-keys -t workflow-monitor:0.1 "docker logs -f astron-agent-console-hub 2>&1 | grep -E 'workflow|WorkflowController|WORKFLOW'" C-m
    tmux send-keys -t workflow-monitor:0.2 "docker logs -f astron-agent-core-workflow-java 2>&1" C-m
    
    # 设置窗格标题
    tmux select-pane -t workflow-monitor:0.0 -T "Nginx"
    tmux select-pane -t workflow-monitor:0.1 -T "Console-Hub"
    tmux select-pane -t workflow-monitor:0.2 -T "Java Workflow"
    
    # 附加到会话
    tmux attach-session -t workflow-monitor
    
else
    # 没有 tmux，使用简单的日志监控
    echo -e "${YELLOW}未安装 tmux，使用简单监控模式${NC}"
    echo -e "${CYAN}建议安装 tmux 以获得更好的体验: brew install tmux${NC}"
    echo ""
    
    # 使用 tail -f 同时监控多个日志
    echo -e "${GREEN}[开始监控 Java Workflow 日志]${NC}"
    echo ""
    
    docker logs -f astron-agent-core-workflow-java 2>&1 | while read line; do
        # 高亮显示关键信息
        if echo "$line" | grep -q "Frontend workflow"; then
            echo -e "${GREEN}[WORKFLOW]${NC} $line"
        elif echo "$line" | grep -q "ERROR"; then
            echo -e "${RED}[ERROR]${NC} $line"
        elif echo "$line" | grep -q "WARN"; then
            echo -e "${YELLOW}[WARN]${NC} $line"
        elif echo "$line" | grep -q "node_start\|node_end\|node_output"; then
            echo -e "${CYAN}[NODE]${NC} $line"
        else
            echo "$line"
        fi
    done
fi
