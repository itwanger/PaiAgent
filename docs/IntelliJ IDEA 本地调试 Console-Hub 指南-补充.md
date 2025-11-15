# IntelliJ IDEA Debug HubApplication 完整配置指南

## 问题现象

运行 HubApplication 时报错:
```
错误: 找不到或无法加载主类 com.iflytek.astron.console.hub.HubApplication
原因: java.lang.ClassNotFoundException: com.iflytek.astron.console.hub.HubApplication
```

classpath 错误指向: `/Users/itwanger/Documents/GitHub/PaiAgent/out/production/PaiAgent`

## 根本原因

IDEA 将项目识别为**普通 Java 项目**而不是 **Maven 项目**，导致:
- 使用错误的输出目录 (`out/` 而不是 `target/`)
- 右键 `pom.xml` 没有 Maven 选项
- 无法正确解析模块依赖

## 解决方案：重新导入为 Maven 项目

### 步骤 1: 关闭当前项目

1. `File` → `Close Project`
2. 或直接退出 IDEA

### 步骤 2: 删除 IDEA 配置文件

```bash
cd /Users/itwanger/Documents/GitHub/PaiAgent
rm -rf .idea/
rm -rf *.iml
```

### 步骤 3: 以 Maven 项目方式重新打开

**方式 A: 打开 Backend Maven 项目 (推荐)**

1. 启动 IntelliJ IDEA
2. 在欢迎界面点击 **Open**
3. **导航到并选择**: `/Users/itwanger/Documents/GitHub/PaiAgent/console/backend/pom.xml`
4. **重要**: 选择 **Open as Project** (不是 Open as File)
5. 等待 IDEA 自动导入 Maven 依赖

**方式 B: 从根目录导入 Maven 模块**

如果需要同时开发前端和后端:

1. 启动 IntelliJ IDEA
2. 点击 **Open**
3. 选择整个项目目录: `/Users/itwanger/Documents/GitHub/PaiAgent`
4. 打开后，在 Project 窗口中:
   - 右键点击 `console/backend/pom.xml`
   - 选择 **Add as Maven Project**

### 步骤 4: 启用 Maven 自动导入

在 IDEA 右下角可能会弹出提示:
- **Maven projects need to be imported**
- 点击 **Enable Auto-Import** 或 **Import Changes**

或手动配置:
1. **Preferences** (⌘,) → **Build, Execution, Deployment** → **Build Tools** → **Maven**
2. 勾选 ✅ **Automatically download sources**
3. 勾选 ✅ **Automatically download documentation**

### 步骤 5: 配置 IDEA 使用 Maven 构建

1. **打开设置**: `IntelliJ IDEA` → `Preferences` (⌘,)

2. **导航到 Maven Runner**:
   ```
   Build, Execution, Deployment 
     → Build Tools 
       → Maven 
         → Runner
   ```

3. **勾选以下选项**:
   - ✅ **Delegate IDE build/run actions to Maven**

4. **点击 Apply → OK**

### 步骤 6: 验证 Maven 配置

1. **打开 Maven 工具窗口**: `View` → `Tool Windows` → `Maven`
2. 应该能看到:
   ```
   console-backend
     ├─ commons
     ├─ hub
     └─ toolkit
   ```
3. 点击刷新按钮 (🔄) 重新加载所有 Maven 项目

### 步骤 7: 等待索引和编译完成

- 观察 IDEA 右下角进度条
- 等待 "Indexing..." 和 "Building..." 完成

### 步骤 8: 执行 Maven Compile

在 Terminal 或 Maven 工具窗口中执行:
```bash
cd /Users/itwanger/Documents/GitHub/PaiAgent/console/backend
mvn clean compile -pl hub -am
```

### 步骤 9: 创建 Run Configuration

**方式 A: 自动创建 (推荐)**

1. 打开 `console/backend/hub/src/main/java/com/iflytek/astron/console/hub/HubApplication.java`
2. 右键文件编辑器 → **Run 'HubApplication.main()'**
3. IDEA 会自动创建配置并运行

**方式 B: 手动创建**

1. `Run` → `Edit Configurations...`
2. 点击 `+` → `Application`
3. 配置如下:

   ```
   Name: HubApplication (Debug)
   
   Build and run:
   Java 21 -cp astron-console-hub.main
   
   Main class: 
   com.iflytek.astron.console.hub.HubApplication
   
   VM options (点击 Modify options → Add VM options):
   -Dspring.profiles.active=minimal
   
   Environment variables (点击 Modify options → Add environment variables):
   MYSQL_URL=jdbc:mysql://localhost:3306/astron_console;MYSQL_USER=root;MYSQL_PASSWORD=root123;REDIS_HOST=localhost;REDIS_PORT=6379
   
   Use classpath of module: 
   astron-console-hub.main (重要!)
   
   JRE: 
   21 (Project SDK)
   
   Working directory:
   $MODULE_WORKING_DIR$
   ```

4. **点击 Apply → OK**

### 步骤 10: Debug 运行

1. 在 `HubApplication.java` 的 `main` 方法中设置断点
2. 点击 Debug 按钮 (🐞) 启动
3. 应该能正常启动并停在断点处

## 验证导入成功的标志

✅ **Maven 工具窗口**可见，显示所有模块  
✅ 右键 `pom.xml` 有 **Maven** 菜单选项  
✅ `console/backend/hub/target/classes/` 目录存在  
✅ External Libraries 包含所有 Maven 依赖  
✅ Project 视图中模块图标正确显示 (有 Maven 图标)  
✅ Run Configuration 的 classpath 指向 `astron-console-hub.main`

## 如果还是没有 Maven 选项

### 检查 Maven 插件是否启用

1. **Preferences** → **Plugins**
2. 搜索 **Maven**
3. 确保以下插件已启用:
   - ✅ Maven
   - ✅ Maven Integration
4. 如果被禁用，启用后重启 IDEA

### 手动添加为 Maven 项目

1. 右键 `console/backend/pom.xml`
2. 如果看到 **Add as Maven Project**，点击它
3. 等待 IDEA 重新导入

### 完全重置 IDEA 缓存

```bash
# 删除所有 IDEA 配置
cd /Users/itwanger/Documents/GitHub/PaiAgent
rm -rf .idea/
rm -rf *.iml
rm -rf console/backend/*.iml
rm -rf console/backend/*/*.iml
rm -rf out/

# 在 IDEA 中: File → Invalidate Caches → Invalidate and Restart
```

## 绕过 Casdoor 认证的配置

### 使用 minimal profile (推荐)

在 Run Configuration 中已配置:
```
-Dspring.profiles.active=minimal
```

这会自动禁用:
- ✅ OAuth2 认证 (Casdoor)
- ✅ Redis 连接
- ✅ Redisson 分布式锁

### 环境变量说明

```bash
# 数据库配置 (必需)
MYSQL_URL=jdbc:mysql://localhost:3306/astron_console
MYSQL_USER=root
MYSQL_PASSWORD=root123

# Redis 配置 (minimal profile 下可选)
REDIS_HOST=localhost
REDIS_PORT=6379

# 其他可选配置
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_USER=spark
POSTGRES_PASSWORD=spark123
```

### 前端配置 (可选)

如果同时开发前端，编辑 `console/frontend/.env.development`:
```env
# 后端地址
VITE_API_BASE_URL=http://localhost:8080

# 禁用 Casdoor 认证 (注释掉或留空)
# VITE_CASDOOR_ENDPOINT=
# VITE_CASDOOR_CLIENT_ID=
# VITE_CASDOOR_APP_NAME=
# VITE_CASDOOR_ORG_NAME=
```

## 常见问题排查

### 问题 1: ClassNotFoundException

**症状**: 找不到主类  
**原因**: classpath 指向错误的 `out/` 目录  
**解决**: 
```bash
# 删除 out 目录
rm -rf /Users/itwanger/Documents/GitHub/PaiAgent/out/

# 使用 Maven 编译
cd console/backend
mvn clean compile -pl hub -am

# 在 Run Configuration 中确认:
Use classpath of module: astron-console-hub.main
```

### 问题 2: 模块依赖找不到

**症状**: 编译时报错 `cannot find symbol`  
**原因**: 依赖模块 (commons, toolkit) 未编译  
**解决**:
```bash
cd console/backend
mvn clean install -pl commons,toolkit -am
```

### 问题 3: Maven 依赖下载失败

**症状**: External Libraries 为空或缺少依赖  
**解决**:
```bash
# 清除本地仓库缓存
rm -rf ~/.m2/repository/com/iflytek/astron/console/

# 重新下载
cd console/backend
mvn clean install -U

# 在 IDEA 中刷新
右键 pom.xml → Maven → Reload Project
```

### 问题 4: 数据库连接失败

**症状**: 启动时报错 `Cannot create PoolableConnectionFactory`  
**解决**:
```bash
# 确保 MySQL 运行
docker ps | grep mysql

# 或启动 Docker 中的 MySQL
cd docker/astronAgent
docker compose up -d mysql

# 确认数据库存在
mysql -h localhost -u root -proot123 -e "CREATE DATABASE IF NOT EXISTS astron_console;"
```

### 问题 5: 端口被占用

**症状**: `Port 8080 is already in use`  
**解决**:
```bash
# 查找占用端口的进程
lsof -i :8080

# 终止进程
kill -9 <PID>

# 或修改端口
在 Run Configuration 的 VM options 中添加:
-Dserver.port=8081
```

## 推荐的本地开发工作流

### 1. 首次设置

```bash
# 1. 启动必需的中间件
cd docker/astronAgent
docker compose up -d mysql postgres redis

# 2. 初始化数据库
mysql -h localhost -u root -proot123 < docker/astronAgent/mysql/init.sql

# 3. 编译项目
cd console/backend
mvn clean install -DskipTests

# 4. 在 IDEA 中配置 Run Configuration (使用 minimal profile)

# 5. Debug 启动 HubApplication
```

### 2. 日常开发

```bash
# 修改代码后
# - IDEA 会自动编译 (Build project automatically)
# - 或手动: ⌘F9 (Build Project)
# - 重启 Debug 进程

# 如果依赖变化
mvn clean compile -pl hub -am
```

### 3. 前端联调

```bash
# Terminal 1: 后端 (IDEA Debug)
# 启动 HubApplication (端口 8080)

# Terminal 2: 前端
cd console/frontend
npm run dev
# 访问 http://localhost:1881
```

## 快速验证命令

```bash
# 验证 Maven 配置
cd /Users/itwanger/Documents/GitHub/PaiAgent/console/backend
mvn -version
mvn clean compile -pl hub -am

# 验证 Java 版本
java -version  # 应该是 21

# 验证数据库连接
mysql -h localhost -u root -proot123 -e "SHOW DATABASES;" | grep astron_console

# 验证编译输出
ls -la console/backend/hub/target/classes/com/iflytek/astron/console/hub/HubApplication.class
```

## 参考文档

- [原始本地调试指南](./IntelliJ%20IDEA%20本地调试%20Console-Hub%20指南.md)
- [AGENTS.md - 项目构建说明](../AGENTS.md)
- [环境变量配置说明](./环境变量配置说明.md)
