#!/bin/bash

# 启动本地 MinIO 服务
# 用途: 为本地调试提供对象存储服务

set -e

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

MINIO_DIR="$HOME/.local/minio"
MINIO_DATA_DIR="$MINIO_DIR/data"
MINIO_CONFIG_DIR="$MINIO_DIR/config"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  启动本地 MinIO 服务${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 1. 检查 MinIO 是否已安装
if ! command -v minio &> /dev/null; then
    echo -e "${YELLOW}MinIO 未安装，正在安装...${NC}"
    brew install minio/stable/minio
    echo -e "${GREEN}✓ MinIO 安装完成${NC}"
fi

# 2. 创建数据目录
mkdir -p "$MINIO_DATA_DIR"
mkdir -p "$MINIO_CONFIG_DIR"

echo -e "${BLUE}数据目录: $MINIO_DATA_DIR${NC}"
echo ""

# 3. 启动 MinIO
echo -e "${YELLOW}启动 MinIO 服务...${NC}"
echo -e "${BLUE}访问地址:${NC}"
echo -e "  API:     ${GREEN}http://localhost:9000${NC}"
echo -e "  Console: ${GREEN}http://localhost:9001${NC}"
echo -e "${BLUE}默认凭据:${NC}"
echo -e "  Access Key: ${GREEN}minioadmin${NC}"
echo -e "  Secret Key: ${GREEN}minioadmin${NC}"
echo ""

# 设置环境变量
export MINIO_ROOT_USER=minioadmin
export MINIO_ROOT_PASSWORD=minioadmin

# 启动 MinIO (前台运行,按 Ctrl+C 停止)
minio server "$MINIO_DATA_DIR" \
    --console-address ":9001" \
    --address ":9000"

# 如果要后台运行,使用:
# nohup minio server "$MINIO_DATA_DIR" \
#     --console-address ":9001" \
#     --address ":9000" > "$MINIO_DIR/minio.log" 2>&1 &
# 
# echo "MinIO 已在后台启动"
# echo "查看日志: tail -f $MINIO_DIR/minio.log"
# echo "停止服务: pkill minio"
