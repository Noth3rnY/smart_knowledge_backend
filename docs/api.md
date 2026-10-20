# 接口与 Flutter 接入

## 通用协议

前缀 `/api/v1`。除注册、登录、刷新、套餐列表外均携带 `Authorization: Bearer <accessToken>`。UUID 使用标准连字符形式；时间使用 UTC ISO8601。成功通常返回 HTTP 200：

```json
{"code":"OK","message":"成功","data":{},"requestId":"服务端生成的UUID"}
```

响应头同时包含 `X-Request-Id`。分页为 `data:{items:[],total:0,page:1,size:20}`，页码从一开始，最大 size=100。时间筛选为 `[from,to)`。Swagger/OpenAPI 及健康端点使用各自标准响应格式。

错误码：400 `INVALID_ARGUMENT`，401 `UNAUTHORIZED`，403 `CONSENT_REQUIRED` / `SUBSCRIPTION_REQUIRED`，404 `NOT_FOUND`，409 `VERSION_CONFLICT` / `IDEMPOTENCY_MISMATCH` / `AI_PENDING` / `BACKUP_LIMIT`，413 `CONTENT_TOO_LARGE` / `STORAGE_LIMIT`，429 `AI_QUOTA_EXCEEDED`，502 `AI_PROVIDER_ERROR` / `AI_TIMEOUT` / `AI_INVALID_RESPONSE`，503 `AI_UNAVAILABLE`。失败不会将 SQL、密码或提供方原始错误传给客户端。

## 路由一览

|方法|路径|请求或含义|
|---|---|---|
|POST|`/auth/register`|`username,password,nickname`；账号为 3—64 位英文、数字或下划线，统一小写；密码至少八字符、UTF-8 最多72字节|
|POST|`/auth/login`|`username,password,deviceUuid,deviceName,platform`|
|POST|`/auth/refresh`|`refreshToken`；成功后替换保存两种新令牌|
|POST|`/auth/logout`|撤销当前设备会话|
|GET / PATCH|`/me`|查询账号、授权及说明；修改提交 `nickname`|
|PUT|`/me/preferences`|完整提交 `syncEnabled,backupEnabled,aiEnabled,consentVersion`；说明版本从 GET /me 获取|
|GET|`/devices`|设备列表，不返回令牌摘要|
|DELETE|`/devices/{id}`|撤销登录返回的设备会话 `deviceId`，不是客户端 `deviceUuid`|
|GET|`/plans`|可用套餐|
|GET|`/subscriptions/me`|有效权益、快照存储字节数和本月 AI 次数|
|POST|`/subscriptions/demo-activate`|`requestId`；仅 demo 环境存在，固定开通年订阅；相同请求号可重放|
|GET|`/notes`|可选 keyword、tags、from、to、page、size；多标签取任一匹配|
|GET|`/notes/{id}`|笔记含全部标签和删除状态；删除笔记不在普通列表显示|
|POST|`/notes`|新建完整笔记，baseVersion=0|
|PUT|`/notes/{id}`|更新完整笔记，id 与路径一致，baseVersion 为当前服务端版本|
|DELETE|`/notes/{id}`|JSON 请求体 `{"baseVersion":1}`，产生删除墓碑|
|GET|`/tags`|当前用户标签字典；笔记删除后字典仍可用于重新使用|
|POST|`/sync/push`|批量变更，见下文|
|GET|`/sync/pull`|after 默认0，size 默认50，返回 items、nextCursor、hasMore|
|GET|`/sync/conflicts`|未解决冲突分页，含双方完整快照|
|POST|`/sync/conflicts/{id}/resolve`|serverVersion、choice，以及 merged 选择下的完整笔记|
|POST|`/backups`|`name`，创建当前全部未删除笔记的快照|
|GET|`/backups`|有效备份分页|
|GET|`/backups/{id}`|备份元数据|
|GET|`/backups/{id}/download`|下载 JSON，仍使用统一响应包装；data 内含 formatVersion、backup、notes|
|DELETE|`/backups/{id}`|删除备份及其快照项|
|POST|`/backups/{id}/restore`|恢复为新 UUID 的笔记，返回新旧来源映射|
|GET|`/notes/{id}/versions`|未过期历史快照分页|
|POST|`/notes/{id}/versions/{versionId}/restore`|`baseVersion`；versionId 为历史记录 UUID，恢复为同一笔记的新版本|
|POST|`/ai/tags`|requestId、title、content、existingTags；仅返回建议，不直接修改笔记|

默认未授权，也无免费云写入权益。笔记新增修改、同步提交和冲突解决需要同步授权及有效订阅；备份创建、备份恢复和历史恢复需要备份授权及有效订阅；AI 需要 AI 授权及有效订阅。已有数据的读取、同步拉取和删除不要求仍有授权或订阅，但始终验证登录和用户归属。

## 笔记与同步请求

完整笔记写入结构：

```json
{
  "id":"11111111-1111-4111-8111-111111111111",
  "baseVersion":0,
  "title":"界面渲染与掉帧优化",
  "content":"# 原因\n记录 Flutter 性能优化。",
  "tags":[{"name":"Flutter","source":"manual"}]
}
```

标题去除首尾空白；正文保留原文。单篇最多十个不同标签，名字去除首尾空白，来源只能 manual 或 llm。UUID 由客户端生成，向量字段不在上传协议中。完整响应额外包含 version、createdAt、updatedAt、deleted、deletedAt、sourceNoteId。

```json
{
  "deviceUuid":"22222222-2222-4222-8222-222222222222",
  "requestId":"33333333-3333-4333-8333-333333333333",
  "changes":[
    {"operation":"upsert","note":{"id":"11111111-1111-4111-8111-111111111111","baseVersion":0,"title":"标题","content":"正文","tags":[]}}
  ]
}
```

每批 1—100 条，同一笔记只能出现一次；delete 操作也携带完整笔记及 baseVersion。设备 UUID 必须与登录会话一致。对同一请求号，请保留原始请求，网络失败后原样重试；服务端按 DTO 固定字段顺序重新序列化计算摘要，不受 JSON 对象字段排列影响，数组顺序属于请求内容。

返回 `results` 中每项为 `applied` 加最新 `note`，或 `conflict` 加 `conflictId`。批次返回的 `revision` 是服务器最新序号，**不能直接用它推进本地拉取游标**，否则可能跳过尚未下载的其他设备修改。仅在本地事务成功应用整个 pull 页面后，把拉取游标推进为该页 nextCursor。

冲突解决示例为 `{"serverVersion":2,"choice":"submitted"}`；选择 server 则保留最新云端稿，选择 merged 时还需完整 merged 笔记，其 id 和 baseVersion 必须与当前云端一致。若返回 409 VERSION_CONFLICT，data 内含最新云端快照，重新展示并确认。服务端不会自动选择较晚设备时间覆盖另一方。

## AI 请求

```json
{"requestId":"44444444-4444-4444-8444-444444444444","title":"SQL 索引","content":"记录索引与查询优化。","existingTags":["数据库"]}
```

成功返回 requestId、tags、provider、model、simulated。客户端确认后，把建议标签以 source=llm 合并进笔记再正常保存。输入不依赖笔记已上传，可只授权并提交本次需要分析的内容。AI 请求记录保存请求摘要与标签结果，不额外保存正文。

成功请求重复提交返回原结果；处理中返回409 AI_PENDING；失败请求重复提交返回原失败状态，不重新调用提供方。需要实际再次生成时使用新请求号。预占归属 UTC 自然月，真实失败、格式错误或超时不扣额度。

## Flutter 数据映射

|现有本地字段|后续处理|
|---|---|
|notes.id 自增整数|保留作本地主键，另增唯一 cloudUuid；首次上传前持久化生成|
|title / content|直接映射标题与 Markdown 原文|
|tags.noteId / name / source|汇总为笔记 tags 数组；重新计算关联，不上传本地标签整数 ID|
|embedding / embeddingBackend|仅保留设备端；下载内容变化后使原向量失效并重新计算|
|createdAt / updatedAt|区分本地编辑时间与云端时间；同步冲突只依据版本号|
|新增 baseVersion|记录最后接受的云端 version；离线修改保留此基础版本|
|新增用户标识|本地数据、待上传队列及游标按账号隔离，切换账号不得混用|
|新增删除队列|本地删除时保存 UUID、基础版本和完整删除请求，确认云端删除后再清理队列|

首次启用云端时由用户确认上传范围。之后先处理本地待上传队列，再按页拉取；应用远端内容时避免覆盖仍有未提交修改的本地稿，将其保留为待解决冲突。关闭云服务后停止相关请求，基础本地编辑、关键词搜索、设备端语义检索和 Markdown 导出继续可用。
