# .mvn 目录说明

这个目录包含 Maven 项目的配置文件，确保 Maven 使用 Java 21 进行编译。

## 文件说明

### 1. `jvm.config`
配置 Maven 运行时的 JVM 参数:
- Java 版本: 21
- 编码: UTF-8
- 内存设置: 512m - 2048m

### 2. `maven.config`
配置 Maven 构建参数:
- 使用 Java 21 toolchain
- 并行构建 (-T 1C)
- 显示错误堆栈 (-e)

### 3. `toolchains.xml`
指定 Maven 使用的 JDK 路径:
- 明确指向 Java 21 的安装路径
- 确保 IDEA 和命令行都使用相同的 JDK

## IntelliJ IDEA 配置

有了这些配置后，IDEA 会自动识别并使用 Java 21。

### 步骤:

1. **重新加载 Maven 项目**
   ```
   右键点击 console/backend/pom.xml
   Maven → Reload Project
   ```

2. **验证 JDK 设置**
   ```
   File → Project Structure → Project
   SDK: 应该自动选择 21
   ```

3. **验证 Maven 设置**
   ```
   File → Settings → Build Tools → Maven → Runner
   JRE: 应该自动选择 21
   ```

## 命令行验证

```bash
cd /Users/itwanger/Documents/GitHub/PaiAgent/console/backend

# 清理并重新构建
mvn clean install -DskipTests

# 查看 Maven 使用的 Java 版本
mvn -version
```

## 如果 Java 21 路径变化

如果将来 Java 21 升级到新版本，需要更新 `toolchains.xml` 中的路径:

```bash
# 获取当前 Java 21 路径
/usr/libexec/java_home -v 21

# 更新 .mvn/toolchains.xml 中的 <jdkHome> 值
```

## 优势

✅ **项目级配置**: 所有开发者使用相同的 Java 版本  
✅ **IDEA 自动识别**: 无需手动配置 IDEA  
✅ **命令行一致**: 终端和 IDEA 使用相同配置  
✅ **持久化配置**: 配置文件提交到 Git，团队共享
