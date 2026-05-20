# Build Parameter History Plugin（构建参数历史插件）

一个 Jenkins 插件，用于记录和显示 Jenkins 任务的构建参数历史。

## 功能特性

- 自动记录每次构建执行的构建参数
- 以简洁、可搜索的表格视图显示参数历史
- 按构建结果、参数名称、参数值筛选记录
- 全局搜索所有字段
- 分页显示，支持配置每页大小
- 通过 API 或 UI 按钮下载历史文件
- 自动清理：每个任务只保留最近 200 条记录（可配置）
- **全局配置**：在 Jenkins 系统设置中配置默认保存的记录条目数
- 支持所有标准 Jenkins 构建参数
- **国际化支持**：根据浏览器语言设置自动切换中文和英文
- **记录选择与批量删除**：选择多条记录并批量删除
- **清除历史**：选项清除任务的所有构建参数历史

## 安装方法

1. 构建插件：
   ```bash
   mvn clean package
   ```

2. `.hpi` 文件将生成在 `target/` 目录下

3. 通过 Jenkins Web UI 安装：
   - 进入 **Manage Jenkins** → **Plugins** → **Advanced settings**
   - 上传 `.hpi` 文件或指定生成的文件路径

## 使用说明

### 查看历史记录

1. 导航到任何带有构建参数的 Jenkins 任务
2. 点击左侧边栏中的 **"Build Parameter History"（构建参数历史）**
3. 查看包含所有已记录构建的历史表格

### 筛选记录

使用筛选面板缩小结果范围：
- **Build Result（构建结果）**：按 Success、Failure、Unstable、Aborted、Not Built 筛选
- **Parameter Name（参数名称）**：按参数名称搜索
- **Parameter Value（参数值）**：按参数值搜索
- **Global Search（全局搜索）**：搜索所有字段

### 国际化支持

插件会自动适配浏览器的语言设置：

| 浏览器语言 | 显示语言 |
|------------|----------|
| English (默认) | 英文 |
| Chinese (zh-CN) | 简体中文 |

无需手动配置 - 插件会检测 `Accept-Language` 请求头并相应切换。Jelly 模板使用 Jenkins 标准的 `${%key}` 语法进行国际化解析，从 Jelly 文件同一目录下的本地化属性文件（`index.properties`、`index_zh_CN.properties`、`filterResults.properties`、`filterResults_zh_CN.properties`）加载消息。

**资源文件编码**：所有属性文件均使用 UTF-8 编码，确保在所有浏览器环境中正确显示字符。

### 记录管理

- **批量删除**：使用复选框选择记录，然后点击 "Delete Selected"（删除所选）
- **全选**：使用表格标题中的复选框选择/取消选择当前页的所有记录
- **清除全部**：点击 "Clear All History"（清除所有历史）按钮删除任务的所有记录

### 下载历史

#### 通过 Web UI
点击历史页面底部的 **"Download History"（下载历史）** 按钮。

#### 通过 API
使用 curl 下载历史文件：

```bash
curl -u username:api-token -o history.txt \
  "http://jenkins-url/job/your-job/buildParameterHistory/downloadHistory"
```

## API 端点

| 端点 | 方法 | 描述 |
|------|------|------|
| `/job/{job}/buildParameterHistory` | GET | 查看历史页面 |
| `/job/{job}/buildParameterHistory/downloadHistory` | POST | 下载历史文件 |
| `/job/{job}/buildParameterHistory/filterResults` | POST | 按条件筛选记录 |
| `/job/{job}/buildParameterHistory/deleteRecords` | POST | 删除所选记录 |
| `/job/{job}/buildParameterHistory/clearHistory` | POST | 清除任务的所有历史 |

## 配置

### 全局配置（推荐）

插件支持通过 Jenkins 系统设置配置默认保存的记录条目数：

1. 登录 Jenkins
2. 进入 **Manage Jenkins** → **System Configuration**（系统设置）
3. 找到 **Build Parameter History** 配置区域
4. 在 **Max Records Per Job**（每个任务最大记录数）字段中输入所需的数值
5. 点击 **Save**（保存）

**配置参数：**

| 参数 | 说明 | 默认值 | 范围 |
|------|------|--------|------|
| Max Records Per Job | 每个任务保留的构建参数记录最大数量 | 200 | 1 - 10000 |

当记录数超过限制时，系统会自动删除最旧的记录。

### 历史文件

记录存储在每个任务的目录中，文件名为 `param_history`。

## 构建

```bash
# 构建插件
mvn clean package

# 运行测试
mvn test

# 跳过测试
mvn package -DskipTests
```

## 测试

项目包含全面的单元测试套件，共 **57 个测试用例**：

### 测试类

| 测试类 | 测试数 | 覆盖范围 |
|--------|--------|----------|
| `BuildParameterRecordTest` | 21 | 模型：构造函数、getter/setter、安全 URL、时长、格式化时间、ParameterEntry |
| `BuildParameterHistoryServiceTest` | 36 | 服务：格式/解析往返、筛选逻辑（结果/关键词/参数名称/值）、文件 I/O、缓存过期、最大记录数配置、单例模式 |

### 运行测试

```bash
# 运行所有测试
mvn test -Denforcer.skip=true

# 运行特定测试类
mvn test -Denforcer.skip=true -Dtest=BuildParameterRecordTest

# 带详细输出运行
mvn test -Denforcer.skip=true -X
```

## 需求

- Jenkins 2.479.2 或更高版本
- Java 11 或更高版本

## 许可证

MIT License - 详见 LICENSE 文件

## 版本

1.0.2

---

*[English Version](README_en.md)*