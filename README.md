# SmartKnowledgeDB 后端

基于 Java 17、Spring Boot 3.5.16、MyBatis-Plus 3.5.17、MySQL 8 和 Flyway 的课程项目后端。现有 `ui` Flutter 工程保持不变。十五张业务表分为五组，字段数分别为 **34、25、29、27、35**，每组均满足三张表、至少二十一个字段。

## 启动

1. 安装 Java 17 和 MySQL 8；首次执行 Maven Wrapper 需要联网。启动前先由数据库管理员建立空数据库及专用账号，示例见 [数据库初始化](scripts/create-database.sql)。Flyway 自动创建业务表并写入年订阅套餐，不创建演示用户。
2. 在本目录打开 PowerShell，配置数据库：

```powershell
$env:DB_URL='jdbc:mysql://127.0.0.1:3306/smart_knowledge_db?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8'
$env:DB_USERNAME='smartknowledge'
$env:DB_PASSWORD='替换为数据库密码'
$env:SPRING_PROFILES_ACTIVE='demo'
.\mvnw.cmd spring-boot:run
```

Linux/macOS 使用 `./mvnw spring-boot:run`。Wrapper 固定 Maven 3.9.16，依赖下载至本项目 `.local/m2`。无需全局 Maven。不要提交 `.env`、`.local` 或真实密钥。

- Swagger UI：[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- OpenAPI：[http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- 健康检查：[http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

Swagger 的 Authorize 输入登录返回的 `accessToken`。访问令牌为随机不透明字符串，有效期三十分钟；刷新令牌三十天，每次刷新均轮换。服务端只保存 SHA256 摘要。一个用户的同一设备 UUID 再次登录会替换该设备会话令牌；不同设备可同时登录。

## 演示与资料

启动 `demo` 环境后执行：

```powershell
.\scripts\demo.ps1
```

脚本创建一个随机演示账号，完成订阅、授权、笔记上传、第二设备拉取、冲突解决、备份恢复及模拟 AI 标签。不输出密码或令牌。重复执行会创建新的演示账号。也可导入 [Postman 集合](docs/SmartKnowledgeDB.postman_collection.json) 按顺序操作。

- [接口与前端接入说明](docs/api.md)
- [数据库字典与字段统计](docs/database.md)
- [ER 图与五人开发分工](docs/architecture.md)
- [测试执行记录](docs/verification.md)

## 环境配置

|变量|默认值 / 用途|
|---|---|
|`PORT`|8080|
|`DB_URL`、`DB_USERNAME`、`DB_PASSWORD`|数据库连接；密码没有默认值|
|`SPRING_PROFILES_ACTIVE`|空；显式 `demo` 才开放模拟开通订阅|
|`AI_MODE`|普通环境 `real`，demo 环境 `mock`；mock 仅允许在 demo 使用|
|`AI_BASE_URL`|兼容 Chat Completions 的基础地址，包含 `/v1`，不含 `/chat/completions`|
|`AI_MODEL`、`AI_API_KEY`|真实模型名称及服务端密钥，没有预设模型或密钥|
|`AI_TIMEOUT_SECONDS`|30 秒，不自动重试提供方调用|
|`NOTE_MAX_BYTES`|正文 UTF-8 编码后 262144 字节|
|`BACKUP_RETENTION_DAYS`、`BACKUP_MAX_COUNT`|30 天、10 份|
|`CORS_ORIGINS`|`http://localhost:3000`；多个来源用逗号隔开|

真实 AI 未完整配置时返回 `503 AI_UNAVAILABLE`；不会自动转用模拟标签。密钥只从服务端环境读取，不接受客户端指定接口或密钥。模拟标签返回 `simulated:true`，仍计入演示月额度。

套餐参数通过 `subscription_plans` 管理。默认年订阅为 9900 分、365 天、104857600 字节存储、30 天历史、UTC 自然月 100 次 AI。真实支付没有接入，普通环境没有开通订阅的公开接口。不得将 demo 环境用于面向公众的收费服务。

## 测试与构建

集成测试默认启动 Testcontainers MySQL，需 Docker 已运行。也可指定**独立且名称以 `_test` 结尾**的 MySQL 测试库：

```powershell
$env:TEST_DB_URL='jdbc:mysql://127.0.0.1:3306/smart_knowledge_test?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
$env:TEST_DB_USERNAME='测试账号'
$env:TEST_DB_PASSWORD='测试密码'
.\mvnw.cmd test
.\mvnw.cmd package
java -jar target/backend-1.0.0.jar
```

Windows 下重新打包前，请先停止正在运行同名 JAR 的应用，避免文件占用阻止生成新包。

测试不会清空现有表，会生成隔离的随机测试账号及套餐；推荐使用可丢弃的空测试库验证首次迁移。仅检查静态表结构和字节计算可执行 `./mvnw -Dtest=SchemaContractTest test`，无需数据库。详细实际执行结果见测试记录。

## 运行边界

这是供课程演示的单体后端：不包含真实支付、短信邮件验证、管理后台页面、向量数据库或 Flutter 接入改造。关键词搜索使用参数化子串匹配，适合首期文本规模；未宣称大规模云端全文或语义检索性能。

备份和历史快照保存在 MySQL，定时任务每分钟清理过期快照和超时 AI 预占。同步日志、冲突记录和删除墓碑按课程方案保留，不自动清理，也不计入套餐快照容量；大规模上线前需另外制定其留存和成本政策。用户删除笔记为逻辑删除，原文可能仍存在于历史、备份及同步记录中，本期没有账号注销及彻底擦除功能。
