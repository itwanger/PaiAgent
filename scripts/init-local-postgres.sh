#!/bin/bash

# 本地 PostgreSQL 数据库初始化脚本
# 用途: 在本地 PostgreSQL 中创建 workflow_java 数据库

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# PostgreSQL 配置
POSTGRES_HOST="localhost"
POSTGRES_PORT="5432"
POSTGRES_USER="postgres"
POSTGRES_PASSWORD="123456"
POSTGRES_DB="workflow_java"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  本地 PostgreSQL 数据库初始化${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 检查 psql 客户端是否安装
if ! command -v psql &> /dev/null; then
    echo -e "${RED}错误: 未找到 psql 命令${NC}"
    echo -e "${YELLOW}请先安装 PostgreSQL 客户端:${NC}"
    echo -e "${CYAN}  brew install postgresql@14${NC}"
    echo -e "${CYAN}  echo 'export PATH=\"/opt/homebrew/opt/postgresql@14/bin:\$PATH\"' >> ~/.zshrc${NC}"
    echo -e "${CYAN}  source ~/.zshrc${NC}"
    exit 1
fi

# 测试 PostgreSQL 连接
echo -e "${YELLOW}[1/2] 测试 PostgreSQL 连接...${NC}"
export PGPASSWORD="$POSTGRES_PASSWORD"

if psql -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" -U "$POSTGRES_USER" -d postgres -c "SELECT 1;" &> /dev/null; then
    echo -e "${GREEN}✓ PostgreSQL 连接成功${NC}"
else
    echo -e "${RED}✗ PostgreSQL 连接失败${NC}"
    echo -e "${YELLOW}请检查:${NC}"
    echo -e "  1. PostgreSQL 服务是否启动: ${CYAN}brew services list | grep postgresql${NC}"
    echo -e "  2. 用户名密码是否正确: ${CYAN}postgres / 123456${NC}"
    echo -e "  3. 启动 PostgreSQL: ${CYAN}brew services start postgresql@14${NC}"
    echo -e "  4. 如果密码不对，重置密码:"
    echo -e "     ${CYAN}psql -U postgres -d postgres${NC}"
    echo -e "     ${CYAN}ALTER USER postgres WITH PASSWORD '123456';${NC}"
    exit 1
fi
echo ""

# 创建数据库
echo -e "${YELLOW}[2/2] 创建 workflow_java 数据库...${NC}"

# 检查数据库是否已存在
if psql -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" -U "$POSTGRES_USER" -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$POSTGRES_DB'" | grep -q 1; then
    echo -e "${YELLOW}⚠ 数据库 $POSTGRES_DB 已存在${NC}"
else
    psql -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" -U "$POSTGRES_USER" -d postgres <<EOF
CREATE DATABASE $POSTGRES_DB
    WITH 
    OWNER = $POSTGRES_USER
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.UTF-8'
    LC_CTYPE = 'en_US.UTF-8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;
EOF
    echo -e "${GREEN}✓ 数据库 $POSTGRES_DB 创建成功${NC}"
fi
echo ""

# 验证数据库
echo -e "${YELLOW}验证数据库创建...${NC}"
echo -e "${CYAN}已创建的数据库:${NC}"
psql -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" -U "$POSTGRES_USER" -d postgres -c "\l" | grep workflow
echo ""

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  PostgreSQL 数据库初始化完成！${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

echo -e "${CYAN}数据库连接信息:${NC}"
echo -e "  Host: ${YELLOW}localhost${NC}"
echo -e "  Port: ${YELLOW}5432${NC}"
echo -e "  Database: ${YELLOW}workflow_java${NC}"
echo -e "  User: ${YELLOW}postgres${NC}"
echo -e "  Password: ${YELLOW}123456${NC}"
echo ""

echo -e "${CYAN}JDBC URL:${NC}"
echo -e "  ${YELLOW}jdbc:postgresql://localhost:5432/workflow_java${NC}"
echo ""

echo -e "${CYAN}注意:${NC}"
echo -e "  - Java Workflow 使用 PostgreSQL 存储工作流定义"
echo -e "  - 表结构会在首次启动 Java Workflow 时自动创建 (JPA)"
echo ""

unset PGPASSWORD
