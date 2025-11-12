#!/bin/bash

# 追踪工作流请求链路
# 用途: 帮助理解请求从浏览器到 Java Workflow 的完整路径

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  工作流请求链路分析${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

echo -e "${YELLOW}📋 请求链路说明:${NC}"
echo ""

echo -e "${CYAN}1️⃣  浏览器 → Nginx (端口 80)${NC}"
echo "   URL: http://localhost/work_flow/184742/arrange?botId=57"
echo "   说明: 前端页面加载"
echo ""

echo -e "${CYAN}2️⃣  前端 React → Nginx → Console-Hub (端口 8080)${NC}"
echo "   前端调用: getFixedUrl('/workflow/chat')"
echo "   实际请求: POST http://localhost/console-api/workflow/chat"
echo "   Nginx 路由: /console-api/ → http://console-hub:8080/"
echo "   到达: Console-Hub 的 /workflow/chat 端点"
echo ""

echo -e "${CYAN}3️⃣  Console-Hub → Java Workflow (端口 7881)${NC}"
echo "   Console-Hub 读取环境变量: WORKFLOW_CHAT_URL"
echo "   当前值: \${WORKFLOW_CHAT_URL}"
echo "   代理请求到: http://core-workflow-java:7881/api/v1/workflow/chat/stream"
echo ""

echo -e "${CYAN}4️⃣  Java Workflow 执行工作流${NC}"
echo "   接收请求: POST /api/v1/workflow/chat/stream"
echo "   Controller: WorkflowFrontendController.workflowChatStream()"
echo "   返回: SSE 事件流"
echo ""

echo -e "${GREEN}========================================${NC}"
echo -e "${YELLOW}🔍 验证当前配置${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 检查 Nginx 配置
echo -e "${BLUE}[Nginx 配置]${NC}"
echo "  /console-api/ 路由到:"
docker exec astron-agent-nginx grep -A2 "location /console-api/" /etc/nginx/nginx.conf | grep proxy_pass || echo "  未找到配置"
echo ""

# 检查 Console-Hub 环境变量
echo -e "${BLUE}[Console-Hub 环境变量]${NC}"
docker exec astron-agent-console-hub env | grep WORKFLOW || echo "  未找到 WORKFLOW 相关环境变量"
echo ""

# 检查 Java Workflow 容器状态
echo -e "${BLUE}[Java Workflow 容器状态]${NC}"
if docker ps | grep -q astron-agent-core-workflow-java; then
    echo -e "  ${GREEN}✓ 运行中${NC}"
    echo "  端口: $(docker port astron-agent-core-workflow-java 7881 2>/dev/null || echo '未映射')"
else
    echo -e "  ${RED}✗ 未运行${NC}"
fi
echo ""

echo -e "${GREEN}========================================${NC}"
echo -e "${YELLOW}🧪 测试命令${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

echo -e "${CYAN}1. 直接测试 Java Workflow (跳过 Nginx + Console-Hub):${NC}"
echo '   curl -X POST http://localhost:7881/api/v1/workflow/chat/stream \'
echo '     -H "Content-Type: application/json" \'
echo '     -d '"'"'{"flowId":"184742","inputs":{"user_input":"test"}}'"'"
echo ""

echo -e "${CYAN}2. 测试通过 Console-Hub (跳过前端):${NC}"
echo '   curl -X POST http://localhost/console-api/workflow/chat \'
echo '     -H "Content-Type: application/json" \'
echo '     -H "Authorization: Bearer YOUR_TOKEN" \'
echo '     -d '"'"'{"flowId":"184742","inputs":{"user_input":"test"}}'"'"
echo ""

echo -e "${CYAN}3. 查看实时日志:${NC}"
echo "   ./scripts/monitor-workflow.sh"
echo ""

echo -e "${CYAN}4. 在浏览器中调试:${NC}"
echo "   打开开发者工具 (F12) → Network 标签 → 点击 \"调试\" 按钮"
echo "   查找以下请求:"
echo "   - 请求 URL: /console-api/workflow/chat 或类似"
echo "   - 响应类型: text/event-stream (SSE)"
echo "   - 状态码: 200"
echo ""

echo -e "${GREEN}========================================${NC}"
echo -e "${YELLOW}🐛 Debug 步骤建议${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

echo "1️⃣  运行日志监控:"
echo "   ./scripts/monitor-workflow.sh"
echo ""

echo "2️⃣  打开浏览器:"
echo "   http://localhost/work_flow/184742/arrange?botId=57"
echo ""

echo "3️⃣  打开浏览器开发者工具 (F12):"
echo "   - 切换到 Network 标签"
echo "   - 勾选 \"Preserve log\""
echo "   - 点击页面上的 \"调试\" 或 \"运行\" 按钮"
echo ""

echo "4️⃣  观察以下内容:"
echo "   - Network 标签: 查找 workflow 相关请求"
echo "   - Console 标签: 查看是否有 JavaScript 错误"
echo "   - 终端日志: 查看是否有请求到达 Java Workflow"
echo ""

echo -e "${GREEN}========================================${NC}"
