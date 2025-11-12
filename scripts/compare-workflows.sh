#!/bin/bash

###############################################################################
# 对比 Python 和 Java Workflow 的运行结果
# 功能：同时测试两个版本，对比输出结果
# 用法：./scripts/compare-workflows.sh <workflow_id>
###############################################################################

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

if [ -z "$1" ]; then
    echo -e "${RED}用法: $0 <workflow_id>${NC}"
    echo -e "${YELLOW}示例: $0 184736${NC}"
    exit 1
fi

WORKFLOW_ID=$1
PYTHON_PORT=7880
JAVA_PORT=7881

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Workflow 版本对比测试${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "${YELLOW}Workflow ID: $WORKFLOW_ID${NC}"
echo ""

# 检查两个服务是否都在运行
echo -e "${BLUE}[1/4] 检查服务状态...${NC}"
PYTHON_RUNNING=false
JAVA_RUNNING=false

if curl -f -s http://localhost:$PYTHON_PORT/health > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Python Workflow 运行中 (端口 $PYTHON_PORT)${NC}"
    PYTHON_RUNNING=true
else
    echo -e "${YELLOW}⚠ Python Workflow 未运行${NC}"
fi

if curl -f -s http://localhost:$JAVA_PORT/actuator/health > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Java Workflow 运行中 (端口 $JAVA_PORT)${NC}"
    JAVA_RUNNING=true
else
    echo -e "${YELLOW}⚠ Java Workflow 未运行${NC}"
fi

if [ "$PYTHON_RUNNING" = false ] && [ "$JAVA_RUNNING" = false ]; then
    echo -e "${RED}✗ 两个服务都未运行，无法对比${NC}"
    exit 1
fi
echo ""

# 准备测试数据
TEST_DATA='{
  "workflowId": "'$WORKFLOW_ID'",
  "inputs": {
    "user_input": "介绍一下 Java 和 Python 的区别"
  }
}'

# 测试 Python 版本
if [ "$PYTHON_RUNNING" = true ]; then
    echo -e "${BLUE}[2/4] 测试 Python 版本...${NC}"
    echo -e "${YELLOW}请求: POST http://localhost:$PYTHON_PORT/workflow/v1/execute${NC}"
    
    PYTHON_RESPONSE=$(curl -s -X POST \
        -H "Content-Type: application/json" \
        -d "$TEST_DATA" \
        http://localhost:$PYTHON_PORT/workflow/v1/execute)
    
    echo "$PYTHON_RESPONSE" | jq '.' > /tmp/python-workflow-response.json 2>/dev/null || echo "$PYTHON_RESPONSE" > /tmp/python-workflow-response.json
    echo -e "${GREEN}✓ Python 版本响应已保存到: /tmp/python-workflow-response.json${NC}"
    echo ""
fi

# 测试 Java 版本
if [ "$JAVA_RUNNING" = true ]; then
    echo -e "${BLUE}[3/4] 测试 Java 版本...${NC}"
    echo -e "${YELLOW}请求: POST http://localhost:$JAVA_PORT/workflow/v1/execute${NC}"
    
    JAVA_RESPONSE=$(curl -s -X POST \
        -H "Content-Type: application/json" \
        -d "$TEST_DATA" \
        http://localhost:$JAVA_PORT/workflow/v1/execute)
    
    echo "$JAVA_RESPONSE" | jq '.' > /tmp/java-workflow-response.json 2>/dev/null || echo "$JAVA_RESPONSE" > /tmp/java-workflow-response.json
    echo -e "${GREEN}✓ Java 版本响应已保存到: /tmp/java-workflow-response.json${NC}"
    echo ""
fi

# 对比结果
if [ "$PYTHON_RUNNING" = true ] && [ "$JAVA_RUNNING" = true ]; then
    echo -e "${BLUE}[4/4] 对比结果...${NC}"
    
    echo -e "${YELLOW}使用 diff 对比：${NC}"
    if diff -u /tmp/python-workflow-response.json /tmp/java-workflow-response.json; then
        echo -e "${GREEN}✓ 两个版本输出完全一致！${NC}"
    else
        echo -e "${YELLOW}⚠ 发现差异（这是正常的，因为实现细节可能不同）${NC}"
    fi
    echo ""
    
    echo -e "${BLUE}详细对比：${NC}"
    echo -e "  Python 响应: ${YELLOW}/tmp/python-workflow-response.json${NC}"
    echo -e "  Java 响应: ${YELLOW}/tmp/java-workflow-response.json${NC}"
    echo ""
fi

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  对比测试完成${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${BLUE}查看详细结果：${NC}"
echo -e "  cat /tmp/python-workflow-response.json"
echo -e "  cat /tmp/java-workflow-response.json"
echo ""
