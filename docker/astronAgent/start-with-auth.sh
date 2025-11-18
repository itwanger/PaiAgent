#!/bin/bash
# ============================================================================
# PaiAgent - 一键启动脚本（完整版 - 包含 Casdoor OAuth 认证）
# ============================================================================
# 使用方法:
#   ./start-with-auth.sh         # 启动所有服务（包括 Casdoor 认证）
#   ./start-with-auth.sh --clean # 清理所有数据并重新启动
# ============================================================================

set -e

cd "$(dirname "$0")"

echo "=============================================================================="
echo "  🚀 PaiAgent - 一键启动（完整版 - 包含 Casdoor OAuth）"
echo "=============================================================================="
echo ""

# 检查参数
CLEAN_MODE=false
if [ "$1" = "--clean" ]; then
  CLEAN_MODE=true
  echo "⚠️  警告: 清理模式已启用，将删除所有数据！"
  read -p "确认继续? (yes/no): " confirm
  if [ "$confirm" != "yes" ]; then
    echo "❌ 已取消操作"
    exit 1
  fi
  echo ""
fi

# 清理模式
if [ "$CLEAN_MODE" = true ]; then
  echo "🗑️  停止所有服务并清理数据..."
  docker compose -f docker-compose-with-auth.yaml down -v
  echo "✅ 清理完成"
  echo ""
fi

# 检查 .env 文件
if [ ! -f ".env" ]; then
  if [ -f ".env.example" ]; then
    echo "📝 复制 .env.example 到 .env..."
    cp .env.example .env
  else
    echo "❌ 错误: 未找到 .env 文件，请先创建！"
    exit 1
  fi
fi

# 启动服务
echo "🚀 启动所有服务（包括 Casdoor 认证）..."
docker compose -f docker-compose-with-auth.yaml up -d

echo ""
echo "⏳ 等待服务启动 (30秒)..."
sleep 30

echo ""
echo "🔍 检查服务状态..."
docker compose -f docker-compose-with-auth.yaml ps

echo ""
echo "⏳ 等待 Casdoor 完全启动..."
CASDOOR_READY=false
for i in {1..30}; do
  if docker exec astron-agent-casdoor curl -sf http://localhost:8000 > /dev/null 2>&1; then
    echo "✅ Casdoor 已就绪 (尝试 $i 次)"
    CASDOOR_READY=true
    break
  fi
  printf "   等待中... (%2d/30)\r" "$i"
  sleep 2
done

echo ""
if [ "$CASDOOR_READY" = false ]; then
  echo "⚠️  Casdoor 启动超时，但服务可能仍在初始化..."
  echo "💡 提示: 可以运行 'docker logs astron-agent-casdoor' 查看日志"
fi

echo ""
echo "⏳ 等待 console-hub 完全启动..."
CONSOLE_HUB_READY=false
for i in {1..30}; do
  if docker exec astron-agent-console-hub curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "✅ console-hub 已就绪 (尝试 $i 次)"
    CONSOLE_HUB_READY=true
    break
  fi
  printf "   等待中... (%2d/30)\r" "$i"
  sleep 2
done

echo ""
if [ "$CONSOLE_HUB_READY" = false ]; then
  echo "⚠️  console-hub 启动超时，但服务可能仍在初始化..."
  echo "💡 提示: 可以运行 'docker logs astron-agent-console-hub' 查看日志"
fi

echo ""
echo "🔄 刷新 nginx DNS 缓存..."
docker compose -f docker-compose-with-auth.yaml restart nginx
sleep 5

echo ""
echo "🧪 测试服务连接..."
if curl -sf http://localhost/console-api/actuator/health > /dev/null 2>&1; then
  echo "✅ 后端服务连接成功！"
else
  echo "⚠️  后端服务连接失败，再等待10秒重试..."
  sleep 10
  if curl -sf http://localhost/console-api/actuator/health > /dev/null 2>&1; then
    echo "✅ 后端服务连接成功！"
  else
    echo "❌ 后端服务仍无法连接"
  fi
fi

echo ""
echo "📊 检查数据库状态..."
TABLE_COUNT=$(docker exec astron-agent-mysql mysql -uroot -proot123 -e "SELECT COUNT(*) as cnt FROM information_schema.tables WHERE table_schema='astron_console';" 2>/dev/null | tail -1)
if [ "$TABLE_COUNT" = "145" ]; then
  echo "✅ 数据库表数量正常 ($TABLE_COUNT 个表)"
else
  echo "⚠️  数据库表数量异常: $TABLE_COUNT (期望: 145)"
fi

echo ""
echo "=============================================================================="
echo "  ✅ 启动完成！"
echo "=============================================================================="
echo ""
echo "🌐 访问地址:"
echo "   - 前端应用:     http://localhost"
echo "   - Casdoor 控制台: http://localhost:8000"
echo ""
echo "👤 默认管理员账号:"
echo "   - Casdoor 账号: admin / 123"
echo "   - Casdoor 组织: built-in"
echo ""
echo "📋 常用命令:"
echo "   查看服务状态:      docker compose -f docker-compose-with-auth.yaml ps"
echo "   查看后端日志:      docker logs astron-agent-console-hub --tail 50"
echo "   查看 Casdoor 日志: docker logs astron-agent-casdoor --tail 50"
echo "   查看 nginx 日志:   docker logs astron-agent-nginx --tail 30"
echo "   查看所有日志:      docker compose -f docker-compose-with-auth.yaml logs -f"
echo "   停止所有服务:      docker compose -f docker-compose-with-auth.yaml down"
echo "   完全清理:          docker compose -f docker-compose-with-auth.yaml down -v"
echo ""
echo "❓ 如果遇到问题，请查看: FAQ.md"
echo ""
echo "💡 重要提示:"
echo "   - 首次启动后，请访问 http://localhost:8000 配置 Casdoor"
echo "   - 确保在 Casdoor 中创建 astronAgent 应用"
echo "   - 配置回调地址: http://localhost/callback"
echo ""
