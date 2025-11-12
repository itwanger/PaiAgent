#!/bin/bash

# 恢复 Docker 环境脚本
# 用途: 调试完成后，恢复所有服务在 Docker 中运行

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DOCKER_DIR="$PROJECT_ROOT/docker/astronAgent"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  恢复 Docker 环境${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

echo -e "${YELLOW}[1/2] 停止 IDEA 中的 console-hub...${NC}"
echo -e "${BLUE}请在 IntelliJ IDEA 中手动停止 console-hub (点击红色停止按钮)${NC}"
echo -e "${YELLOW}按 Enter 继续...${NC}"
read

echo -e "${YELLOW}[2/2] 启动 Docker 中的 console-hub...${NC}"
cd "$DOCKER_DIR"
docker compose up -d console-hub

echo ""
echo -e "${GREEN}✓ Console-Hub 已在 Docker 中启动${NC}"
echo ""

# 等待服务启动
echo -e "${BLUE}等待服务启动...${NC}"
sleep 5

# 验证服务状态
if docker ps | grep -q astron-agent-console-hub; then
    echo -e "${GREEN}✓ Console-Hub 运行正常${NC}"
    docker ps --filter "name=astron-agent-console-hub" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
else
    echo -e "${RED}✗ Console-Hub 启动失败${NC}"
    echo -e "${YELLOW}查看日志: docker logs astron-agent-console-hub${NC}"
fi

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Docker 环境已恢复${NC}"
echo -e "${GREEN}========================================${NC}"
