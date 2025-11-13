#!/bin/bash

# 工作流版本切换脚本 V2
# 用途: 在主 docker-compose.yaml 中切换 Python/Java 工作流版本

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

print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

show_help() {
    cat << EOF
${GREEN}工作流版本切换脚本 V2${NC}

${YELLOW}用法:${NC}
    $0 [java|python|status]

${YELLOW}命令:${NC}
    java       - 切换到 Java 工作流版本 (端口 7880,容器内构建)
    python     - 切换到 Python 工作流版本 (端口 7880,使用镜像)
    status     - 查看当前使用的工作流版本

${YELLOW}示例:${NC}
    $0 java      # 切换到 Java 版本
    $0 python    # 切换到 Python 版本（稳定参考版本）
    $0 status    # 查看当前版本

${YELLOW}说明:${NC}
    - Python 版本: 使用官方镜像，稳定的参考实现
    - Java 版本: 从 core-workflow-java 目录构建，开发版本
    - 两个版本使用相同的容器名和端口 (7880)
    - Console-Hub 自动连接到 core-workflow:7880

EOF
}

get_current_version() {
    cd "$DOCKER_DIR"
    
    if [ -f .env ]; then
        local workflow_version=$(grep "^WORKFLOW_VERSION=" .env | cut -d'=' -f2)
        local build_context=$(grep "^WORKFLOW_BUILD_CONTEXT=" .env | cut -d'=' -f2)
        
        if [ -n "$build_context" ] && [ "$build_context" != "" ]; then
            echo "java"
        elif [[ "$workflow_version" == *"core-workflow"* ]]; then
            echo "python"
        else
            echo "unknown"
        fi
    else
        echo "unknown"
    fi
}

show_status() {
    print_info "检查当前工作流版本..."
    
    local current_version=$(get_current_version)
    
    echo ""
    echo "======================================"
    
    if [ "$current_version" == "java" ]; then
        echo -e "${GREEN}当前版本: Java Workflow${NC}"
        echo "容器: astron-agent-core-workflow"
        echo "端口: 7880"
        echo "镜像: 从 ../../core-workflow-java 构建"
        echo "状态: 开发版本 (可修改)"
    elif [ "$current_version" == "python" ]; then
        echo -e "${YELLOW}当前版本: Python Workflow${NC}"
        echo "容器: astron-agent-core-workflow"
        echo "端口: 7880"
        echo "镜像: ghcr.io/iflytek/astron-agent/core-workflow:latest"
        echo "状态: 稳定参考版本 (禁止修改)"
    else
        echo -e "${RED}当前版本: 未知${NC}"
        echo "请检查 .env 文件配置"
    fi
    
    echo "======================================"
    echo ""
    
    # 检查容器状态
    print_info "检查容器运行状态..."
    cd "$DOCKER_DIR"
    
    docker compose ps core-workflow
    echo ""
}

switch_to_java() {
    print_info "切换到 Java Workflow 版本..."
    
    cd "$DOCKER_DIR"
    
    # 1. 更新 .env 文件
    print_info "更新 .env 配置文件..."
    
    # 更新 WORKFLOW_VERSION 为空(使用 build)
    sed -i.bak 's|^WORKFLOW_VERSION=.*|WORKFLOW_VERSION=|' .env
    sed -i.bak 's|^WORKFLOW_BUILD_CONTEXT=.*|WORKFLOW_BUILD_CONTEXT=../../core-workflow-java|' .env
    sed -i.bak 's|^WORKFLOW_DOCKERFILE=.*|WORKFLOW_DOCKERFILE=Dockerfile|' .env
    
    # 更新 console-hub 的 workflow URL
    sed -i.bak 's|WORKFLOW_CHAT_URL=.*|WORKFLOW_CHAT_URL=http://core-workflow:7880/api/v1/workflow/chat/stream|' .env
    sed -i.bak 's|WORKFLOW_DEBUG_URL=.*|WORKFLOW_DEBUG_URL=http://core-workflow:7880/api/v1/workflow/chat/stream|' .env
    sed -i.bak 's|WORKFLOW_RESUME_URL=.*|WORKFLOW_RESUME_URL=http://core-workflow:7880/api/v1/workflow/chat/resume|' .env
    
    print_success ".env 文件已更新"
    
    # 2. 停止并重建 core-workflow
    print_info "重建 Java Workflow 容器..."
    docker compose stop core-workflow
    docker compose rm -f core-workflow
    docker compose build core-workflow
    docker compose up -d core-workflow
    
    # 3. 重启 console-hub (应用新的环境变量)
    print_info "重启 console-hub..."
    docker compose stop console-hub
    docker compose rm -f console-hub
    docker compose up -d console-hub
    
    # 4. 等待服务启动
    print_info "等待服务启动..."
    sleep 10
    
    print_success "✅ 成功切换到 Java Workflow 版本"
    echo ""
    echo "======================================"
    echo -e "${GREEN}Java Workflow 已激活${NC}"
    echo "容器: astron-agent-core-workflow"
    echo "端口: 7880"
    echo "查看日志: docker logs -f astron-agent-core-workflow"
    echo "======================================"
}

switch_to_python() {
    print_info "切换到 Python Workflow 版本..."
    
    cd "$DOCKER_DIR"
    
    # 1. 更新 .env 文件
    print_info "更新 .env 配置文件..."
    
    # 更新 WORKFLOW_VERSION 为 Python 镜像
    sed -i.bak 's|^WORKFLOW_VERSION=.*|WORKFLOW_VERSION=ghcr.io/iflytek/astron-agent/core-workflow:latest|' .env
    sed -i.bak 's|^WORKFLOW_BUILD_CONTEXT=.*|WORKFLOW_BUILD_CONTEXT=|' .env
    sed -i.bak 's|^WORKFLOW_DOCKERFILE=.*|WORKFLOW_DOCKERFILE=|' .env
    
    # 更新 console-hub 的 workflow URL
    sed -i.bak 's|WORKFLOW_CHAT_URL=.*|WORKFLOW_CHAT_URL=http://core-workflow:7880/workflow/v1/chat/completions|' .env
    sed -i.bak 's|WORKFLOW_DEBUG_URL=.*|WORKFLOW_DEBUG_URL=http://core-workflow:7880/workflow/v1/chat/completions|' .env
    sed -i.bak 's|WORKFLOW_RESUME_URL=.*|WORKFLOW_RESUME_URL=http://core-workflow:7880/workflow/v1/chat/resume|' .env
    
    print_success ".env 文件已更新"
    
    # 2. 停止并重建 core-workflow
    print_info "重建 Python Workflow 容器..."
    docker compose stop core-workflow
    docker compose rm -f core-workflow
    docker compose pull core-workflow
    docker compose up -d core-workflow
    
    # 3. 重启 console-hub (应用新的环境变量)
    print_info "重启 console-hub..."
    docker compose stop console-hub
    docker compose rm -f console-hub
    docker compose up -d console-hub
    
    # 4. 等待服务启动
    print_info "等待服务启动..."
    sleep 10
    
    print_success "✅ 成功切换到 Python Workflow 版本"
    echo ""
    echo "======================================"
    echo -e "${YELLOW}Python Workflow 已激活${NC}"
    echo "容器: astron-agent-core-workflow"
    echo "端口: 7880"
    echo "警告: 这是稳定参考版本，请勿修改代码"
    echo "查看日志: docker logs -f astron-agent-core-workflow"
    echo "======================================"
}

# 主函数
main() {
    case "${1:-}" in
        java)
            switch_to_java
            ;;
        python)
            switch_to_python
            ;;
        status)
            show_status
            ;;
        -h|--help|help)
            show_help
            ;;
        *)
            print_error "无效的命令: ${1:-}"
            echo ""
            show_help
            exit 1
            ;;
    esac
}

main "$@"
