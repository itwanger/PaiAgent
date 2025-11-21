#!/bin/bash
# 智能重启脚本 - 自动处理 Docker 服务重启和 DNS 缓存问题
# 使用方法:
#   ./smart-restart.sh         # 方案1: 快速重启（保留所有数据）
#   ./smart-restart.sh rebuild # 方案2: 重建容器（保留数据）
#   ./smart-restart.sh full    # 方案3: 完全重建（⚠️ 删除所有数据）

set -e

cd "$(dirname "$0")"

echo "=================================================="
echo "  🚀 paiagent - 智能重启工具"
echo "=================================================="
echo ""

# 根据参数选择重启方案
case "$1" in
  "full")
    echo "📦 方案3: 完全重建（删除所有数据）"
    echo "⚠️  警告: 这将删除所有数据库数据、MinIO文件等！"
    read -p "确认继续? (yes/no): " confirm
    if [ "$confirm" != "yes" ]; then
      echo "❌ 已取消操作"
      exit 1
    fi
    echo ""
    echo "🛑 停止所有服务并删除数据卷..."
    docker compose down -v
    echo ""
    echo "🚀 重新启动所有服务..."
    docker compose up -d
    WAIT_TIME=30
    ;;
  "rebuild")
    echo "📦 方案2: 重建容器（保留数据）"
    echo "ℹ️  适用于: docker-compose.yaml 配置变更"
    echo ""
    echo "🛑 停止所有服务..."
    docker compose down
    echo ""
    echo "🚀 重新启动所有服务..."
    docker compose up -d
    WAIT_TIME=20
    ;;
  *)
    echo "♻️  方案1: 快速重启（保留所有）"
    echo "ℹ️  适用于: 代码修改、配置文件修改"
    echo ""
    echo "🔄 重启所有服务..."
    docker compose restart
    WAIT_TIME=15
    ;;
esac

echo ""
echo "⏳ 等待基础服务启动 (${WAIT_TIME}秒)..."
sleep $WAIT_TIME

echo ""
echo "🔍 检查服务状态..."
docker compose ps

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
  echo "⚠️  console-hub 启动超时，但仍会继续..."
  echo "💡 提示: 可以运行 'docker logs astron-agent-console-hub' 查看日志"
fi

echo ""
echo "🧪 测试服务连接..."
if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
  echo "✅ 后端服务连接成功！"
else
  echo "⚠️  后端服务连接失败，再等待10秒重试..."
  sleep 10
  if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
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
echo "=================================================="
echo "  ✅ 重启完成！"
echo "=================================================="
echo ""
echo "🌐 访问地址: "
echo "   前端开发服务器: http://localhost:3000 (需手动启动 npm run dev)"
echo "   后端 API:       http://localhost:8080"
echo "   工作流服务:     http://localhost:7880"
echo "👤 默认账号: admin / 123"
echo ""
echo "📋 常用命令:"
echo "   查看服务状态:      docker compose ps"
echo "   查看后端日志:      docker logs astron-agent-console-hub --tail 50"
echo "   查看工作流日志:    docker logs astron-agent-core-workflow --tail 50"
echo "   查看所有日志:      docker compose logs -f"
echo "   启动前端:          cd console/frontend && npm run dev"
echo ""
echo "❓ 如果仍有问题，请查看: FAQ.md"
echo ""
