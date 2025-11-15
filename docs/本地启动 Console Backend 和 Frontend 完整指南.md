# 本地启动 Console Backend 和 Frontend 完整指南 (绕过 Casdoor)

本指南详细说明如何在本地直接启动 Console 的 Backend 和 Frontend,并完全绕过 Casdoor 认证。

---

## 📋 目录

1. [前置准备](#前置准备)
2. [启动必需的中间件](#启动必需的中间件)
3. [配置后端 (Backend)](#配置后端-backend)
4. [配置前端 (Frontend)](#配置前端-frontend)
5. [启动服务](#启动服务)
6. [验证和测试](#验证和测试)
7. [常见问题](#常见问题)

---

## 前置准备

### 1. 环境要求

- **Java**: 21 (推荐使用 jenv 管理)
- **Node.js**: 18+
- **Maven**: 3.8+
- **Docker Desktop**: 用于运行中间件 (MySQL, Redis, MinIO)
- **IntelliJ IDEA**: Ultimate 或 Community (推荐)

### 2. 验证环境

```bash
# 验证 Java 版本
java -version  # 应该是 21

# 验证 Node.js 版本
node -v        # 应该是 v18+

# 验证 Maven 版本
mvn -version   # 应该是 3.8+

# 验证 Docker
docker --version
docker compose version
```

---

## 启动必需的中间件

### 方式 A: 使用本地已安装的服务 (推荐)

如果你本地已有 MySQL/Redis/MinIO,只需确保它们正常运行:

#### 验证服务状态

```bash
# 验证 MySQL (默认端口 3306)
mysql -h localhost -u root -p -e "SELECT VERSION();"

# 验证 Redis (默认端口 6379)
redis-cli ping
# 预期输出: PONG

# 验证 MinIO (默认端口 9000 API, 9001 Console)
# 如果 MinIO 运行在 9000 端口,需要修改配置
curl http://localhost:9000/minio/health/live
# 或访问 MinIO Console: http://localhost:9001
```

#### 确认服务端口

| 服务 | 默认端口 | 后端配置中的端口 | 是否需要修改配置 |
|------|----------|-----------------|-----------------|
| MySQL | 3306 | 3306 | ❌ 无需修改 |
| Redis | 6379 | 6379 | ❌ 无需修改 (minimal profile 已禁用) |
| MinIO API | 9000 | 18999 | ⚠️ **需要修改** (见下文) |
| MinIO Console | 9001 | 18998 | ⚠️ **需要修改** (见下文) |

#### MinIO 端口配置

如果你的本地 MinIO 运行在默认端口 `9000` (API) 和 `9001` (Console),需要修改后端配置:

**编辑** `console/backend/hub/src/main/resources/application-minimal.yml`:

```yaml
# S3 配置 (使用本地 MinIO)
s3:
  endpoint: http://localhost:9000        # 改为 9000 (API 端口)
  remoteEndpoint: http://localhost:9000  # 改为 9000
  accessKey: minioadmin                  # 改为你的 MinIO accessKey
  secretKey: minioadmin                  # 改为你的 MinIO secretKey
  bucket: astron-agent
  presignExpirySeconds: 600
```

**注意**: 确认你的 MinIO accessKey 和 secretKey (默认通常是 `minioadmin` / `minioadmin`)

#### 启动本地 MinIO (如果未运行)

```bash
# macOS (Homebrew 安装)
brew services start minio

# 或手动启动
minio server ~/minio-data --console-address ":9001"

# Linux
systemctl start minio

# 或手动启动
minio server /data --console-address ":9001"
```

### 方式 B: 使用项目自带的 Docker Compose

如果你想使用 Docker 版本 (可能端口已配置好):

```bash
cd /Users/itwanger/Documents/GitHub/PaiAgent/docker/astronAgent

# 仅启动必需的中间件 (MySQL, Redis, MinIO)
docker compose up -d mysql redis minio

# 等待服务启动 (约 10-30 秒)
sleep 30

# 验证服务状态
docker compose ps
```

预期输出:
```
NAME                         STATUS          PORTS
astron-agent-mysql           Up 30 seconds   0.0.0.0:3306->3306/tcp
astron-agent-redis           Up 30 seconds   0.0.0.0:6379->6379/tcp
astron-agent-minio           Up 30 seconds   0.0.0.0:18998-18999->9000-9001/tcp
```

### 初始化数据库 (仅首次)

```bash
# 确保 astron_console 数据库存在
# 将 YOUR_PASSWORD 替换为你的 MySQL root 密码
mysql -h localhost -u root -pYOUR_PASSWORD -e "CREATE DATABASE IF NOT EXISTS astron_console CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 如果需要导入初始化 SQL (检查 docker/astronAgent/mysql/init.sql)
mysql -h localhost -u root -pYOUR_PASSWORD astron_console < docker/astronAgent/mysql/init.sql
```

**注意**: 
- 将 `YOUR_PASSWORD` 替换为你本地 MySQL 的 root 密码
- 后续配置文件 (`application-minimal.yml`) 中也需要使用相同的密码
- 如果你的 MySQL 没有设置密码,使用 `mysql -h localhost -u root` 即可

---

## 配置后端 (Backend)

### 1. 确认并修改 application-minimal.yml 配置

文件位置: `console/backend/hub/src/main/resources/application-minimal.yml`

**必须修改的配置项** (根据你的本地环境):

```bash
# 编辑配置文件
vim console/backend/hub/src/main/resources/application-minimal.yml
```

关键配置修改:

```yaml
# 最小化本地调试配置

server:
  port: 8080

spring:
  # ✅ 禁用 OAuth2 和 Redis 自动配置 (绕过 Casdoor)
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration
      - org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
      - org.redisson.spring.starter.RedissonAutoConfiguration
      - org.redisson.spring.starter.RedissonAutoConfigurationV2
  
  datasource:
    url: jdbc:mysql://localhost:3306/astron_console?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false
    username: root
    password: YOUR_MYSQL_PASSWORD  # 🔧 修改为你的实际 MySQL root 密码
    driver-class-name: com.mysql.cj.jdbc.Driver
  
  security:
    enabled: false  # ✅ 禁用 Spring Security

# S3/MinIO 配置 - 🔧 根据你的本地 MinIO 配置修改
s3:
  endpoint: http://localhost:9000              # 🔧 改为 9000 (本地 MinIO 默认 API 端口)
  remoteEndpoint: http://localhost:9000        # 🔧 改为 9000
  accessKey: YOUR_MINIO_ACCESS_KEY             # 🔧 改为你的 MinIO accessKey (默认通常是 minioadmin)
  secretKey: YOUR_MINIO_SECRET_KEY             # 🔧 改为你的 MinIO secretKey (默认通常是 minioadmin)
  bucket: astron-agent
  presignExpirySeconds: 600

# Workflow 服务 URL (Java 版本) - 如果不使用可忽略
workflow:
  chatUrl: http://localhost:7881/api/v1/workflow/chat/stream
  debugUrl: http://localhost:7881/api/v1/workflow/chat/stream
  resumeUrl: http://localhost:7881/api/v1/workflow/chat/resume
  enabled: true
  timeout-ms: 300000
```

**配置说明**:

| 配置项 | 默认值 (Docker) | 本地值 (需修改) | 说明 |
|--------|----------------|----------------|------|
| `spring.datasource.password` | `123456` | 你的 MySQL 密码 | ✅ **必须修改** |
| `s3.endpoint` | `http://localhost:18999` | `http://localhost:9000` | ✅ **必须修改** (本地 MinIO 默认端口) |
| `s3.accessKey` | `minioadmin` | 你的 MinIO accessKey | ⚠️ 确认后修改 |
| `s3.secretKey` | `minioadmin` | 你的 MinIO secretKey | ⚠️ 确认后修改 |

**查看你的 MinIO 配置**:

```bash
# 方式 1: 查看 MinIO 配置文件 (如果是 Homebrew 安装)
cat ~/Library/Application\ Support/minio/config.json

# 方式 2: 登录 MinIO Console 查看
# 访问: http://localhost:9001
# 默认用户名/密码: minioadmin / minioadmin

# 方式 3: 查看环境变量
env | grep MINIO
```

### 2. 修改 SecurityConfig.java (绕过认证)

为了完全绕过 Casdoor OAuth2 认证,我们需要修改安全配置,让所有接口都不需要认证。

编辑文件: `console/backend/hub/src/main/java/com/iflytek/astron/console/hub/config/SecurityConfig.java`

**方式 A: 临时禁用所有认证 (开发环境推荐)**

在 `SecurityConfig.java` 中添加一个新的配置类:

```java
package com.iflytek.astron.console.hub.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 本地开发用最小化安全配置 - 禁用所有认证
 * 仅在 spring.profiles.active=minimal 时生效
 */
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(name = "spring.profiles.active", havingValue = "minimal")
public class MinimalSecurityConfig {

    @Bean
    public SecurityFilterChain minimalSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .anyRequest().permitAll()  // 所有接口都不需要认证
            )
            .csrf(AbstractHttpConfigurer::disable)
            .cors(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .securityContext(AbstractHttpConfigurer::disable)
            .sessionManagement(AbstractHttpConfigurer::disable);
        
        return http.build();
    }
}
```

创建文件:

```bash
cat > console/backend/hub/src/main/java/com/iflytek/astron/console/hub/config/MinimalSecurityConfig.java << 'EOF'
package com.iflytek.astron.console.hub.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 本地开发用最小化安全配置 - 禁用所有认证
 * 仅在 spring.profiles.active=minimal 时生效
 */
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(name = "spring.profiles.active", havingValue = "minimal")
public class MinimalSecurityConfig {

    @Bean
    public SecurityFilterChain minimalSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .anyRequest().permitAll()  // 所有接口都不需要认证
            )
            .csrf(AbstractHttpConfigurer::disable)
            .cors(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .securityContext(AbstractHttpConfigurer::disable)
            .sessionManagement(AbstractHttpConfigurer::disable);
        
        return http.build();
    }
}
EOF
```

这样,当使用 `minimal` profile 时,Spring Security 会加载这个配置,允许所有请求通过。

### 3. 编译后端

```bash
cd /Users/itwanger/Documents/GitHub/PaiAgent/console/backend

# 编译所有模块
mvn clean install -DskipTests

# 或仅编译 hub 模块及其依赖
mvn clean compile -pl hub -am
```

预期输出:
```
[INFO] BUILD SUCCESS
[INFO] Total time: XX.XXX s
```

---

## 配置前端 (Frontend)

### 1. 创建本地开发环境变量文件

在 `console/frontend/` 目录下创建 `.env.local` 文件 (优先级最高,不会被提交到 Git):

```bash
cd /Users/itwanger/Documents/GitHub/PaiAgent/console/frontend

cat > .env.local << 'EOF'
# 本地开发环境配置 (绕过 Casdoor 认证)

# ✅ 后端 API 地址
VITE_BASE_URL=http://localhost:8080
CONSOLE_API_URL=http://localhost:8080

# ✅ 禁用 Casdoor (留空或注释掉)
CONSOLE_CASDOOR_URL=
CONSOLE_CASDOOR_ID=
CONSOLE_CASDOOR_APP=
CONSOLE_CASDOOR_ORG=

# 或者明确设置为空字符串
# VITE_CASDOOR_ENDPOINT=
# VITE_CASDOOR_CLIENT_ID=
# VITE_CASDOOR_APP_NAME=
# VITE_CASDOOR_ORG_NAME=
EOF
```

**原理**: Vite 会按以下优先级加载环境变量:
1. `.env.local` (最高优先级,不会被提交)
2. `.env.development` (开发环境)
3. `.env` (通用配置)

通过创建 `.env.local` 并留空 Casdoor 相关变量,前端会跳过 OAuth2 登录流程。

### 2. 修改前端代码绕过认证 (可选)

如果前端代码有硬编码的认证逻辑,需要修改路由守卫:

```bash
# 查找认证相关文件
find console/frontend/src -name "*auth*" -o -name "*login*" -o -name "*casdoor*"
```

通常需要修改的文件:
- `src/router/index.ts` 或 `src/router/index.js` (路由守卫)
- `src/utils/auth.ts` (认证工具函数)
- `src/App.tsx` 或 `src/App.jsx` (认证初始化)

**临时方案**: 直接在路由守卫中返回 `true`:

```typescript
// 示例: src/router/index.ts
router.beforeEach((to, from, next) => {
  // 临时绕过认证检查
  if (import.meta.env.MODE === 'development') {
    next();
    return;
  }
  
  // 原有认证逻辑...
});
```

### 3. 安装前端依赖

```bash
cd /Users/itwanger/Documents/GitHub/PaiAgent/console/frontend

# 安装依赖
npm install

# 或使用 pnpm/yarn
# pnpm install
# yarn install
```

---

## 启动服务

### 1. 启动后端 (Backend)

#### 方式 A: 使用 IntelliJ IDEA (推荐)

1. **打开项目**
   ```bash
   # 在 IDEA 中打开: File → Open → 选择 console/backend/pom.xml
   # 或直接: idea console/backend/pom.xml
   ```

2. **创建 Run Configuration**
   - 打开 `console/backend/hub/src/main/java/com/iflytek/astron/console/hub/HubApplication.java`
   - 右键 → **Run 'HubApplication.main()'** (首次会创建配置)
   - 或手动创建配置: `Run` → `Edit Configurations...` → `+` → `Application`

3. **配置参数**
   ```
   Name: HubApplication (Minimal)
   
   Main class: 
   com.iflytek.astron.console.hub.HubApplication
   
   VM options (点击 Modify options → Add VM options):
   -Dspring.profiles.active=minimal
   
   Use classpath of module: 
   astron-console-hub.main
   
   JRE: 
   21
   ```

4. **启动**
   - 点击绿色运行按钮或 `Ctrl+R` / `⌘R`
   - 观察控制台输出,应该看到:
     ```
     Started HubApplication in X.XXX seconds
     ```

#### 方式 B: 使用命令行

```bash
cd /Users/itwanger/Documents/GitHub/PaiAgent/console/backend/hub

# 使用 Maven 启动
mvn spring-boot:run -Dspring-boot.run.profiles=minimal

# 或使用编译好的 jar
cd target
java -Dspring.profiles.active=minimal -jar astron-console-hub-*.jar
```

#### 验证后端启动成功

```bash
# 检查健康状态
curl http://localhost:8080/health

# 预期输出:
# {"status":"UP"}

# 检查端口监听
lsof -i :8080 | grep LISTEN
```

### 2. 启动前端 (Frontend)

#### 开发模式启动

```bash
cd /Users/itwanger/Documents/GitHub/PaiAgent/console/frontend

# 启动开发服务器
npm run dev

# 或使用其他命令
# npm run test  # 测试环境 (localhost)
```

预期输出:
```
  VITE vX.X.X  ready in XXX ms

  ➜  Local:   http://localhost:1881/
  ➜  Network: use --host to expose
```

#### 访问前端

在浏览器中打开: **http://localhost:1881**

如果绕过认证成功,应该能直接看到主界面,而不是登录页面。

---

## 验证和测试

### 1. 测试后端接口

```bash
# 测试不需要认证的接口
curl http://localhost:8080/health

# 测试需要认证的接口 (应该能直接访问,无需 token)
curl http://localhost:8080/api/model/checkModelBase

# 测试 Workflow 相关接口
curl http://localhost:8080/workflow/version
```

### 2. 测试前端功能

1. 打开浏览器: http://localhost:1881
2. 检查是否跳过了登录页面
3. 尝试访问各个功能模块
4. 打开浏览器开发者工具 (F12) 查看控制台是否有错误

### 3. 测试前后端联调

1. 在前端触发一个 API 调用 (如创建 Workflow)
2. 观察后端控制台日志
3. 检查网络请求是否成功 (F12 → Network)

---

## 常见问题

### 1. 后端启动失败: ClassNotFoundException

**症状**:
```
错误: 找不到或无法加载主类 com.iflytek.astron.console.hub.HubApplication
```

**解决**:
```bash
# 删除 IDEA 缓存
rm -rf /Users/itwanger/Documents/GitHub/PaiAgent/.idea/
rm -rf /Users/itwanger/Documents/GitHub/PaiAgent/out/

# 重新导入 Maven 项目
# File → Close Project
# File → Open → 选择 console/backend/pom.xml → Open as Project

# 重新编译
cd console/backend
mvn clean compile -pl hub -am
```

详细解决方案参考: [IntelliJ IDEA 本地调试 Console-Hub 指南-补充.md](./IntelliJ%20IDEA%20本地调试%20Console-Hub%20指南-补充.md)

### 2. 数据库连接失败

**症状**:
```
Cannot create PoolableConnectionFactory (Access denied for user 'root'@'localhost')
```

**解决**:

**情况 A: 密码错误**
```bash
# 确认 MySQL root 密码
mysql -h localhost -u root -p

# 修改 application-minimal.yml 中的密码
vim console/backend/hub/src/main/resources/application-minimal.yml
# password: 123456  # 改为实际密码
```

**情况 B: 数据库不存在**
```bash
# 创建数据库
mysql -h localhost -u root -p -e "CREATE DATABASE IF NOT EXISTS astron_console CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

**情况 C: MySQL 未启动**
```bash
# macOS (Homebrew)
brew services start mysql

# 或手动启动
mysql.server start

# Linux
systemctl start mysql

# 验证 MySQL 运行
mysql -h localhost -u root -p -e "SELECT VERSION();"
```

### 2. Redis 连接失败 (使用 minimal profile 应该不会报错)

**症状**:
```
Unable to connect to Redis
```

**解决**:

**确认**: `application-minimal.yml` 已经禁用了 Redis 自动配置:
```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
```

如果仍然报错,检查:

```bash
# 检查 Redis 是否运行
redis-cli ping
# 预期输出: PONG

# 如果 Redis 未运行 (macOS Homebrew)
brew services start redis

# 或手动启动
redis-server

# Linux
systemctl start redis
```

### 4. 前端显示 Casdoor 登录页面

**症状**: 访问 http://localhost:1881 时跳转到 Casdoor 登录页

**解决**:

```bash
# 确认 .env.local 文件存在且配置正确
cat console/frontend/.env.local

# 应该包含:
# CONSOLE_CASDOOR_URL=
# CONSOLE_CASDOOR_ID=
# CONSOLE_CASDOOR_APP=
# CONSOLE_CASDOOR_ORG=

# 重启前端开发服务器
cd console/frontend
npm run dev
```

如果仍然跳转,可能是前端代码有硬编码的认证逻辑,需要修改路由守卫:

```bash
# 查找认证相关代码
grep -r "casdoor\|Casdoor\|CASDOOR" console/frontend/src/

# 临时禁用认证检查 (在路由守卫中)
# 编辑 src/router/index.ts 或 src/router/index.js
```

### 5. 端口被占用

**症状**:
```
Port 8080 is already in use
```

**解决**:

```bash
# 查找占用端口的进程
lsof -i :8080

# 终止进程
kill -9 <PID>

# 或修改后端端口 (在 Run Configuration VM options 中添加)
-Dserver.port=8081

# 同时修改前端 .env.local
# VITE_BASE_URL=http://localhost:8081
```

### 6. MinIO 连接失败

**症状**:
```
Unable to execute HTTP request: Connection refused
```

**解决**:

```bash
# 检查 MinIO 状态 (macOS Homebrew)
brew services list | grep minio

# 启动 MinIO
brew services start minio

# 或手动启动
minio server ~/minio-data --console-address ":9001"

# Linux
systemctl start minio

# 访问 MinIO Console 验证
# http://localhost:9001
# 用户名: minioadmin (或你配置的用户名)
# 密码: minioadmin (或你配置的密码)

# 确认 bucket 存在
# 在 MinIO Console 中查看 Buckets,确保有 astron-agent bucket
# 或使用 mc 命令行工具:
mc ls local/
# 如果没有 astron-agent bucket,创建它:
mc mb local/astron-agent
```

### 7. 前端编译错误

**症状**:
```
Module not found: Can't resolve 'xxx'
```

**解决**:

```bash
# 删除 node_modules 和锁文件
cd console/frontend
rm -rf node_modules package-lock.json

# 重新安装
npm install

# 清除 Vite 缓存
rm -rf node_modules/.vite

# 重启开发服务器
npm run dev
```

### 8. 跨域 (CORS) 错误

**症状**:
```
Access to XMLHttpRequest at 'http://localhost:8080/api/...' from origin 'http://localhost:1881' has been blocked by CORS policy
```

**解决**:

**确认后端 CORS 配置**: `SecurityConfig.java` 已经允许所有来源:
```java
configuration.setAllowedOriginPatterns(List.of("*"));
```

如果使用 `MinimalSecurityConfig` (推荐),需要添加 CORS 配置:

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOriginPatterns(List.of("*"));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowCredentials(false);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
}
```

然后在 `minimalSecurityFilterChain` 中启用:

```java
.cors(cors -> cors.configurationSource(corsConfigurationSource()))
```

---

## 推荐的本地开发工作流

### 首次启动

```bash
# 1. 确保本地中间件运行
# MySQL
mysql -h localhost -u root -p -e "SELECT VERSION();"

# Redis
redis-cli ping

# MinIO (如果未运行,启动它)
# macOS Homebrew:
brew services start minio
# 或手动启动:
# minio server ~/minio-data --console-address ":9001"

# 2. 初始化数据库 (仅首次)
mysql -h localhost -u root -pYOUR_PASSWORD -e "CREATE DATABASE IF NOT EXISTS astron_console;"

# 3. 修改后端配置文件
vim console/backend/hub/src/main/resources/application-minimal.yml
# 修改:
#   - spring.datasource.password (你的 MySQL 密码)
#   - s3.endpoint (改为 http://localhost:9000)
#   - s3.accessKey (你的 MinIO accessKey)
#   - s3.secretKey (你的 MinIO secretKey)

# 4. 编译后端
cd console/backend
mvn clean install -DskipTests

# 5. 创建 MinimalSecurityConfig.java (绕过认证)
cat > hub/src/main/java/com/iflytek/astron/console/hub/config/MinimalSecurityConfig.java << 'EOF'
package com.iflytek.astron.console.hub.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@ConditionalOnProperty(name = "spring.profiles.active", havingValue = "minimal")
public class MinimalSecurityConfig {

    @Bean
    public SecurityFilterChain minimalSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .anyRequest().permitAll()
            )
            .csrf(AbstractHttpConfigurer::disable)
            .cors(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .securityContext(AbstractHttpConfigurer::disable)
            .sessionManagement(AbstractHttpConfigurer::disable);
        
        return http.build();
    }
}
EOF

# 6. 安装前端依赖
cd ../frontend
npm install

# 7. 创建前端环境变量文件
cat > .env.local << 'EOF'
VITE_BASE_URL=http://localhost:8080
CONSOLE_API_URL=http://localhost:8080
CONSOLE_CASDOOR_URL=
CONSOLE_CASDOOR_ID=
CONSOLE_CASDOOR_APP=
CONSOLE_CASDOOR_ORG=
EOF

# 8. 启动后端 (在 IDEA 中使用 minimal profile)
#    或命令行: cd ../backend/hub && mvn spring-boot:run -Dspring-boot.run.profiles=minimal

# 9. 启动前端
npm run dev

# 10. 访问 http://localhost:3000 (或 1881,取决于 vite.config.js 配置)
```

### 日常开发

```bash
# Terminal 1: 后端 (IDEA Debug 模式)
# 修改代码后,IDEA 会自动热重载 (DevTools)
# 或手动重启: ⌘F9 (Build Project) → 重启 Debug

# Terminal 2: 前端
cd console/frontend
npm run dev
# Vite 自动热重载,修改代码即时生效

# Terminal 3: 查看本地服务日志 (可选)
# MySQL 日志
tail -f /usr/local/var/mysql/*.err  # macOS Homebrew

# MinIO 日志
# 查看 MinIO 启动时的控制台输出
```

---

## 环境变量配置总结

### 后端环境变量 (application-minimal.yml)

| 变量名 | 默认值 | 说明 | 是否必需 |
|--------|--------|------|----------|
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/astron_console` | MySQL 连接 URL | ✅ 必需 |
| `spring.datasource.username` | `root` | MySQL 用户名 | ✅ 必需 |
| `spring.datasource.password` | 你的实际密码 | MySQL 密码 | ✅ 必需 (根据实际修改) |
| `s3.endpoint` | `http://localhost:9000` | MinIO API 端点 | ✅ 必需 (本地默认 9000) |
| `s3.accessKey` | `minioadmin` | MinIO 访问密钥 | ✅ 必需 (根据实际修改) |
| `s3.secretKey` | `minioadmin` | MinIO 密钥 | ✅ 必需 (根据实际修改) |
| `s3.bucket` | `astron-agent` | MinIO Bucket 名称 | ✅ 必需 |
| `workflow.chatUrl` | `http://localhost:7881/...` | Workflow 服务 URL | ⚠️ 可选 (不使用 Workflow 可留空) |

### 前端环境变量 (.env.local)

| 变量名 | 值 | 说明 | 是否必需 |
|--------|-----|------|----------|
| `VITE_BASE_URL` | `http://localhost:8080` | 后端 API 地址 | ✅ 必需 |
| `CONSOLE_API_URL` | `http://localhost:8080` | 后端 API 地址 (备用) | ✅ 必需 |
| `CONSOLE_CASDOOR_URL` | *(留空)* | Casdoor 服务地址 | ✅ 必需留空以绕过认证 |
| `CONSOLE_CASDOOR_ID` | *(留空)* | Casdoor 客户端 ID | ✅ 必需留空 |
| `CONSOLE_CASDOOR_APP` | *(留空)* | Casdoor 应用名 | ✅ 必需留空 |
| `CONSOLE_CASDOOR_ORG` | *(留空)* | Casdoor 组织名 | ✅ 必需留空 |

---

## 参考文档

- [AGENTS.md - 项目构建说明](../AGENTS.md)
- [IntelliJ IDEA 本地调试 Console-Hub 指南-补充.md](./IntelliJ%20IDEA%20本地调试%20Console-Hub%20指南-补充.md)
- [Docker 部署指南](../docker/astronAgent/README.md)
- [前端开发指南](../console/frontend/README.md)
- [后端 API 文档](http://localhost:8080/swagger-ui/index.html) (启动后端后访问)

---

## 快速验证清单

启动完成后,使用以下清单验证所有服务是否正常:

```bash
# ✅ 本地中间件健康检查
# MySQL
mysql -h localhost -u root -p -e "SHOW DATABASES;" | grep astron_console

# Redis
redis-cli ping  # 应该返回 PONG

# MinIO
curl http://localhost:9000/minio/health/live  # 应该返回 200
# 或访问 Console: http://localhost:9001

# ✅ 后端健康检查
curl http://localhost:8080/health  # 应该返回 {"status":"UP"}

# ✅ 前端访问
# 打开浏览器: http://localhost:3000 (或 1881,取决于 vite.config.js 配置)
# 应该能看到主界面,而不是登录页

# ✅ API 调用测试
curl http://localhost:8080/api/model/checkModelBase  # 应该返回 200

# ✅ 数据库连接测试
mysql -h localhost -u root -p -e "USE astron_console; SHOW TABLES;"
```

如果所有测试通过,恭喜你已经成功在本地启动了 Console Backend 和 Frontend! 🎉

---

**最后更新**: 2025-11-15  
**维护者**: PaiAgent Team
