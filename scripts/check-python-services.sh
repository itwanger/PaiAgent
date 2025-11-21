#!/bin/bash

# 检查 Python 服务状态
# 用途: 查看所有 Python 服务是否正在运行

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}========================================${NC}"
echo -e "${CYAN}  Python 服务状态检查${NC}"
echo -e "${CYAN}========================================${NC}"
echo ""

PORT_CHECK=("18888:Link" "18668:AITools" "7880:Workflow" "17870:Agent")
ALL_RUNNING=true

for port_service in "${PORT_CHECK[@]}"; do
    port="${port_service%%:*}"
    service="${port_service##*:}"
    
    if lsof -i ":$port" &> /dev/null; then
        PID=$(lsof -ti ":$port")
        echo -e "${GREEN}✓ $service${NC}"
        echo -e "  端口: $port"
        echo -e "  PID:  $PID"
        echo -e "  URL:  http://localhost:$port"
    else
        echo -e "${RED}✗ $service${NC}"
        echo -e "  端口: $port"
        echo -e "  状态: 未运行"
        ALL_RUNNING=false
    fi
    echo ""
done

if [ "$ALL_RUNNING" = true ]; then
    echo -e "${GREEN}✓ 所有服务正在运行${NC}"
else
    echo -e "${YELLOW}⚠️  部分服务未运行${NC}"
    echo ""
    echo -e "${CYAN}启动服务:${NC}"
    echo -e "  ./scripts/start-all-python-services.sh"
fi
echo ""
echo -e "${CYAN}管理命令:${NC}"
echo -e "  - 停止所有服务: ${YELLOW}./scripts/stop-python-services.sh${NC}"
echo -e "  - 停止单个服务: ${YELLOW}kill -9 \$(lsof -ti :端口号)${NC}"
echo ""
