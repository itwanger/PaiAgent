# IntelliJ IDEA 打开项目并找到主类的步骤

## 问题原因

IDEA 找不到主类通常是因为:
1. ❌ Maven 项目没有正确导入
2. ❌ 依赖没有下载完成
3. ❌ 打开的目录层级不对

## ✅ 正确步骤

### 1️⃣ 关闭 IDEA 中所有已打开的项目
```
File → Close Project
```

### 2️⃣ 重新打开项目 (重要!)

**正确做法:**
```
File → Open
选择目录: /Users/itwanger/Documents/GitHub/PaiAgent/console/backend
点击: Open
```

**IDEA 会提示 "Trust and Open Project in New Window?" → 选择 "Trust Project"**

### 3️⃣ 等待 Maven 导入完成

**观察 IDEA 右下角:**
- 会显示 "Importing Maven projects..."
- 等待进度条完成 (可能需要几分钟)

**如果没有自动导入:**
1. 右键点击 `console/backend/pom.xml`
2. 选择 **"Maven → Reload Project"**

### 4️⃣ 配置 JDK 为 Java 21

**检查 Project SDK:**
```
File → Project Structure → Project
Project SDK: 选择 21 (如果没有，点击 New → Download JDK → 选择 21)
Project language level: 21 - Pattern matching for switch
```

**检查 Maven 设置:**
```
File → Settings → Build, Execution, Deployment → Build Tools → Maven → Runner
JRE: 选择 21
```

### 5️⃣ 标记源代码目录 (如果 IDEA 没有自动识别)

**右键点击目录并标记:**
- `console/backend/hub/src/main/java` → **Mark Directory as → Sources Root**
- `console/backend/commons/src/main/java` → **Mark Directory as → Sources Root**
- `console/backend/toolkit/src/main/java` → **Mark Directory as → Sources Root**

### 6️⃣ 创建运行配置

**方式 1: 快捷方式 (推荐)**

1. 打开文件: `console/backend/hub/src/main/java/com/iflytek/astron/console/hub/HubApplication.java`
2. 右键点击 `main` 方法旁边的绿色三角形 ▶️
3. 选择 **"Run 'HubApplication.main()'"** 或 **"Debug 'HubApplication.main()'"**
4. IDEA 会自动创建运行配置

**方式 2: 手动创建**

```
Run → Edit Configurations... → + → Application (不是 Spring Boot!)
```

配置:
- **Name**: `console-hub (local debug)`
- **Main class**: `com.iflytek.astron.console.hub.HubApplication`
  - 点击 `...` 按钮 → 输入 `HubApplication` → 选择 `com.iflytek.astron.console.hub.HubApplication`
- **Module**: `hub` (从下拉列表选择)
- **Working directory**: `$MODULE_WORKING_DIR$`
- **JRE**: `21`

**添加环境变量:**

点击 **Environment variables** 旁边的 **Browse** 按钮，添加:

```
MYSQL_URL=jdbc:mysql://localhost:3306/astron_console?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false
MYSQL_USER=root
MYSQL_PASSWORD=root123
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_DATABASE_CONSOLE=1
WORKFLOW_CHAT_URL=http://localhost:7881/api/v1/workflow/chat/stream
WORKFLOW_DEBUG_URL=http://localhost:7881/api/v1/workflow/chat/stream
WORKFLOW_RESUME_URL=http://localhost:7881/api/v1/workflow/chat/resume
WORKFLOW_URL=http://localhost:7880
MAAS_WORKFLOW_VERSION=http://127.0.0.1:8080/workflow/version
MAAS_WORKFLOW_CONFIG=http://127.0.0.1:8080/workflow/get-flow-advanced-config
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=local
LOGGING_LEVEL_ROOT=INFO
LOGGING_LEVEL_COM_IFLYTEK=DEBUG
```

### 7️⃣ 验证配置

**检查 Module 是否正确识别:**
```
File → Project Structure → Modules
```

应该看到:
- ✅ `parent` (console/backend)
- ✅ `hub` (console/backend/hub)
- ✅ `commons` (console/backend/commons)
- ✅ `toolkit` (console/backend/toolkit)

每个 Module 应该有 **Sources** 和 **Dependencies** 标签。

### 8️⃣ 启动调试

1. **点击 Debug 按钮** (Shift + F9)
2. **观察控制台输出**

**成功的标志:**
```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v3.x.x)

...
Started HubApplication in x.xxx seconds
```

## 🔧 故障排除

### ❌ "Cannot resolve symbol 'SpringApplication'"

**原因**: Maven 依赖没有下载完成

**解决**:
```bash
# 在终端中运行
cd /Users/itwanger/Documents/GitHub/PaiAgent/console/backend
mvn clean install -DskipTests
```

然后在 IDEA 中:
```
File → Invalidate Caches... → Invalidate and Restart
```

### ❌ "Module not specified"

**原因**: IDEA 没有识别 Maven 模块

**解决**:
1. 右键点击 `console/backend/pom.xml`
2. 选择 **"Add as Maven Project"**
3. 等待 Maven 导入完成

### ❌ "java: error: release version 21 not supported"

**原因**: Maven 使用的 JDK 不是 21

**解决**:
```
File → Settings → Build, Execution, Deployment → Build Tools → Maven → Runner
JRE: 选择 21
```

### ❌ 主类下拉列表为空

**原因**: 模块的 Sources Root 没有正确标记

**解决**:
1. 右键点击 `console/backend/hub/src/main/java`
2. **Mark Directory as → Sources Root**
3. 等待 IDEA 索引完成
4. 重新打开 Run Configuration，主类应该可以搜索到了

## 📸 截图参考

### Project Structure 应该显示的模块
```
PaiAgent
└── console
    └── backend
        ├── hub (Module)
        │   └── src/main/java (Sources Root)
        ├── commons (Module)
        │   └── src/main/java (Sources Root)
        └── toolkit (Module)
            └── src/main/java (Sources Root)
```

### Run Configuration 正确配置示例
```
Name: console-hub (local debug)
Main class: com.iflytek.astron.console.hub.HubApplication
Use classpath of module: hub
JRE: 21
Environment variables: [已配置]
```

## 🎯 快速验证脚本

运行以下命令验证项目结构:

```bash
cd /Users/itwanger/Documents/GitHub/PaiAgent/console/backend

# 验证主类存在
ls -la hub/src/main/java/com/iflytek/astron/console/hub/HubApplication.java

# 验证 Maven 配置正确
mvn help:effective-pom -pl hub | grep -A5 mainClass

# 编译项目
mvn clean compile -DskipTests
```

如果上述命令都成功，项目结构就是正确的。

---

**完成以上步骤后，IDEA 应该能正确识别主类并启动调试！** 🎉
