# 本地 MinIO 快速启动指南

## 方式 1: 使用脚本启动 (推荐)

### 1️⃣ 启动本地 MinIO

打开一个新终端窗口:
```bash
cd /Users/itwanger/Documents/GitHub/PaiAgent
./scripts/start-local-minio.sh
```

这会:
- ✅ 自动安装 MinIO (如果未安装)
- ✅ 在端口 9000 启动 API
- ✅ 在端口 9001 启动管理控制台
- ✅ 使用默认凭据: `minioadmin` / `minioadmin`

### 2️⃣ 访问 MinIO 控制台

浏览器打开: http://localhost:9001

登录:
- Access Key: `minioadmin`
- Secret Key: `minioadmin`

### 3️⃣ 创建 Bucket

在 MinIO 控制台:
1. 点击 "Buckets" → "Create Bucket"
2. Bucket Name: `astron-agent`
3. 点击 "Create Bucket"

### 4️⃣ 在 IDEA 中启动 console-hub

MinIO 启动后,在 IDEA 中:
- Active profiles: `local`
- 点击 Debug

应该能正常连接本地 MinIO 了! ✅

---

## 方式 2: 手动启动 MinIO

```bash
# 安装 MinIO
brew install minio/stable/minio

# 创建数据目录
mkdir -p ~/minio/data

# 启动 MinIO (前台运行)
export MINIO_ROOT_USER=minioadmin
export MINIO_ROOT_PASSWORD=minioadmin
minio server ~/minio/data --console-address ":9001" --address ":9000"
```

---

## 方式 3: 后台运行 MinIO

```bash
# 启动到后台
export MINIO_ROOT_USER=minioadmin
export MINIO_ROOT_PASSWORD=minioadmin
nohup minio server ~/minio/data \
    --console-address ":9001" \
    --address ":9000" > ~/minio/minio.log 2>&1 &

# 查看日志
tail -f ~/minio/minio.log

# 停止服务
pkill minio
```

---

## 验证 MinIO 是否运行

```bash
# 检查端口
lsof -i :9000
lsof -i :9001

# 测试 API
curl http://localhost:9000/minio/health/live
```

---

## application-local.yml 配置

已经配置为连接本地 MinIO:

```yaml
s3:
  endpoint: http://localhost:9000
  remoteEndpoint: http://localhost:9000
  accessKey: minioadmin
  secretKey: minioadmin
  bucket: astron-agent
  presignExpirySeconds: 600
  enablePublicRead: false
```

---

## 停止本地 MinIO

在运行 MinIO 的终端按 `Ctrl+C`

或者:
```bash
pkill minio
```

---

## 常见问题

### ❌ 端口冲突

如果端口 9000 或 9001 被占用:

```bash
# 查看占用端口的进程
lsof -i :9000
lsof -i :9001

# 杀掉进程
kill -9 <PID>
```

### ❌ Bucket 不存在

启动后在 MinIO 控制台手动创建 bucket `astron-agent`

或者使用 MinIO Client (mc):
```bash
brew install minio/stable/mc
mc alias set local http://localhost:9000 minioadmin minioadmin
mc mb local/astron-agent
```

---

## 推荐工作流

1. **启动 MinIO** (运行 `./scripts/start-local-minio.sh`)
2. **创建 Bucket** (在控制台 http://localhost:9001)
3. **在 IDEA 中启动 console-hub** (profile: `local`)
4. **开始调试!** 🎉
