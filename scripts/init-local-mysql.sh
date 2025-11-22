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

# 检查是否已存在数据库
echo -e "${YELLOW}检查现有数据库...${NC}"
EXISTING_DBS=$(mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -e "SHOW DATABASES;" 2>/dev/null | grep -E "astron_console|spark-" || true)

if [ -n "$EXISTING_DBS" ]; then
    echo -e "${YELLOW}发现以下数据库已存在:${NC}"
    echo "$EXISTING_DBS"
    echo ""
    echo -e "${YELLOW}请选择操作:${NC}"
    echo -e "  ${CYAN}1${NC} - 删除现有数据库并重新初始化 (⚠️  会丢失所有数据)"
    echo -e "  ${CYAN}2${NC} - 跳过已存在的数据库，只导入缺失的"
    echo -e "  ${CYAN}3${NC} - 取消操作"
    echo ""
    read -p "请输入选项 [1/2/3]: " choice
    
    case $choice in
        1)
            echo -e "${RED}警告: 将删除所有现有数据！${NC}"
            read -p "确认删除？输入 'yes' 继续: " confirm
            if [ "$confirm" != "yes" ]; then
                echo -e "${YELLOW}操作已取消${NC}"
                exit 0
            fi
            DROP_EXISTING=true
            ;;
        2)
            echo -e "${CYAN}将跳过已存在的数据库${NC}"
            DROP_EXISTING=false
            ;;
        3)
            echo -e "${YELLOW}操作已取消${NC}"
            exit 0
            ;;
        *)
            echo -e "${RED}无效选项，操作已取消${NC}"
            exit 1
            ;;
    esac
else
    DROP_EXISTING=false
fi
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
    if [ "$DROP_EXISTING" = true ]; then
        echo -e "${RED}删除现有数据库: ${db}${NC}"
        mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" <<EOF
DROP DATABASE IF EXISTS \`${db}\`;
EOF
    fi
    
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
    if mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" astron_console < "$SQL_DIR/schema.sql" 2>&1 | tee /tmp/mysql_import.log | grep -v "Warning"; then
        echo -e "${GREEN}✓ schema.sql 导入成功${NC}"
    else
        if grep -q "Duplicate entry" /tmp/mysql_import.log; then
            echo -e "${YELLOW}⚠ 检测到重复数据，部分数据已存在（这是正常的）${NC}"
        else
            echo -e "${RED}✗ schema.sql 导入失败${NC}"
            echo -e "${YELLOW}查看详细错误: cat /tmp/mysql_import.log${NC}"
            exit 1
        fi
    fi
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

# 调整 spark-link 数据库配置以适配本地环境
echo -e "${YELLOW}[7/7] 调整本地开发环境配置...${NC}"
echo -e "${BLUE}更新 spark-link.tools_schema 中的 AITools 服务地址...${NC}"
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" spark-link <<EOF
UPDATE tools_schema
SET open_api_schema = REPLACE(
  open_api_schema,
  'http://core-aitools:18668',
  'http://localhost:18668'
)
WHERE tool_id='tool@8b2262bef821000';
EOF

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ AITools 服务地址已更新为本地地址${NC}"
else
    echo -e "${YELLOW}⚠ AITools 服务地址更新失败（可能是数据不存在）${NC}"
fi

echo -e "${BLUE}更新 spark-workflow.workflow 中的 AI_APP_ID...${NC}"
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" spark-workflow <<EOF
UPDATE workflow
SET workflow = REPLACE(
  workflow,
  '680ab54f',
  'f740451b'
)
WHERE workflow LIKE '%680ab54f%';
EOF

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Workflow AI_APP_ID 已更新为 f740451b${NC}"
else
    echo -e "${YELLOW}⚠ Workflow AI_APP_ID 更新失败（可能是数据不存在）${NC}"
fi
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
echo -e "  1. 生成 Python 服务配置:"
echo -e "     ${YELLOW}./scripts/setup-python-local-debug.sh${NC}"
echo ""
echo -e "  2. 启动 Python 服务 (在 PyCharm/VSCode 中 Debug 启动):"
echo -e "     - Link Service:    ${YELLOW}core/plugin/link/main.py${NC}"
echo -e "     - AITools Service: ${YELLOW}core/plugin/aitools/main.py${NC}"
echo -e "     - Workflow Service:${YELLOW}core/workflow/main.py${NC}"
echo -e "     - Agent Service:   ${YELLOW}core/agent/main.py${NC}"
echo ""
echo -e "  3. 启动 Console Hub (在 IDEA 中,Active profiles: local)"
echo ""
echo -e "  4. 启动前端:"
echo -e "     ${YELLOW}cd console/frontend && npm run dev${NC}"
echo ""
