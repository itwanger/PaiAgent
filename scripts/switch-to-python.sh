#!/bin/bash

###############################################################################
# 切换到 Python Workflow 版本
# 功能：停止 Java 版本，启动 Python 版本，更新路由配置
# 用法：./scripts/switch-to-python.sh
###############################################################################

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="$SCRIPT_DIR/../docker/astronAgent"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  切换到 Python Workflow${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

cd "$DOCKER_DIR"

# 停止 Java 版本
echo -e "${YELLOW}[1/3] 停止 Java Workflow...${NC}"
docker compose stop core-workflow-java 2>/dev/null || true
echo -e "${GREEN}✓ Java Workflow 已停止${NC}"
echo ""

# 确保 Python 版本运行
echo -e "${YELLOW}[2/3] 启动 Python Workflow...${NC}"
docker compose up -d core-workflow
echo -e "${GREEN}✓ Python Workflow 已启动${NC}"
echo ""

# 更新环境变量
echo -e "${YELLOW}[3/3] 更新路由配置...${NC}"
export WORKFLOW_VERSION=python
echo "WORKFLOW_VERSION=python" > .env.workflow
echo -e "${GREEN}✓ 已切换到 Python 版本${NC}"
echo ""

# 健康检查
echo -e "${BLUE}健康检查...${NC}"
sleep 3
if curl -f -s http://localhost:7880/health > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Python Workflow 运行正常${NC}"
else
    echo -e "${YELLOW}⚠ 服务启动中，请稍后检查${NC}"
fi

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  已切换到 Python Workflow${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${BLUE}服务信息：${NC}"
echo -e "  版本: ${GREEN}Python${NC}"
echo -e "  端口: ${GREEN}7880${NC}"
echo -e "  日志: ${YELLOW}docker logs -f astron-agent-core-workflow${NC}"
echo ""
