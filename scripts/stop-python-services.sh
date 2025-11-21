#!/bin/bash

# 停止所有 Python 服务
# 用途: 杀死所有正在运行的 Python 服务进程

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}  停止所有 Python 服务${NC}"
echo -e "${YELLOW}========================================${NC}"
echo ""

PORT_CHECK=("18888:Link" "18668:AITools" "7880:Workflow" "17870:Agent")
STOPPED_COUNT=0

for port_service in "${PORT_CHECK[@]}"; do
    port="${port_service%%:*}"
    service="${port_service##*:}"
    
    if lsof -i ":$port" &> /dev/null; then
        PID=$(lsof -ti ":$port")
        echo -e "${CYAN}停止 $service (PID: $PID, 端口: $port)${NC}"
        kill -9 $PID 2>/dev/null
        STOPPED_COUNT=$((STOPPED_COUNT + 1))
        echo -e "${GREEN}✓ $service 已停止${NC}"
    else
        echo -e "${YELLOW}⚠ $service 未运行 (端口: $port)${NC}"
    fi
done

echo ""
if [ $STOPPED_COUNT -gt 0 ]; then
    echo -e "${GREEN}✓ 已停止 $STOPPED_COUNT 个服务${NC}"
else
    echo -e "${YELLOW}⚠️  没有发现正在运行的服务${NC}"
fi

echo ""
echo -e "${CYAN}验证服务状态:${NC}"
echo -e "  ./scripts/check-python-services.sh"
echo ""
