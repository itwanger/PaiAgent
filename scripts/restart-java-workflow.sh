#!/bin/bash

###############################################################################
# Java Workflow 快速重启脚本
# 功能：编译 Java 代码，重新构建 Docker 镜像，重启服务
# 用法：./scripts/restart-java-workflow.sh
###############################################################################

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 项目路径
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
JAVA_PROJECT_DIR="$PROJECT_ROOT/core-workflow-java"
DOCKER_DIR="$PROJECT_ROOT/docker/astronAgent"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Java Workflow 快速重启脚本${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# 检查 jenv 和 JDK 21
echo -e "${YELLOW}[1/6] 检查 Java 环境...${NC}"
if command -v jenv &> /dev/null; then
    echo -e "${GREEN}✓ jenv 已安装${NC}"
    
    # 设置本地 Java 版本为 21
    cd "$JAVA_PROJECT_DIR"
    if jenv versions | grep -q "21"; then
        jenv local 21 2>/dev/null || true
        echo -e "${GREEN}✓ 已设置 JDK 21${NC}"
    else
        echo -e "${RED}✗ JDK 21 未通过 jenv 安装${NC}"
        echo -e "${YELLOW}请运行: jenv add /path/to/jdk21${NC}"
        exit 1
    fi
else
    echo -e "${YELLOW}⚠ jenv 未安装，使用系统默认 Java${NC}"
fi

# 验证 Java 版本 - 使用 JAVA_HOME 确保正确版本
export JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || echo "")
if [ -z "$JAVA_HOME" ]; then
    echo -e "${RED}✗ 找不到 JDK 21${NC}"
    echo -e "${YELLOW}请安装 JDK 21${NC}"
    exit 1
fi

export PATH="$JAVA_HOME/bin:$PATH"
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
echo -e "${GREEN}✓ 当前 Java 版本: $JAVA_VERSION${NC}"
echo -e "${GREEN}✓ Java 版本: $(java -version 2>&1 | head -n 1)${NC}"
echo ""

# 清理旧的构建产物
echo -e "${YELLOW}[2/6] 清理旧的构建产物...${NC}"
cd "$JAVA_PROJECT_DIR"
mvn clean > /dev/null 2>&1
echo -e "${GREEN}✓ 清理完成${NC}"
echo ""

# 编译打包
echo -e "${YELLOW}[3/6] 编译打包 Java 项目...${NC}"
echo -e "${BLUE}正在执行: mvn package -DskipTests${NC}"
cd "$JAVA_PROJECT_DIR"
if mvn package -DskipTests; then
    echo -e "${GREEN}✓ 编译成功: target/workflow-java.jar${NC}"
else
    echo -e "${RED}✗ 编译失败！请检查代码错误${NC}"
    echo -e "${YELLOW}💡 提示：可以切换到 Python 版本继续工作：${NC}"
    echo -e "${YELLOW}   cd $DOCKER_DIR${NC}"
    echo -e "${YELLOW}   ./scripts/switch-to-python.sh${NC}"
    exit 1
fi
echo ""

# 停止旧容器
echo -e "${YELLOW}[4/6] 停止旧的 Java Workflow 容器...${NC}"
cd "$DOCKER_DIR"
docker compose -f docker-compose.workflow-dual.yml --profile java-workflow stop core-workflow-java 2>/dev/null || true
docker compose -f docker-compose.workflow-dual.yml --profile java-workflow rm -f core-workflow-java 2>/dev/null || true
echo -e "${GREEN}✓ 旧容器已停止${NC}"
echo ""

# 重新构建镜像
echo -e "${YELLOW}[5/6] 重新构建 Docker 镜像...${NC}"
cd "$DOCKER_DIR"
if docker compose -f docker-compose.workflow-dual.yml build core-workflow-java; then
    echo -e "${GREEN}✓ 镜像构建成功${NC}"
else
    echo -e "${RED}✗ 镜像构建失败！${NC}"
    exit 1
fi
echo ""

# 启动新容器
echo -e "${YELLOW}[6/6] 启动新的 Java Workflow 容器...${NC}"
cd "$DOCKER_DIR"
docker compose -f docker-compose.workflow-dual.yml --profile java-workflow up -d core-workflow-java

# 等待服务启动
echo -e "${BLUE}等待服务启动...${NC}"
sleep 5

# 健康检查
HEALTH_URL="http://localhost:7881/actuator/health"
MAX_RETRIES=30
RETRY_COUNT=0

while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    if curl -f -s "$HEALTH_URL" > /dev/null 2>&1; then
        echo -e "${GREEN}✓ 服务启动成功！${NC}"
        echo ""
        echo -e "${GREEN}========================================${NC}"
        echo -e "${GREEN}  Java Workflow 已成功重启！${NC}"
        echo -e "${GREEN}========================================${NC}"
        echo ""
        echo -e "${BLUE}服务信息：${NC}"
        echo -e "  端口: ${GREEN}7881${NC}"
        echo -e "  健康检查: ${GREEN}$HEALTH_URL${NC}"
        echo -e "  日志: ${YELLOW}docker logs -f astron-agent-core-workflow-java${NC}"
        echo ""
        echo -e "${BLUE}快速命令：${NC}"
        echo -e "  查看日志: ${YELLOW}docker logs -f astron-agent-core-workflow-java${NC}"
        echo -e "  重启服务: ${YELLOW}./scripts/restart-java-workflow.sh${NC}"
        echo -e "  切换到 Python: ${YELLOW}./scripts/switch-to-python.sh${NC}"
        echo ""
        exit 0
    fi
    RETRY_COUNT=$((RETRY_COUNT + 1))
    echo -e "${YELLOW}⏳ 等待服务启动... ($RETRY_COUNT/$MAX_RETRIES)${NC}"
    sleep 2
done

echo -e "${RED}✗ 服务启动失败或超时！${NC}"
echo -e "${YELLOW}查看日志: docker logs astron-agent-core-workflow-java${NC}"
echo ""
echo -e "${YELLOW}💡 紧急回滚到 Python 版本：${NC}"
echo -e "${YELLOW}   ./scripts/switch-to-python.sh${NC}"
exit 1
