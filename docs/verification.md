# 验证记录

执行日期：2026-10-20（Asia/Tokyo）。

## 实际执行环境

- Windows，OpenJDK 17.0.12。
- Maven Wrapper 3.3.4，Maven 3.9.16。
- Spring Boot 3.5.16，MyBatis-Plus 3.5.17，springdoc-openapi 2.8.17。
- 独立 MySQL 8.0.46 实例，仅监听 127.0.0.1:13306；数据库为 `smart_knowledge_test`，未连接或修改用户已有 MySQL 服务的数据。
- 数据库最初为空，Flyway 成功执行 V1、V2；后续启动校验迁移校验和并识别数据库已为最新版本。

## 自动测试

**21 项测试，失败 0，错误 0，跳过 0。** Maven Surefire 详细报告保存在 `target/surefire-reports/`，该目录属于本地构建产物。

|测试类|数量|覆盖内容|
|---|---:|---|
|BackendIntegrationTest|17|真实 MySQL 迁移、认证撤销、云授权、订阅到期、租户隔离、笔记筛选、同步幂等、冲突三种选择、冲突期间再次更新、删除传播、分页、并发提交、备份恢复、历史恢复、备份上限、容量超限回滚、批次原子回滚、过期清理、AI 幂等、并发额度、真实协议适配、提供方失败和超时、Swagger 及 HTTP 错误格式|
|NormalEnvironmentTest|2|模拟开通控制器限定 demo；缺少真实配置或非 demo 使用 mock 时不预占额度|
|SchemaContractTest|2|十五张业务表、每表至少七字段及每组至少二十一字段；UTF-8 字节计量|

执行配置及命令：

```powershell
$env:TEST_DB_URL='jdbc:mysql://127.0.0.1:13306/smart_knowledge_test?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
$env:TEST_DB_USERNAME='root'
$env:TEST_DB_PASSWORD=''
.\mvnw.cmd package
```

以上账号仅用于本次临时且仅本机可访问的测试实例，不是项目默认数据库账号。常规运行应使用单独的数据库账号与密码。

## 可执行包与 HTTP 演示

- 成功生成 Spring Boot 可执行包 `target/backend-1.0.0.jar`。
- 分别以 demo 和普通环境启动打包应用，健康检查返回 UP。
- `scripts/demo.ps1 -BaseUrl http://127.0.0.1:18080` 全流程通过：注册登录、模拟订阅、授权、上传、第二设备拉取、冲突解决、备份下载及恢复为新笔记、模拟标签生成和重放只计一次额度。
- 普通环境使用已认证请求访问模拟开通返回 HTTP 404，普通环境 OpenAPI 不含该接口。
- 普通环境未配置真实 AI 时返回 HTTP 503、`AI_UNAVAILABLE`，没有降级为模拟服务。
- Flutter 仓库的工作区检查没有发现修改。
- 验证完成后，临时 demo、普通环境应用进程及独立 MySQL 测试实例均已停止；源码、文档与可执行包保留。

## 验证限制

- 真实 AI 协议使用本机可控 HTTP 服务验证成功、无效 JSON 和超时；没有使用真实厂商密钥，没有产生付费模型调用。
- Testcontainers 路径已提供，本次 Docker 未运行，实际使用独立本机 MySQL 完成集成测试。
- 没有接入或验证真实支付，也没有修改或联调 Flutter 登录同步界面。
- 并发测试验证一致性和额度限制，不属于大量笔记、长时间运行或公网部署的性能压测。
- Postman 集合已生成并校验 JSON 结构；完整演示通过 PowerShell 脚本执行，未在 Postman 客户端逐项运行集合。
