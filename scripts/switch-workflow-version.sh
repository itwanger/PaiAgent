#!/bin/bash

# 工作流版本切换脚本
# 用途：在 Java 和 Python 工作流版本之间快速切换

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DOCKER_DIR="$PROJECT_ROOT/docker/astronAgent"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 打印带颜色的消息
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

# 显示帮助信息
show_help() {
    cat << EOF
${GREEN}工作流版本切换脚本${NC}

${YELLOW}用法:${NC}
    $0 [java|python|status]

${YELLOW}命令:${NC}
    java       - 切换到 Java 工作流版本 (端口 7881)
    python     - 切换到 Python 工作流版本 (端口 7880)
    status     - 查看当前使用的工作流版本

${YELLOW}示例:${NC}
    $0 java      # 切换到 Java 版本
    $0 python    # 切换到 Python 版本（稳定参考版本）
    $0 status    # 查看当前版本

${YELLOW}说明:${NC}
    - Python 版本 (端口 7880): 稳定的参考实现，不应修改
    - Java 版本 (端口 7881): 开发版本，可以自由修改
    - 切换版本会修改 console-hub 的环境变量并重启相关容器

EOF
}

# 获取当前版本
get_current_version() {
    cd "$DOCKER_DIR"
    
    # 读取 .env 文件中的 WORKFLOW_CHAT_URL
    if [ -f .env ]; then
        local workflow_url=$(grep "^WORKFLOW_CHAT_URL=" .env | cut -d'=' -f2)
        
        if [[ "$workflow_url" == *"core-workflow-java:7881"* ]]; then
            echo "java"
        elif [[ "$workflow_url" == *"core-workflow:7880"* ]]; then
            echo "python"
        else
            echo "unknown"
        fi
    else
        echo "unknown"
    fi
}

# 显示当前版本状态
show_status() {
    print_info "检查当前工作流版本..."
    
    local current_version=$(get_current_version)
    
    echo ""
    echo "======================================"
    
    if [ "$current_version" == "java" ]; then
        echo -e "${GREEN}当前版本: Java Workflow${NC}"
        echo "端口: 7881"
        echo "服务: core-workflow-java"
        echo "状态: 开发版本 (可修改)"
    elif [ "$current_version" == "python" ]; then
        echo -e "${YELLOW}当前版本: Python Workflow${NC}"
        echo "端口: 7880"
        echo "服务: core-workflow"
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
    
    local java_status=$(docker compose -f docker-compose.workflow-dual.yml ps core-workflow-java --format json 2>/dev/null | grep -o '"State":"[^"]*"' | cut -d'"' -f4 || echo "not found")
    local python_status=$(docker compose ps core-workflow --format json 2>/dev/null | grep -o '"State":"[^"]*"' | cut -d'"' -f4 || echo "not found")
    
    echo "Java Workflow (7881):   $java_status"
    echo "Python Workflow (7880): $python_status"
    echo ""
}

# 切换到 Java 版本
switch_to_java() {
    print_info "切换到 Java Workflow 版本..."
    
    cd "$DOCKER_DIR"
    
    # 1. 更新 .env 文件
    print_info "更新 .env 配置文件..."
    
    if [ -f .env ]; then
        # 备份原文件
        cp .env .env.backup
        
        # 替换 URL
        sed -i '' 's|WORKFLOW_CHAT_URL=.*|WORKFLOW_CHAT_URL=http://core-workflow-java:7881/api/v1/workflow/chat/stream|' .env
        sed -i '' 's|WORKFLOW_DEBUG_URL=.*|WORKFLOW_DEBUG_URL=http://core-workflow-java:7881/api/v1/workflow/chat/stream|' .env
        sed -i '' 's|WORKFLOW_RESUME_URL=.*|WORKFLOW_RESUME_URL=http://core-workflow-java:7881/api/v1/workflow/chat/resume|' .env
        
        print_success ".env 文件已更新"
    else
        print_warning ".env 文件不存在，创建新文件..."
        cat >> .env << 'EOF'
# Workflow Service URLs (Java Version)
WORKFLOW_CHAT_URL=http://core-workflow-java:7881/api/v1/workflow/chat/stream
WORKFLOW_DEBUG_URL=http://core-workflow-java:7881/api/v1/workflow/chat/stream
WORKFLOW_RESUME_URL=http://core-workflow-java:7881/api/v1/workflow/chat/resume
EOF
    fi
    
    # 2. 更新 docker-compose.yaml
    print_info "更新 docker-compose.yaml..."
    
    if grep -q "WORKFLOW_CHAT_URL.*core-workflow-java:7881" docker-compose.yaml; then
        print_success "docker-compose.yaml 已配置为 Java 版本"
    else
        print_warning "需要手动检查 docker-compose.yaml 中 console-hub 的环境变量"
    fi
    
    # 3. 确保 Java Workflow 容器正在运行
    print_info "启动 Java Workflow 容器..."
    docker compose -f docker-compose.workflow-dual.yml up -d core-workflow-java
    
    # 4. 重启 console-hub 以应用新配置
    print_info "重启 console-hub 服务..."
    docker compose stop console-hub
    docker compose rm -f console-hub
    docker compose up -d console-hub
    
    # 5. 等待服务启动
    print_info "等待服务启动..."
    sleep 5
    
    # 6. 验证配置
    print_info "验证配置..."
    local workflow_url=$(docker exec astron-agent-console-hub env | grep WORKFLOW_CHAT_URL | cut -d'=' -f2)
    
    if [[ "$workflow_url" == *"core-workflow-java:7881"* ]]; then
        print_success "✅ 成功切换到 Java Workflow 版本"
        echo ""
        echo "======================================"
        echo -e "${GREEN}Java Workflow 已激活${NC}"
        echo "端口: 7881"
        echo "查看日志: docker logs -f astron-agent-core-workflow-java"
        echo "======================================"
    else
        print_error "❌ 切换失败，请检查配置"
        echo "当前 URL: $workflow_url"
        exit 1
    fi
}

# 切换到 Python 版本
switch_to_python() {
    print_info "切换到 Python Workflow 版本..."
    
    cd "$DOCKER_DIR"
    
    # 1. 更新 .env 文件
    print_info "更新 .env 配置文件..."
    
    if [ -f .env ]; then
        # 备份原文件
        cp .env .env.backup
        
        # 替换 URL
        sed -i '' 's|WORKFLOW_CHAT_URL=.*|WORKFLOW_CHAT_URL=http://core-workflow:7880/workflow/v1/chat/completions|' .env
        sed -i '' 's|WORKFLOW_DEBUG_URL=.*|WORKFLOW_DEBUG_URL=http://core-workflow:7880/workflow/v1/chat/completions|' .env
        sed -i '' 's|WORKFLOW_RESUME_URL=.*|WORKFLOW_RESUME_URL=http://core-workflow:7880/workflow/v1/chat/resume|' .env
        
        print_success ".env 文件已更新"
    else
        print_warning ".env 文件不存在，创建新文件..."
        cat >> .env << 'EOF'
# Workflow Service URLs (Python Version)
WORKFLOW_CHAT_URL=http://core-workflow:7880/workflow/v1/chat/completions
WORKFLOW_DEBUG_URL=http://core-workflow:7880/workflow/v1/chat/completions
WORKFLOW_RESUME_URL=http://core-workflow:7880/workflow/v1/chat/resume
EOF
    fi
    
    # 2. 更新 docker-compose.yaml
    print_info "更新 docker-compose.yaml..."
    
    if grep -q "WORKFLOW_CHAT_URL.*core-workflow:7880" docker-compose.yaml; then
        print_success "docker-compose.yaml 已配置为 Python 版本"
    else
        print_warning "需要手动检查 docker-compose.yaml 中 console-hub 的环境变量"
    fi
    
    # 3. 确保 Python Workflow 容器正在运行
    print_info "启动 Python Workflow 容器..."
    docker compose up -d core-workflow
    
    # 4. 重启 console-hub 以应用新配置
    print_info "重启 console-hub 服务..."
    docker compose stop console-hub
    docker compose rm -f console-hub
    docker compose up -d console-hub
    
    # 5. 等待服务启动
    print_info "等待服务启动..."
    sleep 5
    
    # 6. 验证配置
    print_info "验证配置..."
    local workflow_url=$(docker exec astron-agent-console-hub env | grep WORKFLOW_CHAT_URL | cut -d'=' -f2)
    
    if [[ "$workflow_url" == *"core-workflow:7880"* ]]; then
        print_success "✅ 成功切换到 Python Workflow 版本"
        echo ""
        echo "======================================"
        echo -e "${YELLOW}Python Workflow 已激活${NC}"
        echo "端口: 7880"
        echo "警告: 这是稳定参考版本，请勿修改代码"
        echo "查看日志: docker logs -f astron-agent-core-workflow"
        echo "======================================"
    else
        print_error "❌ 切换失败，请检查配置"
        echo "当前 URL: $workflow_url"
        exit 1
    fi
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

# 执行主函数
main "$@"
