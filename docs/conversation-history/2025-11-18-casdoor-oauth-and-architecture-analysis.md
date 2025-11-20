# Conversation Summary: Casdoor OAuth Configuration and Architecture Analysis

**Date**: 2025-11-18  
**Topic**: Casdoor OAuth Integration, Docker Configuration, and Microservices Architecture Analysis

---

## Overview

This conversation focused on configuring and debugging the AI Podcast Workshop application, specifically:
1. Integrating Casdoor OAuth authentication for resume/portfolio purposes
2. Troubleshooting OAuth redirect issues with hardcoded IPs
3. Switching to non-Casdoor mode for team accessibility
4. Removing frontend from Docker for better development experience
5. Analyzing Hub ↔ Python microservices communication patterns

---

## Key Technical Decisions

### 1. Casdoor OAuth Integration (Later Disabled)

**Initial Goal**: Integrate Casdoor for unified authentication management ("集成Casdoor OAuth，以统一管理多用户的身份认证和管理")

**Configuration Files Created**:
- `.env.casdoor` - Docker environment backup
- `.env.production.casdoor` - Frontend build backup

**Final Decision**: Disabled Casdoor to lower barrier to entry for team members

### 2. Frontend Development Workflow

**Change**: Removed `console-frontend` from Docker Compose

**Reason**: Enable hot reload and better development experience

**New Workflow**:
```bash
# Start backend services with Docker
cd /docker/astronAgent
docker compose up -d

# Start frontend locally
cd /console/frontend
npm run dev
```

**Nginx Configuration**: Proxy `http://localhost/` → `http://host.docker.internal:3000`

---

## Architecture Analysis

### Service Communication Patterns

```mermaid
sequenceDiagram
    participant User as 用户
    participant Hub as Hub(Java:8080)
    participant Workflow as Workflow(Python:7880)
    participant Link as Link(Python:18888)
    participant AITools as AITools(Python:18668)
    
    User->>Hub: POST /chat-message/chat
    Hub->>Workflow: POST :7880/workflow/v1/chat/completions
    Workflow->>Link: POST :18888/mcp/invoke (调用工具)
    Workflow->>AITools: POST :18668/tts/synthesize (合成语音)
    Workflow-->>Hub: SSE 流式返回结果
    Hub-->>User: SSE 流式返回
```

### When Each Service is Called

#### 1. Core-Workflow (Port 7880)
- **Called by**: Hub (Java)
- **When**: User sends message to Workflow Bot
- **How**: HTTP POST to `http://localhost:7880/workflow/v1/chat/completions`
- **Code**: `WorkflowBotChatServiceImpl.java`
- **Returns**: SSE stream of workflow execution results

#### 2. Core-Link (Port 18888)
- **Called by**: Workflow Engine (Python)
- **When**: Workflow execution reaches Plugin Tool node
- **How**: HTTP POST to `http://core-link:18888/mcp/invoke`
- **Purpose**: Execute MCP (Model Context Protocol) tools
- **Returns**: Tool execution results

#### 3. Core-AITools (Port 18668)
- **Called by**: Workflow Engine (Python)
- **When**: Workflow execution reaches TTS synthesis node
- **How**: HTTP POST to `http://core-aitools:18668/tts/synthesize`
- **Purpose**: Generate podcast audio using iFlyTek voice synthesis
- **Returns**: Audio file URL from MinIO storage

### Key Architectural Insights

1. **Hub does NOT directly call Link or AITools**
   - Hub only calls Workflow Engine
   - Workflow Engine internally orchestrates calls to Link and AITools

2. **Workflow is NOT LangChain-based**
   - Custom DSL (Domain Specific Language) workflow engine
   - Only uses `langchain-sandbox` for safe Python code execution in Code nodes
   - Not using LangChain or LangGraph as core framework

3. **Data Flow for Podcast Generation**:
   ```
   User Input (Text) 
     → Frontend (React) 
     → Console Hub (Spring Boot) 
     → Workflow Engine (FastAPI) 
     → DeepSeek LLM (script rewriting) 
     → AI Tools Plugin (iFlyTek voice synthesis) 
     → MinIO (audio storage) 
     → Frontend (audio playback)
   ```

---

## Troubleshooting History

### Issue 1: OAuth Redirect with Empty client_id

**Symptom**: Clicking login redirected to `http://172.31.114.167:8000/login/oauth/authorize?client_id=&...`

**Root Cause**: `.env.production` had hardcoded old IP `172.31.114.167:8000`

**Fix**:
```bash
# Updated .env.production
CONSOLE_CASDOOR_URL=http://localhost:8000
CONSOLE_CASDOOR_ID=astron-agent-client
CONSOLE_CASDOOR_APP=astron-agent-app
CONSOLE_CASDOOR_ORG=built-in

# Rebuilt frontend
docker compose build console-frontend
docker compose up -d console-frontend
```

### Issue 2: Persistent OAuth Redirect After Disabling Casdoor

**Symptom**: Even after clearing Casdoor config, login still redirected to OAuth endpoint

**Root Cause**: Frontend container using cached build with old environment variables

**Fix**:
1. Modified `casdoor.ts` to return fallback URL `http://localhost:3000`
2. Enhanced `auth.ts` to detect fallback URL as "disabled"
3. Rebuilt frontend container

**Code Changes**:

`casdoor.ts`:
```typescript
const getRuntimeCasdoorUrl = (): string => {
  // 如果配置为空，返回 localhost:3000 作为 fallback（但不会实际使用）
  if (!envUrl || envUrl === '') {
    if (!fallbackUrl || fallbackUrl === '') {
      return 'http://localhost:3000';
    }
  }
  return (envUrl !== undefined ? envUrl : fallbackUrl) || 'http://localhost:3000';
};
```

`auth.ts`:
```typescript
const isCasdoorEnabled = (): boolean => {
  const config = (casdoorSdk as any).config;
  const serverUrl = config?.serverUrl || '';
  // 如果 serverUrl 为空或者是 localhost:3000（fallback值），则认为未启用
  return !!(serverUrl && serverUrl !== '' && serverUrl !== 'http://localhost:3000');
};
```

### Issue 3: Nginx Proxy Not Working After Configuration

**Symptom**: Accessing `http://localhost/` returned blank page

**Root Cause**: Nginx still using old configuration cached in container, trying to access `http://198.18.6.241:1881` instead of `host.docker.internal:3000`

**Fix**: Restart nginx to pick up new configuration
```bash
docker compose restart nginx
```

---

## Configuration Files Modified

### `/docker/astronAgent/.env`
```bash
# 🚫 Casdoor 已禁用 - 使用本地模拟登录模式
CONSOLE_CASDOOR_URL=
CONSOLE_CASDOOR_ID=
CONSOLE_CASDOOR_APP=
CONSOLE_CASDOOR_ORG=
```

### `/console/frontend/.env.production`
```bash
# ====================================
# Casdoor Authentication Configuration
# ====================================
# 🚫 Casdoor 已禁用 - 使用本地模拟登录
CONSOLE_CASDOOR_URL=
CONSOLE_CASDOOR_ID=
CONSOLE_CASDOOR_APP=
CONSOLE_CASDOOR_ORG=
```

### `/docker/astronAgent/docker-compose.yaml`
```yaml
# Console Frontend - REMOVED (run locally with npm run dev)
# To start frontend locally:
#   cd console/frontend
#   npm run dev
# Frontend will be available at http://localhost:3000

nginx:
  depends_on:
    - console-hub  # Removed dependency on console-frontend
```

### `/docker/astronAgent/nginx/nginx.conf`
```nginx
# Frontend application proxy - proxy to local dev server
location / {
    proxy_pass http://host.docker.internal:3000;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;

    # Timeout settings
    proxy_connect_timeout 30s;
    proxy_send_timeout 30s;
    proxy_read_timeout 30s;
}
```

---

## Backup Configurations Created

### `/docker/astronAgent/.env.casdoor`
```bash
CONSOLE_CASDOOR_URL=${HOST_BASE_ADDRESS}:${CASDOOR_PORT}
CONSOLE_CASDOOR_ID=astron-agent-client
CONSOLE_CASDOOR_APP=astron-agent-app
CONSOLE_CASDOOR_ORG=built-in
```

### `/console/frontend/.env.production.casdoor`
```bash
CONSOLE_CASDOOR_URL=http://localhost:8000
CONSOLE_CASDOOR_ID=astron-agent-client
CONSOLE_CASDOOR_APP=astron-agent-app
CONSOLE_CASDOOR_ORG=built-in
```

---

## Service Ports Reference

| Service | Port | Purpose |
|---------|------|---------|
| nginx | 80 | Reverse proxy |
| console-hub | 8080 | Java backend API |
| console-frontend | 3000 | React dev server (local) |
| core-workflow | 7880 | Workflow orchestration engine |
| core-link | 18888 | MCP tool integration |
| core-aitools | 18668 | TTS voice synthesis |
| postgres | 5432 | Workflow data storage |
| mysql | 3306 | Tool metadata storage |
| redis | 6379 | Caching |
| minio | 18998 (console), 18999 (API) | Object storage |

---

## Pending Verification Steps

1. **Restart nginx** to pick up new configuration:
   ```bash
   docker compose restart nginx
   ```

2. **Start frontend dev server** on host machine:
   ```bash
   cd console/frontend
   npm run dev
   ```

3. **Verify local development setup** - Access `http://localhost` and confirm it proxies correctly to local Vite dev server

---

## Key Learnings

1. **Environment variable injection in Docker** requires rebuild, not just restart
2. **Frontend build-time variables** (`.env.production`) are compiled into JavaScript, not runtime-configurable
3. **host.docker.internal** is Docker's special DNS name for accessing host machine from containers
4. **Nginx configuration changes** require container restart to take effect
5. **Microservices architecture** - Hub only calls Workflow; Workflow orchestrates Link and AITools internally

---

## Resume/Portfolio Description

**技术亮点**:
- 集成 Casdoor OAuth 2.0 PKCE 认证流程（可选）
- Docker Compose 多服务编排（9+ 微服务）
- 前后端分离架构（React + Spring Boot + FastAPI）
- 自研 DSL 工作流引擎（非 LangChain 实现）
- 讯飞星火 TTS 语音合成集成
- SSE 流式响应优化用户体验

**解决的技术难点**:
- 环境变量注入与构建时配置管理
- Docker 容器与宿主机网络通信（host.docker.internal）
- OAuth 重定向 URL 配置与调试
- 微服务间 HTTP 通信与 SSE 流式传输
- Nginx 反向代理配置与动态路由

---

## References

- Project Repository: `/Users/itwanger/Documents/GitHub/PaiAgent`
- Frontend: `/console/frontend` (React + Vite)
- Backend: `/console/backend` (Spring Boot)
- Workflow Engine: `/core/workflow` (FastAPI)
- Docker Compose: `/docker/astronAgent/docker-compose.yaml`
- AGENTS.md: Project architecture and development guide
