#!/bin/bash

# 本地 MySQL 数据库初始化脚本
# 用途: 在本地 MySQL 中创建所需的数据库和表

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# MySQL 配置
MYSQL_HOST="localhost"
MYSQL_PORT="3306"
MYSQL_USER="root"
MYSQL_PASSWORD="123456"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SQL_DIR="$PROJECT_ROOT/docker/astronAgent/mysql"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  本地 MySQL 数据库初始化${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 检查 MySQL 客户端是否安装
if ! command -v mysql &> /dev/null; then
    echo -e "${RED}错误: 未找到 mysql 命令${NC}"
    echo -e "${YELLOW}请先安装 MySQL 客户端:${NC}"
    echo -e "${CYAN}  brew install mysql-client${NC}"
    echo -e "${CYAN}  echo 'export PATH=\"/opt/homebrew/opt/mysql-client/bin:\$PATH\"' >> ~/.zshrc${NC}"
    echo -e "${CYAN}  source ~/.zshrc${NC}"
    exit 1
fi

# 测试 MySQL 连接
echo -e "${YELLOW}[1/6] 测试 MySQL 连接...${NC}"
if mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -e "SELECT 1;" &> /dev/null; then
    echo -e "${GREEN}✓ MySQL 连接成功${NC}"
else
    echo -e "${RED}✗ MySQL 连接失败${NC}"
    echo -e "${YELLOW}请检查:${NC}"
    echo -e "  1. MySQL 服务是否启动: ${CYAN}brew services list | grep mysql${NC}"
    echo -e "  2. 用户名密码是否正确: ${CYAN}root / 123456${NC}"
    echo -e "  3. 启动 MySQL: ${CYAN}brew services start mysql${NC}"
    exit 1
fi
echo ""

# 创建数据库
echo -e "${YELLOW}[2/6] 创建数据库...${NC}"

DATABASES=(
    "astron_console"
    "spark-link"
    "spark-workflow"
    "spark-agent"
    "spark-tenant"
)

for db in "${DATABASES[@]}"; do
    echo -e "${BLUE}创建数据库: ${db}${NC}"
    mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" <<EOF
CREATE DATABASE IF NOT EXISTS \`${db}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
EOF
    echo -e "${GREEN}✓ ${db} 创建成功${NC}"
done
echo ""

# 导入主数据库 schema
echo -e "${YELLOW}[3/6] 导入 astron_console 数据库表结构...${NC}"
if [ -f "$SQL_DIR/schema.sql" ]; then
    echo -e "${BLUE}正在导入: schema.sql (可能需要几分钟)...${NC}"
    mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" astron_console < "$SQL_DIR/schema.sql"
    echo -e "${GREEN}✓ schema.sql 导入成功${NC}"
else
    echo -e "${YELLOW}⚠ schema.sql 文件不存在，跳过${NC}"
fi
echo ""

# 导入 workflow 表
echo -e "${YELLOW}[4/6] 导入 spark-workflow 表结构...${NC}"
if [ -f "$SQL_DIR/workflow.sql" ]; then
    echo -e "${BLUE}正在导入: workflow.sql${NC}"
    mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" spark-workflow < "$SQL_DIR/workflow.sql"
    echo -e "${GREEN}✓ workflow.sql 导入成功${NC}"
else
    echo -e "${YELLOW}⚠ workflow.sql 文件不存在，跳过${NC}"
fi
echo ""

# 导入 link 表
echo -e "${YELLOW}[5/6] 导入 spark-link 表结构...${NC}"
if [ -f "$SQL_DIR/link.sql" ]; then
    echo -e "${BLUE}正在导入: link.sql${NC}"
    mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" spark-link < "$SQL_DIR/link.sql"
    echo -e "${GREEN}✓ link.sql 导入成功${NC}"
else
    echo -e "${YELLOW}⚠ link.sql 文件不存在，跳过${NC}"
fi
echo ""

# 导入其他表
echo -e "${YELLOW}[6/6] 导入其他表结构...${NC}"

SQL_FILES=(
    "agent.sql:spark-agent"
    "tenant.sql:spark-tenant"
)

for entry in "${SQL_FILES[@]}"; do
    IFS=':' read -r sql_file db_name <<< "$entry"
    
    if [ -f "$SQL_DIR/$sql_file" ]; then
        echo -e "${BLUE}正在导入: $sql_file → $db_name${NC}"
        mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$db_name" < "$SQL_DIR/$sql_file"
        echo -e "${GREEN}✓ $sql_file 导入成功${NC}"
    else
        echo -e "${YELLOW}⚠ $sql_file 文件不存在，跳过${NC}"
    fi
done
echo ""

# 验证数据库
echo -e "${YELLOW}验证数据库创建...${NC}"
echo -e "${CYAN}已创建的数据库:${NC}"
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -e "SHOW DATABASES;" | grep -E "astron|spark"
echo ""

# 显示主要表
echo -e "${CYAN}astron_console 主要表:${NC}"
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" astron_console -e "SHOW TABLES;" | head -20
echo ""

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  数据库初始化完成！${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

echo -e "${CYAN}数据库连接信息:${NC}"
echo -e "  Host: ${YELLOW}localhost${NC}"
echo -e "  Port: ${YELLOW}3306${NC}"
echo -e "  User: ${YELLOW}root${NC}"
echo -e "  Password: ${YELLOW}123456${NC}"
echo ""

echo -e "${CYAN}已创建的数据库:${NC}"
for db in "${DATABASES[@]}"; do
    echo -e "  - ${GREEN}${db}${NC}"
done
echo ""

echo -e "${CYAN}下一步:${NC}"
echo -e "  1. 在 IDEA 中配置环境变量:"
echo -e "     ${YELLOW}MYSQL_URL=jdbc:mysql://localhost:3306/astron_console${NC}"
echo -e "     ${YELLOW}MYSQL_USER=root${NC}"
echo -e "     ${YELLOW}MYSQL_PASSWORD=123456${NC}"
echo ""
echo -e "  2. 启动 console-hub 进行调试"
echo ""
