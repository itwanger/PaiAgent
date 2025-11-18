# PaiAgent 项目完整部署指南

本指南将帮助您按照正确的顺序启动 PaiAgent 项目的所有组件，包括身份认证、知识库和核心服务。

## 📋 项目架构概述

PaiAgent 项目包含以下三个主要组件：

1. **Casdoor** - 身份认证和单点登录服务(必要部署组件,提供单点登录功能)
3. **PaiAgent** - 核心业务服务集群(必要部署组件)

## 🚀 部署步骤

### 前置要求

**Agent系统配置要求**
- CPU >= 2 Core
- RAM >= 4 GiB
- Disk >= 50 GB

**RAGFlow配置要求**
- CPU >= 4 Core
- RAM >= 16 GB
- Disk >= 50 GB

### 第一步：配置 PaiAgent 环境变量

在启动 PaiAgent 服务之前，需要配置相关的连接信息。

```bash
# 进入 PaiAgent 目录
cd docker/PaiAgent

# 复制环境变量配置
cp .env.example .env
```

#### 2.2 配置 讯飞开放平台 相关 APP_ID API_KEY 等信息

获取文档详见：https://www.xfyun.cn/doc/platform/quickguide.html

创建应用完成后可能需要购买或领取相应能力的API授权服务量
- 星火大模型API: https://xinghuo.xfyun.cn/sparkapi
  (对于大模型API会有额外的SPARK_API_PASSWORD需要在页面上获取)
  (指令型助手对应的文本AI生成/优化功能需要开通Spark Ultra能力，页面地址为https://console.xfyun.cn/services/bm4)
- 实时语音转写API: https://console.xfyun.cn/services/rta
- 图片生成API: https://www.xfyun.cn/services/wtop

编辑 docker/PaiAgent/.env 文件，更新相关环境变量：
```env
PLATFORM_APP_ID=your-app-id
PLATFORM_API_KEY=your-api-key
PLATFORM_API_SECRET=your-api-secret

SPARK_API_PASSWORD=your-api-password
SPARK_RTASR_API_KEY=your-rtasr-api-key
```

#### 2.3 配置星火 RAG 云服务（可选）

星火RAG云服务提供两种使用方式：

##### 方式一：在页面中获取

1. 使用讯飞开放平台创建的 APP_ID 和 API_SECRET
2. 直接在页面中获取星火数据集ID，详见：[xinghuo_rag_tool.html](/docs/xinghuo_rag_tool.html)

##### 方式二：使用 cURL 命令行方式

如果您更喜欢使用命令行工具，可以通过以下 cURL 命令创建数据集：

```bash
# 创建星火RAG数据集
curl -X PUT 'https://chatdoc.xfyun.cn/openapi/v1/dataset/create' \
    -H "Accept: application/json" \
    -H "appId: your_app_id" \
    -H "timestamp: $(date +%s)" \
    -H "signature: $(echo -n "$(echo -n "your_app_id$(date +%s)" | md5sum | awk '{print $1}')" | openssl dgst -sha1 -hmac 'your_api_secret' -binary | base64)" \
    -F "name=我的数据集"
```

**注意事项：**
- 请将 `your_app_id` 替换为您的实际 APP ID
- 请将 `your_api_secret` 替换为您的实际 API Secret

获取到数据集ID后，请将数据集ID更新到 docker/PaiAgent/.env 文件中：
```env
XINGHUO_DATASET_ID=
```

#### 2.4 配置服务主机地址

编辑 docker/PaiAgent/.env 文件，配置 PaiAgent 服务的主机地址：

```env
HOST_BASE_ADDRESS=http://localhost
```

**说明：**
- 如果您使用域名访问，请将 `localhost` 替换为您的域名
- 确保 nginx 和 minio 的端口已正确开放

### 第三步：启动 PaiAgent 核心服务（包含 Casdoor 认证服务）

启动 PaiAgent 服务请运行我们的 [docker-compose-with-auth.yaml](/docker/PaiAgent/docker-compose-with-auth.yaml) 文件。**该文件已通过 `include` 机制集成了 Casdoor 认证服务**，会自动启动 Casdoor。

```bash
# 进入 PaiAgent 目录
cd docker/PaiAgent

# 启动所有服务（包含 Casdoor）
docker compose -f docker-compose-with-auth.yaml up -d
```

**说明：**
- Casdoor默认的登录账户名：`admin`，密码：`123`

### 第四步：修改 Casdoor 认证（可选）

您可以根据需要在 Casdoor 中创建新的应用和组织，并将配置信息更新到 `.env` 文件中（已存在默认组织和应用）。

#### 4.1 配置 Casdoor 应用

**获取 Casdoor 配置信息：**
1. 访问 Casdoor 管理控制台： [http://localhost:8000](http://localhost:8000)
2. 使用默认管理员账号登录：`admin / 123`
3. **创建组织**
   进入 [http://localhost:8000/organizations](http://localhost:8000/organizations) 页面，点击"添加"，填写组织名称后保存并退出。
4. **创建应用并绑定组织**
   进入 [http://localhost:8000/applications](http://localhost:8000/applications) 页面，点击"添加"。

   创建应用时填写以下信息：
   - **Name**：自定义应用名称，例如 `agent`
   - **Redirect URL**：设置为项目的回调地址。如果 Nginx 暴露的端口号是 `80`，使用 `http://your-local-ip/callback`；如果是其他端口（例如 `888`），使用 `http://your-local-ip:888/callback`
   - **Organization**：选择刚创建的组织名称
5. 保存应用后，记录以下信息并与项目配置项一一对应：

| Casdoor 信息项 | 示例值 | `.env` 中对应配置项 |
|----------------|--------|----------------------|
| Casdoor 服务地址（URL） | `http://localhost:8000` | `CONSOLE_CASDOOR_URL=http://localhost:8000` |
| 客户端 ID（Client ID） | `your-casdoor-client-id` | `CONSOLE_CASDOOR_ID=your-casdoor-client-id` |
| 应用名称（Name） | `your-casdoor-app-name` | `CONSOLE_CASDOOR_APP=your-casdoor-app-name` |
| 组织名称（Organization） | `your-casdoor-org-name` | `CONSOLE_CASDOOR_ORG=your-casdoor-org-name` |

6. 将以上配置信息填写到项目的环境变量文件中：
```bash
# 进入 PaiAgent 目录
cd docker/PaiAgent

# 编辑环境变量配置
vim .env
```

**在 .env 文件中添加或更新以下配置项：**
```env
# Casdoor配置
CONSOLE_CASDOOR_URL=http://localhost:8000
CONSOLE_CASDOOR_ID=your-casdoor-client-id
CONSOLE_CASDOOR_APP=your-casdoor-app-name
CONSOLE_CASDOOR_ORG=your-casdoor-org-name
```

7. 重启 PaiAgent 服务以应用新配置：
```bash
docker compose restart console-frontend console-hub
```

## 📊 服务访问地址

启动完成后，您可以通过以下地址访问各项服务：

### 认证服务
- **Casdoor 管理界面**：http://localhost:8000

### 知识库服务
- **RagFlow Web界面**：http://localhost:18080

### PaiAgent 核心服务
- **控制台前端(nginx代理)**：http://localhost/

## 📚 更多资源

- [PaiAgent 官方文档](https://docs.PaiAgent.cn)
- [Casdoor 官方文档](https://casdoor.org/docs/overview)
- [RagFlow 官方文档](https://ragflow.io/docs)
- [Docker Compose 官方文档](https://docs.docker.com/compose/)

## 🤝 技术支持

如遇到问题，请：

1. 查看相关服务的日志文件
2. 检查官方文档和故障排除指南
3. 在项目 GitHub 仓库提交 Issue
4. 联系技术支持团队

---

**注意**：首次部署项目建议在测试环境中验证所有功能后再部署到生产环境。
