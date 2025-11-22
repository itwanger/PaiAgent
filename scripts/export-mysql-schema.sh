#!/bin/bash

# 从本地 MySQL 导出最新的数据库结构
# 用途: 将本地开发的数据库变更同步到版本控制

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SQL_DIR="$PROJECT_ROOT/docker/astronAgent/mysql"

# MySQL 连接信息
MYSQL_HOST="localhost"
MYSQL_PORT="3306"
MYSQL_USER="root"
MYSQL_PASSWORD="123456"

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  导出 MySQL 数据库结构${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 检查 MySQL 连接
echo -e "${YELLOW}[1/7] 检查 MySQL 连接...${NC}"
if ! mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -e "SELECT 1;" &> /dev/null; then
    echo -e "${RED}✗ MySQL 连接失败${NC}"
    echo -e "${YELLOW}请确保 MySQL 服务正在运行${NC}"
    exit 1
fi
echo -e "${GREEN}✓ MySQL 连接成功${NC}"
echo ""

# 备份现有文件
echo -e "${YELLOW}[2/7] 备份现有 SQL 文件...${NC}"
BACKUP_DIR="$SQL_DIR/backup_$(date +%Y%m%d_%H%M%S)"
mkdir -p "$BACKUP_DIR"
cp "$SQL_DIR"/*.sql "$BACKUP_DIR/" 2>/dev/null || true
echo -e "${GREEN}✓ 已备份到: $BACKUP_DIR${NC}"
echo ""

# 导出 astron_console 数据库 (包含数据)
echo -e "${YELLOW}[3/8] 导出 astron_console 数据库...${NC}"
mysqldump -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" \
  --databases \
  --single-transaction \
  --quick \
  --lock-tables=false \
  astron_console > "$SQL_DIR/schema.sql"
echo -e "${GREEN}✓ schema.sql 导出成功 ($(du -h "$SQL_DIR/schema.sql" | cut -f1))${NC}"

# 导出 workflow 数据库 (包含数据)
echo -e "${YELLOW}[4/9] 导出 workflow 数据库...${NC}"
mysqldump -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" \
  --databases \
  --single-transaction \
  --quick \
  --lock-tables=false \
  workflow > "$SQL_DIR/workflow.sql"
echo -e "${GREEN}✓ workflow.sql 导出成功 ($(du -h "$SQL_DIR/workflow.sql" | cut -f1))${NC}"

# 导出 workflow_java 数据库 (包含数据)
echo -e "${YELLOW}[5/9] 导出 workflow_java 数据库...${NC}"
mysqldump -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" \
  --databases \
  --single-transaction \
  --quick \
  --lock-tables=false \
  workflow_java > "$SQL_DIR/init-workflow-java.sql"
echo -e "${GREEN}✓ init-workflow-java.sql 导出成功 ($(du -h "$SQL_DIR/init-workflow-java.sql" | cut -f1))${NC}"

# 导出 spark-link 数据库 (包含数据)
echo -e "${YELLOW}[6/9] 导出 spark-link 数据库...${NC}"
mysqldump -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" \
  --databases \
  --single-transaction \
  --quick \
  --lock-tables=false \
  "spark-link" > "$SQL_DIR/link.sql"
echo -e "${GREEN}✓ link.sql 导出成功 ($(du -h "$SQL_DIR/link.sql" | cut -f1))${NC}"

# 导出 agent 数据库 (包含数据)
echo -e "${YELLOW}[7/9] 导出 agent 数据库...${NC}"
mysqldump -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" \
  --databases \
  --single-transaction \
  --quick \
  --lock-tables=false \
  agent > "$SQL_DIR/agent.sql"
echo -e "${GREEN}✓ agent.sql 导出成功 ($(du -h "$SQL_DIR/agent.sql" | cut -f1))${NC}"

# 导出 tenant 数据库 (包含数据)
echo -e "${YELLOW}[8/9] 导出 tenant 数据库...${NC}"
mysqldump -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" \
  --databases \
  --single-transaction \
  --quick \
  --lock-tables=false \
  tenant > "$SQL_DIR/tenant.sql"
echo -e "${GREEN}✓ tenant.sql 导出成功 ($(du -h "$SQL_DIR/tenant.sql" | cut -f1))${NC}"

# 导出 spark-前缀的数据库 (如果存在数据的话)
echo -e "${YELLOW}[9/9] 检查其他数据库...${NC}"
for spark_db in "spark-workflow" "spark-agent" "spark-tenant"; do
    table_count=$(mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -e "SELECT COUNT(TABLE_NAME) FROM information_schema.tables WHERE table_schema='$spark_db';" 2>/dev/null | tail -1)
    if [ "$table_count" -gt 0 ]; then
        echo -e "${BLUE}导出 $spark_db (包含 $table_count 个表)...${NC}"
        output_file="$SQL_DIR/${spark_db#spark-}-spark.sql"
        mysqldump -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" \
          --databases \
          --single-transaction \
          --quick \
          --lock-tables=false \
          "$spark_db" > "$output_file"
        echo -e "${GREEN}✓ $output_file 导出成功${NC}"
    fi
done

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  导出完成！${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

echo -e "${CYAN}📁 导出的文件:${NC}"
echo -e "  - ${YELLOW}schema.sql${NC}            (astron_console, 结构+数据)"
echo -e "  - ${YELLOW}workflow.sql${NC}          (workflow, 结构+数据)"
echo -e "  - ${YELLOW}init-workflow-java.sql${NC} (workflow_java, 结构+数据)"
echo -e "  - ${YELLOW}link.sql${NC}              (spark-link, 结构+数据)"
echo -e "  - ${YELLOW}agent.sql${NC}             (agent, 结构+数据)"
echo -e "  - ${YELLOW}tenant.sql${NC}            (tenant, 结构+数据)"
echo ""

echo -e "${CYAN}💾 备份位置:${NC}"
echo -e "  ${YELLOW}$BACKUP_DIR${NC}"
echo ""

echo -e "${CYAN}📊 文件大小:${NC}"
ls -lh "$SQL_DIR"/*.sql | grep -v backup | awk '{print "  - " $9 ": " $5}'
echo ""

echo -e "${YELLOW}⚠️  注意事项:${NC}"
echo "1. 所有数据库导出都包含完整的表结构和数据"
echo "2. schema.sql 包含 astron_console 的所有配置数据"
echo "3. workflow.sql/link.sql 等包含工具、流程等配置信息"
echo "4. 原有文件已备份到 backup 目录"
echo "5. 如需恢复，请运行: ./scripts/init-local-mysql.sh"
echo ""
