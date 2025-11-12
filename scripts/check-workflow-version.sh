#!/bin/bash

###############################################################################
# Java Workflow 版本验证脚本
# 功能：验证当前使用的是 Java 还是 Python 版本的 Workflow
###############################################################################

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Workflow 版本验证${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# 1. 检查 Nginx 配置
echo -e "${YELLOW}[1/5] 检查 Nginx 配置...${NC}"
NGINX_CONFIG=$(docker exec astron-agent-nginx cat /etc/nginx/nginx.conf 2>/dev/null | grep -A 1 "workflow/v1/chat/completions" | grep proxy_pass)

if echo "$NGINX_CONFIG" | grep -q "core-workflow-java:7881"; then
    echo -e "${GREEN}✓ Nginx 已路由到 Java Workflow (7881)${NC}"
    NGINX_VERSION="Java"
elif echo "$NGINX_CONFIG" | grep -q "core-workflow:7880"; then
    echo -e "${YELLOW}⚠ Nginx 已路由到 Python Workflow (7880)${NC}"
    NGINX_VERSION="Python"
else
    echo -e "${RED}✗ 无法确定 Nginx 配置${NC}"
    NGINX_VERSION="Unknown"
fi
echo ""

# 2. 检查容器运行状态
echo -e "${YELLOW}[2/5] 检查容器运行状态...${NC}"
JAVA_STATUS=$(docker ps --filter name=core-workflow-java --format "{{.Status}}" 2>/dev/null)
PYTHON_STATUS=$(docker ps --filter name=core-workflow --format "{{.Status}}" 2>/dev/null | head -1)

if [ -n "$JAVA_STATUS" ]; then
    echo -e "${GREEN}✓ Java Workflow 容器运行中: $JAVA_STATUS${NC}"
else
    echo -e "${RED}✗ Java Workflow 容器未运行${NC}"
fi

if [ -n "$PYTHON_STATUS" ]; then
    echo -e "${CYAN}ℹ Python Workflow 容器运行中: $PYTHON_STATUS${NC}"
else
    echo -e "${CYAN}ℹ Python Workflow 容器未运行${NC}"
fi
echo ""

# 3. 测试端点响应
echo -e "${YELLOW}[3/5] 测试端点响应...${NC}"

# 测试 Java 端点
JAVA_RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:7881/actuator/health 2>/dev/null)
if [ "$JAVA_RESPONSE" = "200" ]; then
    echo -e "${GREEN}✓ Java Workflow 端点响应正常 (7881)${NC}"
    
    # 检查日志中的特征
    JAVA_LOG=$(docker logs astron-agent-core-workflow-java --tail 10 2>&1 | grep -i "WorkflowEngine\|Started WorkflowApplication")
    if [ -n "$JAVA_LOG" ]; then
        echo -e "${GREEN}  - 日志包含 Java Workflow 特征${NC}"
    fi
else
    echo -e "${RED}✗ Java Workflow 端点无响应 (7881)${NC}"
fi

# 测试 Python 端点
PYTHON_RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:7880/health 2>/dev/null)
if [ "$PYTHON_RESPONSE" = "200" ]; then
    echo -e "${CYAN}ℹ Python Workflow 端点响应正常 (7880)${NC}"
else
    echo -e "${CYAN}ℹ Python Workflow 端点无响应 (7880)${NC}"
fi
echo ""

# 4. 发送测试请求并检查日志
echo -e "${YELLOW}[4/5] 发送测试请求...${NC}"

# 清空 Java 日志标记
docker exec astron-agent-core-workflow-java sh -c 'echo "=== TEST REQUEST MARKER ===" >> /dev/null' 2>/dev/null

# 发送测试请求
TEST_RESPONSE=$(curl -s -X POST http://localhost:7881/api/v1/workflow/chat/stream \
  -H "Content-Type: application/json" \
  -d '{
    "flowId": "184736",
    "inputs": {"user_input": "版本验证测试"},
    "chatId": "version-check-'$(date +%s)'",
    "userId": "test"
  }' 2>&1 | head -5)

if echo "$TEST_RESPONSE" | grep -q "event:node_start"; then
    echo -e "${GREEN}✓ 测试请求成功，收到 SSE 事件流${NC}"
    
    # 等待1秒让日志写入
    sleep 1
    
    # 检查最新的 Java 日志
    RECENT_JAVA_LOG=$(docker logs astron-agent-core-workflow-java --tail 20 2>&1)
    if echo "$RECENT_JAVA_LOG" | grep -q "Frontend workflow chat stream request\|Executing node"; then
        echo -e "${GREEN}  - Java Workflow 日志显示正在处理请求${NC}"
        EXECUTION_VERSION="Java"
    else
        echo -e "${YELLOW}  - 未在 Java 日志中发现请求记录${NC}"
        EXECUTION_VERSION="Unknown"
    fi
    
    # 检查 Python 日志
    RECENT_PYTHON_LOG=$(docker logs astron-agent-core-workflow --tail 20 2>&1)
    if echo "$RECENT_PYTHON_LOG" | grep -q "版本验证测试"; then
        echo -e "${RED}  - Python Workflow 日志显示正在处理请求${NC}"
        EXECUTION_VERSION="Python"
    fi
else
    echo -e "${RED}✗ 测试请求失败${NC}"
    EXECUTION_VERSION="Unknown"
fi
echo ""

# 5. 总结
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  验证结果${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

if [ "$NGINX_VERSION" = "Java" ] && [ "$EXECUTION_VERSION" = "Java" ]; then
    echo -e "${GREEN}✅ 当前使用的是 Java Workflow${NC}"
    echo -e "${GREEN}   - Nginx 路由: Java (7881)${NC}"
    echo -e "${GREEN}   - 实际执行: Java${NC}"
    echo ""
    echo -e "${CYAN}📍 特征标识:${NC}"
    echo -e "   - 日志包含: WorkflowEngine, Started WorkflowApplication"
    echo -e "   - 端口: 7881"
    echo -e "   - 容器: astron-agent-core-workflow-java"
elif [ "$NGINX_VERSION" = "Python" ] && [ "$EXECUTION_VERSION" = "Python" ]; then
    echo -e "${YELLOW}⚠️  当前使用的是 Python Workflow${NC}"
    echo -e "${YELLOW}   - Nginx 路由: Python (7880)${NC}"
    echo -e "${YELLOW}   - 实际执行: Python${NC}"
    echo ""
    echo -e "${CYAN}💡 切换到 Java 版本:${NC}"
    echo -e "   ./scripts/switch-to-java.sh"
else
    echo -e "${RED}❓ 版本状态不明确${NC}"
    echo -e "   - Nginx 路由: $NGINX_VERSION"
    echo -e "   - 实际执行: $EXECUTION_VERSION"
    echo ""
    echo -e "${CYAN}📋 手动检查:${NC}"
    echo -e "   docker logs astron-agent-core-workflow-java --tail 20"
    echo -e "   docker logs astron-agent-core-workflow --tail 20"
fi
echo ""

# 快速命令提示
echo -e "${CYAN}🔧 常用命令:${NC}"
echo -e "   查看 Java 日志:   ${YELLOW}docker logs -f astron-agent-core-workflow-java${NC}"
echo -e "   查看 Python 日志: ${YELLOW}docker logs -f astron-agent-core-workflow${NC}"
echo -e "   切换到 Java:      ${YELLOW}./scripts/switch-to-java.sh${NC}"
echo -e "   切换到 Python:    ${YELLOW}./scripts/switch-to-python.sh${NC}"
echo -e "   重启 Java:        ${YELLOW}./scripts/restart-java-workflow.sh${NC}"
echo ""
