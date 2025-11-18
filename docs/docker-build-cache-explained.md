# Docker Build 缓存机制详解

## 📋 命令对比

### `docker compose build` vs `docker compose build --no-cache`

| 特性 | `docker compose build` | `docker compose build --no-cache` |
|------|----------------------|----------------------------------|
| **构建速度** | ⚡ 快速 (利用缓存层) | 🐌 慢速 (重新执行所有步骤) |
| **依赖下载** | 只下载变更的依赖 | 重新下载所有依赖 |
| **适用场景** | 日常开发迭代 | 首次构建、重大版本升级 |
| **缓存使用** | ✅ 使用 Docker 层缓存 | ❌ 忽略所有缓存 |
| **构建时间** | 1-5 分钟 | 10-30 分钟 |
| **推荐频率** | 每次代码修改后 | 每月/季度一次 |

---

## 🔍 Docker 分层缓存原理

### Dockerfile 层缓存机制

Docker 按 Dockerfile 的每条指令构建**镜像层**，每层都可以被缓存：

```dockerfile
FROM python:3.11-slim              # Layer 1: 基础镜像 (缓存)
WORKDIR /opt/core/workflow         # Layer 2: 工作目录 (缓存)
COPY requirements.txt .            # Layer 3: 依赖文件 (检查文件是否变化)
RUN pip install -r requirements.txt # Layer 4: 安装依赖 (如果 Layer 3 命中缓存，则复用)
COPY . .                           # Layer 5: 复制代码 (每次都变化)
CMD ["python", "main.py"]          # Layer 6: 启动命令 (缓存)
```

### 缓存命中规则

1. **基础镜像层**: 如果本地已有 `python:3.11-slim`，直接使用
2. **文件复制层**: 检查文件 **内容哈希值**
   - `requirements.txt` 未改动 → 缓存命中
   - `requirements.txt` 有修改 → 缓存失效，重新执行后续所有层
3. **RUN 指令**: 依赖上一层缓存
   - 上层缓存命中 + 指令未变 → 使用缓存
   - 上层缓存失效 → 重新执行

---

## 📊 实际构建时间对比

### 场景 1: 首次构建 (无本地镜像)

```bash
docker compose build --no-cache
```

**时间**: 15-30 分钟

**过程**:
```
[1/6] 下载基础镜像 python:3.11-slim        ██████████ 5 分钟
[2/6] 设置工作目录                         █ 10 秒
[3/6] 复制 requirements.txt                █ 5 秒
[4/6] 安装 Python 依赖 (200+ 包)           ████████████████ 15 分钟
[5/6] 复制项目代码                         ███ 2 分钟
[6/6] 设置启动命令                         █ 5 秒
```

---

### 场景 2: 仅修改代码 (依赖未变)

```bash
# ✅ 推荐: 使用缓存
docker compose build
```

**时间**: 1-3 分钟

**过程**:
```
[1/6] 基础镜像                              ✓ 缓存命中
[2/6] 工作目录                              ✓ 缓存命中
[3/6] requirements.txt (未修改)             ✓ 缓存命中
[4/6] 安装依赖                              ✓ 缓存命中 (省略 15 分钟)
[5/6] 复制代码 (已修改)                     ███ 2 分钟 (重新执行)
[6/6] 启动命令                              █ 5 秒
```

---

### 场景 3: 修改了依赖文件

```bash
# 编辑了 requirements.txt 或 package.json
docker compose build
```

**时间**: 10-20 分钟

**过程**:
```
[1/6] 基础镜像                              ✓ 缓存命中
[2/6] 工作目录                              ✓ 缓存命中
[3/6] requirements.txt (已修改)             ❌ 缓存失效
[4/6] 安装依赖                              ████████████████ 15 分钟 (重新下载)
[5/6] 复制代码                              ███ 2 分钟
[6/6] 启动命令                              █ 5 秒
```

---

### 场景 4: 强制重新构建 (无缓存)

```bash
# ⚠️ 慎用: 完全重新构建
docker compose build --no-cache
```

**时间**: 15-30 分钟

**过程**:
```
[1/6] 下载基础镜像 (即使本地有)            ██████████ 5 分钟
[2/6] 设置工作目录                         █ 10 秒
[3/6] 复制 requirements.txt                █ 5 秒
[4/6] 安装依赖 (重新下载所有包)            ████████████████ 15 分钟
[5/6] 复制代码                             ███ 2 分钟
[6/6] 启动命令                             █ 5 秒
```

---

## 🎯 使用场景建议

### ✅ 使用 `docker compose build` (带缓存)

**日常开发迭代**:
```bash
# 修改了 Python/Java/TypeScript 代码
docker compose build

# 只重建特定服务
docker compose build core-workflow
docker compose build console-hub
```

**适用情况**:
- ✅ 修改业务逻辑代码
- ✅ 修改配置文件 (`.env`)
- ✅ 修改 Dockerfile (除 `RUN` 指令外)
- ✅ 日常 Bug 修复
- ✅ 功能迭代开发

---

### ⚠️ 使用 `docker compose build --no-cache` (无缓存)

**重大更新场景**:
```bash
# 仅在必要时使用
docker compose build --no-cache
```

**适用情况**:
- 🔴 首次构建项目 (本地无镜像)
- 🔴 升级依赖包版本 (requirements.txt 大改)
- 🔴 更换基础镜像 (如 Python 3.10 → 3.11)
- 🔴 怀疑缓存导致的构建问题
- 🔴 生产环境发布前的最终构建
- 🔴 清理后重新部署 (`docker system prune -a`)

**不推荐频率**: 每月或每季度一次

---

## 🚀 优化构建速度的技巧

### 技巧 1: 合理组织 Dockerfile 层次

**❌ 错误示例** (每次都重新安装依赖):
```dockerfile
FROM python:3.11-slim
WORKDIR /app
COPY . .                           # 代码变动导致后续全部失效
RUN pip install -r requirements.txt # 每次都重新下载
CMD ["python", "main.py"]
```

**✅ 正确示例** (最大化缓存利用):
```dockerfile
FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .            # 先复制依赖文件
RUN pip install -r requirements.txt # 只在依赖变化时重新执行
COPY . .                           # 最后复制代码
CMD ["python", "main.py"]
```

---

### 技巧 2: 使用 `.dockerignore` 减少上下文

**创建 `.dockerignore`**:
```
# 排除不需要的文件
__pycache__/
*.pyc
*.pyo
*.pyd
.git/
.gitignore
.vscode/
.idea/
node_modules/
*.log
*.md
docs/
tests/
.env
.env.local
```

**效果**: 减少 `COPY . .` 的文件数量，避免无关文件变动导致缓存失效

---

### 技巧 3: 使用多阶段构建

```dockerfile
# Stage 1: 构建阶段
FROM node:18 AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci --only=production      # 只安装生产依赖
COPY . .
RUN npm run build

# Stage 2: 运行阶段
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
```

**优势**: 
- 最终镜像不包含构建工具
- 镜像体积更小
- 安全性更高

---

### 技巧 4: 使用 BuildKit 缓存挂载

**启用 BuildKit**:
```bash
export DOCKER_BUILDKIT=1
```

**Dockerfile 优化**:
```dockerfile
# syntax=docker/dockerfile:1

FROM python:3.11-slim
WORKDIR /app

# 使用缓存挂载加速 pip 安装
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install -r requirements.txt
```

**效果**: pip 下载的包会缓存到本地，跨镜像复用

---

### 技巧 5: 分层安装依赖

**前端项目 (package.json)**:
```dockerfile
# 先安装基础依赖
COPY package.json package-lock.json ./
RUN npm ci

# 再复制代码
COPY . .
RUN npm run build
```

**Python 项目 (requirements.txt)**:
```dockerfile
# 区分基础依赖和开发依赖
COPY requirements-base.txt .
RUN pip install -r requirements-base.txt

COPY requirements.txt .
RUN pip install -r requirements.txt
```

---

## 📈 构建时间优化案例

### 案例: PaiAgent 项目

**优化前**:
```bash
# 每次修改代码都要 20 分钟
docker compose build --no-cache
```

**优化后**:
```bash
# 日常迭代只需 2-3 分钟
docker compose build

# 仅在重大更新时才用 --no-cache
```

**优化策略**:
1. ✅ 依赖文件单独 `COPY`
2. ✅ 添加 `.dockerignore`
3. ✅ 使用 BuildKit
4. ✅ 合理利用缓存

**效果对比**:

| 操作 | 优化前 | 优化后 | 节省 |
|------|-------|-------|------|
| 修改业务代码 | 20 分钟 | 2 分钟 | 90% ⬇️ |
| 修改依赖包 | 20 分钟 | 12 分钟 | 40% ⬇️ |
| 首次构建 | 25 分钟 | 25 分钟 | - |

---

## 🔧 实用命令

### 查看构建缓存

```bash
# 查看镜像层
docker history <image-name>

# 查看构建缓存使用情况
docker system df

# 查看详细的缓存层
docker buildx du
```

---

### 清理缓存

```bash
# 清理构建缓存 (保留镜像)
docker builder prune

# 清理所有未使用的资源
docker system prune -a

# 清理特定服务的缓存
docker compose build --no-cache core-workflow
```

---

### 部分重建

```bash
# 只重建变更的服务
docker compose build core-workflow console-hub

# 重建并启动
docker compose up -d --build core-workflow

# 强制重建特定服务
docker compose build --no-cache --pull core-workflow
```

---

## 🎯 最佳实践总结

### 日常开发流程

```bash
# 1. 修改代码
vim core/workflow/engine/nodes/llm/llm_node.py

# 2. 快速重建 (使用缓存)
docker compose build core-workflow

# 3. 重启服务
docker compose up -d core-workflow

# 4. 查看日志验证
docker compose logs -f core-workflow
```

---

### 月度维护流程

```bash
# 1. 更新依赖包
pip-compile requirements.in  # 更新 requirements.txt
npm update                   # 更新 package.json

# 2. 完全重建 (无缓存)
docker compose build --no-cache

# 3. 测试验证
docker compose up -d
docker compose ps
docker compose logs -f

# 4. 清理旧镜像
docker image prune -a
```

---

### 生产环境发布

```bash
# 1. 代码打 Tag
git tag -a v1.2.0 -m "Release v1.2.0"

# 2. 无缓存构建 (确保干净)
docker compose build --no-cache

# 3. 导出镜像
docker save -o images.tar \
  core-workflow:local \
  core-agent:local \
  console-hub:local

# 4. 传输到生产服务器
scp images.tar prod-server:/opt/

# 5. 加载镜像
ssh prod-server "docker load -i /opt/images.tar"

# 6. 部署
ssh prod-server "cd /opt/app && docker compose up -d"
```

---

## 📝 快速决策表

**我应该用哪个命令？**

| 场景 | 命令 | 原因 |
|------|------|------|
| 修改了 `.py` 文件 | `docker compose build` | 利用缓存，快速重建 |
| 修改了 `requirements.txt` | `docker compose build` | 自动检测依赖变化 |
| 修改了 `Dockerfile` | `docker compose build` | 缓存层智能失效 |
| 首次克隆项目 | `docker compose build --no-cache` | 确保干净构建 |
| 升级 Python 版本 | `docker compose build --no-cache` | 重新下载基础镜像 |
| 怀疑缓存有问题 | `docker compose build --no-cache` | 排除缓存干扰 |
| 生产环境发布 | `docker compose build --no-cache` | 保证构建一致性 |
| 日常 Bug 修复 | `docker compose build` | 快速迭代 |

---

## 💡 关键要点

1. **默认使用缓存**: `docker compose build` 是日常开发的首选
2. **缓存很智能**: Docker 能自动检测文件变化，无需手动清理
3. **`--no-cache` 很慢**: 重新下载所有依赖，只在必要时使用
4. **Dockerfile 顺序很重要**: 把不常变的指令放前面
5. **利用 `.dockerignore`**: 减少构建上下文，提升缓存命中率
6. **分层构建**: 依赖文件和代码分开 `COPY`，最大化缓存利用

---

## 🔗 相关资源

- [Docker 官方文档 - Build Cache](https://docs.docker.com/build/cache/)
- [Dockerfile 最佳实践](https://docs.docker.com/develop/develop-images/dockerfile_best-practices/)
- [BuildKit 缓存优化](https://docs.docker.com/build/cache/backends/)
- 项目文档: `CLEANUP-AND-REBUILD.md`
