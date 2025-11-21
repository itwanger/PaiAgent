# PaiAgent - AI 播客生成平台

<div align="center">

[![License](https://img.shields.io/badge/license-apache2.0-blue.svg)](LICENSE)
[![基于 AstronAgent](https://img.shields.io/badge/基于-AstronAgent-blue.svg)](https://github.com/iflytek/astron-agent)

</div>

## 📻 项目简介

**AI Podcast Workshop（AI 播客工坊）** 是一个基于讯飞 AstronAgent 二次开发的 AI 播客生成平台，通过智能工作流编排，将用户输入的文本内容自动改写为播客风格的口播稿，并使用讯飞超拟人合成技术生成高质量语音。

![](https://cdn.tobebetterjavaer.com/stutymore/README-20251028201755.png)

### 核心能力

- **AI 内容改写**：集成 DeepSeek 大模型，将普通文本智能改编为王二电台特色的播客风格逐字稿
- **超拟人语音合成**：基于讯飞星火语音合成技术，生成自然流畅的播客音频
- **可视化工作流编排**：通过 AstronAgent 的工作流引擎，实现"文本输入 → AI 改写 → 语音合成"的自动化流程
- **开箱即用**：基于 Docker Compose 一键部署，无需复杂配置

### 技术特点

- ✅ 基于企业级开源项目 [AstronAgent](https://github.com/iflytek/astron-agent) 二次开发
- ✅ 微服务架构，支持高可用部署
- ✅ 支持自定义播客风格、发音人、语速等参数
- ✅ 完整的工作流可视化调试能力

## 🚀 快速开始

### 环境要求

- Docker 20.10+
- Docker Compose 2.0+
- 8GB+ 可用内存
- 20GB+ 可用磁盘空间

### 一键部署

```bash
# 1. 克隆项目
git clone https://github.com/itwanger/astron-agent.git
cd astron-agent/docker/astronAgent

# 2. 复制环境变量配置
cp .env.example .env

# 3. 编辑环境变量（可选，使用默认配置即可快速体验）
vim .env

# 4. 启动所有服务
docker compose up -d

# 5. 查看服务状态
docker compose ps
```

### 访问应用

启动完成后，访问以下地址：

- **应用前端**：http://localhost
- **默认账户**：admin / 123

## 📖 使用指南

### 创建 AI 播客工作流

1. 登录系统后，进入「工作流」页面
2. 创建新工作流，配置以下节点：
   - **开始节点**：定义用户输入
   - **大模型节点**：配置 DeepSeek 模型，设置播客风格改写提示词
   - **超拟人合成节点**：配置语音合成参数（发音人、语速等）
   - **结束节点**：输出语音 URL
3. 保存并运行工作流
4. 输入文本，即可生成播客音频

### 工作流配置示例

```
用户输入 → DeepSeek 改写 → 超拟人合成 → 输出音频
```

**提示词示例**：

```
# 角色
你是沉默王二，一个嘴上贫、心里明白的技术博主。现在你主持一档叫「王二电台」的节目。

# 任务
把用户提供的原始内容改编成适合播客节目风格的逐字稿。
要像电台聊天那样自然，有节奏、有情绪、有点梗。

# 原始内容：{{input}}
```

## 🛠️ 技术架构

### 核心服务

| 服务 | 说明 | 端口 |
|------|------|------|
| console-hub | 控制台后端服务 | 8080 |
| console-frontend | 前端界面 | 1881 |
| core-workflow | 工作流引擎 | 7880 |
| core-aitools | AI 工具服务（包含超拟人合成） | 18668 |
| nginx | 反向代理 | 80 |
| postgres | PostgreSQL 数据库 | 5432 |
| mysql | MySQL 数据库 | 3306 |
| redis | Redis 缓存 | 6379 |
| minio | 对象存储 | 18998/18999 |

### 数据库说明

- **PostgreSQL**：工作流数据、用户配置
- **MySQL**：工具元数据、智能体配置
  - `astron_console.tool_box`：工具注册表
  - `spark-link.tools_schema`：工具 Schema 定义

## 🔧 常见问题

### 工作流执行失败？

检查以下配置：

1. **工具版本号**：确保 `tools_schema` 表中工具版本为 `V1.0`
2. **服务地址**：超拟人合成服务地址应为 `http://core-aitools:18668`
3. **app_id**：确保工具的 `app_id` 与工作流一致

手动修复命令：

```bash
# 修复工具版本号和服务地址
docker compose exec mysql mysql -uroot -proot123 spark-link -e "
UPDATE tools_schema 
SET version='V1.0', 
    app_id='680ab54f',
    open_api_schema = REPLACE(open_api_schema, 'https://core-aitools:18669', 'http://core-aitools:18668')
WHERE tool_id='tool@8b2262bef821000';"

# 重启相关服务
docker compose restart core-link core-workflow
```

### 容器重启后出现 502 错误？

这是因为容器 IP 地址变化导致 Nginx 无法连接到 console-hub。解决方法：

```bash
docker compose restart nginx
```

### 如何查看服务日志？

```bash
# 查看所有服务日志
docker compose logs -f

# 查看特定服务日志
docker compose logs -f console-hub
docker compose logs -f core-workflow
```

## 🤝 贡献指南

本项目基于 [AstronAgent](https://github.com/iflytek/astron-agent) 二次开发。

如果您有任何建议或发现问题，欢迎提交 Issue 或 Pull Request。

## 📄 开源协议

本项目基于 Apache 2.0 协议开源，可自由商业使用。

## 🙏 致谢

- [讯飞 AstronAgent](https://github.com/iflytek/astron-agent) - 提供强大的智能体开发平台
- [讯飞星火](https://www.xfyun.cn) - 提供大模型和语音合成能力
- [DeepSeek](https://www.deepseek.com) - 提供高性能中文大模型

---

**AI Podcast Workshop** - 让 AI 成为你的播客搭档 🎙️
