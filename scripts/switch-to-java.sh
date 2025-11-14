#!/bin/bash

###############################################################################
# 切换到 Java Workflow 版本
# 功能：停止 Python 版本，启动 Java 版本，更新路由配置
# 用法：./scripts/switch-to-java.sh
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
echo -e "${BLUE}  切换到 Java Workflow${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

cd "$DOCKER_DIR"

# 停止 Python 版本
echo -e "${YELLOW}[1/5] 停止 Python Workflow...${NC}"
docker compose stop core-workflow 2>/dev/null || true
echo -e "${GREEN}✓ Python Workflow 已停止${NC}"
echo ""

# 更新环境变量 - 必须在启动容器之前修改
echo -e "${YELLOW}[2/5] 更新路由配置...${NC}"
export WORKFLOW_VERSION=java
echo "WORKFLOW_VERSION=java" > .env.workflow

# 修改 .env 中的 CORE_WORKFLOW_PORT 为 7881
if [ -f .env ]; then
    sed -i.bak 's/^CORE_WORKFLOW_PORT=7880/CORE_WORKFLOW_PORT=7881/' .env
    echo -e "${GREEN}✓ 已更新 .env 文件: CORE_WORKFLOW_PORT=7881${NC}"
fi
echo ""

# 重建 Java Workflow 以加载新的环境变量
echo -e "${YELLOW}[3/5] 重建 Java Workflow (加载端口 7881)...${NC}"
if docker compose -f docker-compose.workflow-dual.yml ps core-workflow-java | grep -q "Up"; then
    docker compose -f docker-compose.workflow-dual.yml stop core-workflow-java
    docker compose -f docker-compose.workflow-dual.yml rm -f core-workflow-java
fi
docker compose -f docker-compose.workflow-dual.yml up -d core-workflow-java
echo -e "${GREEN}✓ Java Workflow 已启动 (端口 7881)${NC}"
echo ""

# 重建 console-hub 以应用新的环境变量
echo -e "${YELLOW}[4/5] 重建 console-hub (加载路由配置)...${NC}"
docker compose stop console-hub
docker compose rm -f console-hub
docker compose up -d console-hub
echo -e "${GREEN}✓ console-hub 已重建，现在所有请求将转发到 Java Workflow (7881)${NC}"
echo ""

# 健康检查
echo -e "${YELLOW}[5/5] 健康检查...${NC}"
sleep 5
if curl -f -s http://localhost:7881/actuator/health > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Java Workflow 运行正常${NC}"
else
    echo -e "${YELLOW}⚠ 服务启动中，请稍后检查${NC}"
fi

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  已切换到 Java Workflow${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${BLUE}服务信息：${NC}"
echo -e "  版本: ${GREEN}Java${NC}"
echo -e "  端口: ${GREEN}7881${NC}"
echo -e "  日志: ${YELLOW}docker logs -f astron-agent-core-workflow-java${NC}"
echo ""
