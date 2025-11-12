#!/bin/bash

# Docker Remote Debug 配置脚本
# 用途: 配置 console-hub 在 Docker 中启动但可以在 IDEA 中远程调试

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
echo -e "${GREEN}  配置 Docker 远程调试${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

echo -e "${YELLOW}步骤 1: 修改 docker-compose.yaml 添加调试端口${NC}"
echo ""
echo -e "${CYAN}需要在 console-hub 服务中添加:${NC}"
cat << 'EOF'

  console-hub:
    environment:
      - JAVA_TOOL_OPTIONS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
    ports:
      - "8080:8080"
      - "5005:5005"  # 远程调试端口

EOF

echo -e "${YELLOW}是否自动修改 docker-compose.yaml? (y/n)${NC}"
read -r answer

if [ "$answer" = "y" ]; then
    cd "$DOCKER_DIR"
    
    # 检查是否已经配置了调试端口
    if grep -q "5005:5005" docker-compose.yaml; then
        echo -e "${GREEN}✓ 调试端口已配置${NC}"
    else
        echo -e "${YELLOW}⚠️  需要手动编辑 docker-compose.yaml${NC}"
        echo -e "${CYAN}在 console-hub 服务的 ports 部分添加:${NC}"
        echo -e "${CYAN}  - \"5005:5005\"${NC}"
    fi
fi

echo ""
echo -e "${YELLOW}步骤 2: 重启 console-hub 容器${NC}"
echo ""
echo -e "${CYAN}运行命令:${NC}"
echo -e "${GREEN}cd $DOCKER_DIR${NC}"
echo -e "${GREEN}docker compose stop console-hub${NC}"
echo -e "${GREEN}docker compose rm -f console-hub${NC}"
echo -e "${GREEN}docker compose up -d console-hub${NC}"
echo ""

echo -e "${YELLOW}步骤 3: 在 IDEA 中配置远程调试${NC}"
echo ""
echo -e "${CYAN}1. Run → Edit Configurations...${NC}"
echo -e "${CYAN}2. + → Remote JVM Debug${NC}"
echo -e "${CYAN}3. 配置:${NC}"
echo -e "   Name: ${GREEN}console-hub (Docker Remote Debug)${NC}"
echo -e "   Debugger mode: ${GREEN}Attach to remote JVM${NC}"
echo -e "   Host: ${GREEN}localhost${NC}"
echo -e "   Port: ${GREEN}5005${NC}"
echo -e "   Command line arguments for remote JVM: ${GREEN}(自动生成，不用管)${NC}"
echo ""

echo -e "${YELLOW}步骤 4: 启动调试${NC}"
echo ""
echo -e "${CYAN}1. 在 WorkflowChatController.java 打断点${NC}"
echo -e "${CYAN}2. 点击 IDEA 中的 Debug 按钮 (选择刚创建的 Remote Debug 配置)${NC}"
echo -e "${CYAN}3. 看到 \"Connected to the target VM\" 说明连接成功${NC}"
echo -e "${CYAN}4. 在浏览器中触发工作流，断点应该会命中！${NC}"
echo ""

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  配置完成！${NC}"
echo -e "${GREEN}========================================${NC}"
