# 数据字典与五人分工

全部业务表使用 InnoDB、utf8mb4_0900_as_cs；时间均为 UTC。下列字段和约束直接依据 V1 迁移生成。

未标注 DEFAULT 的字段没有数据库默认值：NOT NULL 字段由业务写入，NULL 字段默认可为空。所有 UUID 主键由应用或客户端生成；来源字段不额外设置无意义的占位默认值。

|成员|模块|表数|字段总数|
|---|---|---:|---:|
|成员1|account|3|34|
|成员2|note|3|25|
|成员3|sync|3|29|
|成员4|backup|3|27|
|成员5|cloud|3|35|

## users（7 字段）

|字段|类型|空值 / 默认值|说明|
|---|---|---|---|
|`id`|CHAR(36)|NOT NULL|用户 UUID|
|`username`|VARCHAR(64)|NOT NULL|规范化小写账号|
|`password_hash`|VARCHAR(100)|NOT NULL|BCrypt 密码摘要|
|`nickname`|VARCHAR(100)|NOT NULL|昵称|
|`status`|VARCHAR(20)|NOT NULL DEFAULT 'active'|账号状态|
|`created_at`|DATETIME(6)|NOT NULL|创建时间|
|`updated_at`|DATETIME(6)|NOT NULL|更新时间|

约束与索引：

```sql
PRIMARY KEY(id), UNIQUE KEY uk_username(username)
```

## user_preferences（14 字段）

|字段|类型|空值 / 默认值|说明|
|---|---|---|---|
|`id`|CHAR(36)|NOT NULL|设置 UUID|
|`user_id`|CHAR(36)|NOT NULL|所属用户|
|`sync_enabled`|BOOLEAN|NOT NULL DEFAULT FALSE|同步授权|
|`sync_consent_at`|DATETIME(6)|NULL|同步最近授权时间|
|`sync_consent_version`|VARCHAR(30)|NULL|同步授权说明版本|
|`backup_enabled`|BOOLEAN|NOT NULL DEFAULT FALSE|备份授权|
|`backup_consent_at`|DATETIME(6)|NULL|备份最近授权时间|
|`backup_consent_version`|VARCHAR(30)|NULL|备份授权说明版本|
|`ai_enabled`|BOOLEAN|NOT NULL DEFAULT FALSE|AI 授权|
|`ai_consent_at`|DATETIME(6)|NULL|AI 最近授权时间|
|`ai_consent_version`|VARCHAR(30)|NULL|AI 授权说明版本|
|`sync_revision`|BIGINT|NOT NULL DEFAULT 0|用户内已提交同步序号|
|`created_at`|DATETIME(6)|NOT NULL|创建时间|
|`updated_at`|DATETIME(6)|NOT NULL|更新时间|

约束与索引：

```sql
PRIMARY KEY(id), UNIQUE KEY uk_preferences_user(user_id), FOREIGN KEY(user_id) REFERENCES users(id)
```

## devices（13 字段）

|字段|类型|空值 / 默认值|说明|
|---|---|---|---|
|`id`|CHAR(36)|NOT NULL|会话设备 UUID|
|`user_id`|CHAR(36)|NOT NULL|所属用户|
|`device_uuid`|CHAR(36)|NOT NULL|客户端设备 UUID|
|`name`|VARCHAR(100)|NOT NULL|设备名称|
|`platform`|VARCHAR(30)|NOT NULL|设备平台|
|`access_token_hash`|CHAR(64)|NOT NULL|访问令牌 SHA256|
|`access_expires_at`|DATETIME(6)|NOT NULL|访问令牌到期时间|
|`refresh_token_hash`|CHAR(64)|NOT NULL|刷新令牌 SHA256|
|`refresh_expires_at`|DATETIME(6)|NOT NULL|刷新令牌到期时间|
|`revoked_at`|DATETIME(6)|NULL|撤销时间|
|`last_seen_at`|DATETIME(6)|NOT NULL|最近登录或刷新时间|
|`created_at`|DATETIME(6)|NOT NULL|创建时间|
|`updated_at`|DATETIME(6)|NOT NULL|更新时间|

约束与索引：

```sql
PRIMARY KEY(id), UNIQUE KEY uk_device(user_id,device_uuid), UNIQUE KEY uk_access(access_token_hash), UNIQUE KEY uk_refresh(refresh_token_hash), UNIQUE KEY uk_device_owner(user_id,id), FOREIGN KEY(user_id) REFERENCES users(id)
```

## notes（11 字段）

|字段|类型|空值 / 默认值|说明|
|---|---|---|---|
|`id`|CHAR(36)|NOT NULL|客户端可生成的笔记 UUID|
|`user_id`|CHAR(36)|NOT NULL|所属用户|
|`title`|VARCHAR(200)|NOT NULL|笔记标题|
|`content`|MEDIUMTEXT|NOT NULL|Markdown 正文|
|`version`|BIGINT|NOT NULL|服务端版本号|
|`deleted`|BOOLEAN|NOT NULL DEFAULT FALSE|删除墓碑|
|`source_note_id`|CHAR(36)|NULL|恢复前原笔记 UUID，仅作来源信息|
|`snapshot_bytes`|BIGINT|NOT NULL|当前完整快照 UTF8 字节数|
|`created_at`|DATETIME(6)|NOT NULL|创建时间|
|`updated_at`|DATETIME(6)|NOT NULL|更新时间|
|`deleted_at`|DATETIME(6)|NULL|删除时间|

约束与索引：

```sql
PRIMARY KEY(id), UNIQUE KEY uk_note_owner(user_id,id), KEY ix_notes_user_time(user_id,updated_at,id), FOREIGN KEY(user_id) REFERENCES users(id)
```

## tags（7 字段）

|字段|类型|空值 / 默认值|说明|
|---|---|---|---|
|`id`|CHAR(36)|NOT NULL|标签 UUID|
|`user_id`|CHAR(36)|NOT NULL|所属用户|
|`name`|VARCHAR(50)|NOT NULL|去除首尾空白的标签名|
|`first_source`|VARCHAR(10)|NOT NULL|首次建立来源 manual 或 llm|
|`last_used_at`|DATETIME(6)|NOT NULL|最近使用时间|
|`created_at`|DATETIME(6)|NOT NULL|创建时间|
|`updated_at`|DATETIME(6)|NOT NULL|更新时间|

约束与索引：

```sql
PRIMARY KEY(id), UNIQUE KEY uk_tag_name(user_id,name), UNIQUE KEY uk_tag_owner(user_id,id), FOREIGN KEY(user_id) REFERENCES users(id)
```

## note_tags（7 字段）

|字段|类型|空值 / 默认值|说明|
|---|---|---|---|
|`id`|CHAR(36)|NOT NULL|关联 UUID|
|`user_id`|CHAR(36)|NOT NULL|所属用户|
|`note_id`|CHAR(36)|NOT NULL|笔记 UUID|
|`tag_id`|CHAR(36)|NOT NULL|标签 UUID|
|`source`|VARCHAR(10)|NOT NULL|该笔记上的标签来源 manual 或 llm|
|`created_at`|DATETIME(6)|NOT NULL|创建时间|
|`updated_at`|DATETIME(6)|NOT NULL|更新时间|

约束与索引：

```sql
PRIMARY KEY(id), UNIQUE KEY uk_note_tag(user_id,note_id,tag_id), FOREIGN KEY(user_id,note_id) REFERENCES notes(user_id,id), FOREIGN KEY(user_id,tag_id) REFERENCES tags(user_id,id)
```

## sync_requests（9 字段）

|字段|类型|空值 / 默认值|说明|
|---|---|---|---|
|`id`|CHAR(36)|NOT NULL|记录 UUID|
|`user_id`|CHAR(36)|NOT NULL|所属用户|
|`device_id`|CHAR(36)|NOT NULL|提交设备会话 UUID|
|`request_id`|CHAR(36)|NOT NULL|客户端幂等请求 UUID|
|`payload_hash`|CHAR(64)|NOT NULL|规范化请求 SHA256|
|`status`|VARCHAR(20)|NOT NULL|处理状态|
|`response_json`|MEDIUMTEXT|NOT NULL|可重放的完整结果|
|`created_at`|DATETIME(6)|NOT NULL|创建时间|
|`completed_at`|DATETIME(6)|NOT NULL|完成时间|

约束与索引：

```sql
PRIMARY KEY(id), UNIQUE KEY uk_sync_request(user_id,request_id), FOREIGN KEY(user_id,device_id) REFERENCES devices(user_id,id)
```

## sync_changes（8 字段）

|字段|类型|空值 / 默认值|说明|
|---|---|---|---|
|`id`|CHAR(36)|NOT NULL|变更 UUID|
|`user_id`|CHAR(36)|NOT NULL|所属用户|
|`revision`|BIGINT|NOT NULL|用户内递增同步序号|
|`note_id`|CHAR(36)|NOT NULL|笔记 UUID|
|`operation`|VARCHAR(20)|NOT NULL|upsert 或 delete|
|`snapshot`|MEDIUMTEXT|NOT NULL|含标签与删除状态的完整快照|
|`device_id`|CHAR(36)|NOT NULL|来源设备会话 UUID|
|`created_at`|DATETIME(6)|NOT NULL|提交时间|

约束与索引：

```sql
PRIMARY KEY(id), UNIQUE KEY uk_change_revision(user_id,revision), FOREIGN KEY(user_id,note_id) REFERENCES notes(user_id,id), FOREIGN KEY(user_id,device_id) REFERENCES devices(user_id,id)
```

## sync_conflicts（12 字段）

|字段|类型|空值 / 默认值|说明|
|---|---|---|---|
|`id`|CHAR(36)|NOT NULL|冲突 UUID|
|`user_id`|CHAR(36)|NOT NULL|所属用户|
|`note_id`|CHAR(36)|NOT NULL|笔记 UUID|
|`device_id`|CHAR(36)|NOT NULL|提交设备会话 UUID|
|`base_version`|BIGINT|NOT NULL|提交方基础版本|
|`server_version`|BIGINT|NOT NULL|冲突发生时云端版本|
|`submitted_snapshot`|MEDIUMTEXT|NOT NULL|提交方内容及标签快照|
|`server_snapshot`|MEDIUMTEXT|NOT NULL|云端内容及标签快照|
|`status`|VARCHAR(20)|NOT NULL DEFAULT 'open'|open 或 resolved|
|`resolution`|VARCHAR(20)|NULL|server submitted merged|
|`created_at`|DATETIME(6)|NOT NULL|创建时间|
|`resolved_at`|DATETIME(6)|NULL|解决时间|

约束与索引：

```sql
PRIMARY KEY(id), KEY ix_conflicts_user_status(user_id,status,created_at), FOREIGN KEY(user_id,note_id) REFERENCES notes(user_id,id), FOREIGN KEY(user_id,device_id) REFERENCES devices(user_id,id)
```

## backups（10 字段）

|字段|类型|空值 / 默认值|说明|
|---|---|---|---|
|`id`|CHAR(36)|NOT NULL|备份 UUID|
|`user_id`|CHAR(36)|NOT NULL|所属用户|
|`name`|VARCHAR(100)|NOT NULL|备份名称|
|`status`|VARCHAR(20)|NOT NULL|备份状态 ready|
|`note_count`|INT|NOT NULL|完整笔记数量|
|`size_bytes`|BIGINT|NOT NULL|快照字节总数|
|`checksum`|CHAR(64)|NOT NULL|有序快照清单 SHA256|
|`created_at`|DATETIME(6)|NOT NULL|创建时间|
|`expires_at`|DATETIME(6)|NOT NULL|过期时间|
|`completed_at`|DATETIME(6)|NOT NULL|完成时间|

约束与索引：

```sql
PRIMARY KEY(id), UNIQUE KEY uk_backup_owner(user_id,id), KEY ix_backup_expiry(expires_at), FOREIGN KEY(user_id) REFERENCES users(id)
```

## backup_items（8 字段）

|字段|类型|空值 / 默认值|说明|
|---|---|---|---|
|`id`|CHAR(36)|NOT NULL|备份项 UUID|
|`user_id`|CHAR(36)|NOT NULL|所属用户|
|`backup_id`|CHAR(36)|NOT NULL|所属备份 UUID|
|`note_id`|CHAR(36)|NOT NULL|原笔记 UUID，仅作快照来源|
|`snapshot`|MEDIUMTEXT|NOT NULL|完整笔记及标签 JSON|
|`snapshot_bytes`|BIGINT|NOT NULL|UTF8 字节数|
|`checksum`|CHAR(64)|NOT NULL|快照 SHA256|
|`created_at`|DATETIME(6)|NOT NULL|创建时间|

约束与索引：

```sql
PRIMARY KEY(id), UNIQUE KEY uk_backup_note(user_id,backup_id,note_id), FOREIGN KEY(user_id,backup_id) REFERENCES backups(user_id,id) ON DELETE CASCADE
```

## note_versions（9 字段）

|字段|类型|空值 / 默认值|说明|
|---|---|---|---|
|`id`|CHAR(36)|NOT NULL|历史 UUID|
|`user_id`|CHAR(36)|NOT NULL|所属用户|
|`note_id`|CHAR(36)|NOT NULL|原笔记 UUID|
|`version`|BIGINT|NOT NULL|快照版本号|
|`snapshot`|MEDIUMTEXT|NOT NULL|含标题正文标签的完整快照|
|`snapshot_bytes`|BIGINT|NOT NULL|UTF8 字节数|
|`source`|VARCHAR(30)|NOT NULL|api sync restore conflict|
|`created_at`|DATETIME(6)|NOT NULL|创建时间|
|`expires_at`|DATETIME(6)|NOT NULL|保留截止时间|

约束与索引：

```sql
PRIMARY KEY(id), UNIQUE KEY uk_note_version(user_id,note_id,version), KEY ix_version_expiry(expires_at), FOREIGN KEY(user_id,note_id) REFERENCES notes(user_id,id)
```

## subscription_plans（11 字段）

|字段|类型|空值 / 默认值|说明|
|---|---|---|---|
|`id`|CHAR(36)|NOT NULL|套餐 UUID|
|`code`|VARCHAR(30)|NOT NULL|套餐代码|
|`name`|VARCHAR(100)|NOT NULL|套餐名称|
|`price_cents`|INT|NOT NULL|价格，人民币分|
|`duration_days`|INT|NOT NULL|有效天数|
|`storage_bytes`|BIGINT|NOT NULL|云端存储限额|
|`history_days`|INT|NOT NULL|历史保留天数|
|`ai_monthly_limit`|INT|NOT NULL|UTC 自然月 AI 次数|
|`enabled`|BOOLEAN|NOT NULL DEFAULT TRUE|是否可开通|
|`created_at`|DATETIME(6)|NOT NULL|创建时间|
|`updated_at`|DATETIME(6)|NOT NULL|更新时间|

约束与索引：

```sql
PRIMARY KEY(id), UNIQUE KEY uk_plan_code(code)
```

## user_subscriptions（10 字段）

|字段|类型|空值 / 默认值|说明|
|---|---|---|---|
|`id`|CHAR(36)|NOT NULL|订阅 UUID|
|`user_id`|CHAR(36)|NOT NULL|所属用户|
|`plan_id`|CHAR(36)|NOT NULL|套餐 UUID|
|`status`|VARCHAR(20)|NOT NULL|active 状态，过期按时间判断|
|`starts_at`|DATETIME(6)|NOT NULL|生效时间|
|`expires_at`|DATETIME(6)|NOT NULL|到期时间|
|`source`|VARCHAR(20)|NOT NULL|开通来源 demo|
|`request_id`|CHAR(36)|NOT NULL|开通幂等请求 UUID|
|`created_at`|DATETIME(6)|NOT NULL|创建时间|
|`updated_at`|DATETIME(6)|NOT NULL|更新时间|

约束与索引：

```sql
PRIMARY KEY(id), UNIQUE KEY uk_subscription_request(user_id,request_id), KEY ix_subscription_active(user_id,status,expires_at), FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(plan_id) REFERENCES subscription_plans(id)
```

## ai_requests（14 字段）

|字段|类型|空值 / 默认值|说明|
|---|---|---|---|
|`id`|CHAR(36)|NOT NULL|AI 请求记录 UUID|
|`user_id`|CHAR(36)|NOT NULL|所属用户|
|`request_id`|CHAR(36)|NOT NULL|客户端幂等请求 UUID|
|`payload_hash`|CHAR(64)|NOT NULL|请求摘要，不保存正文|
|`provider`|VARCHAR(20)|NOT NULL|mock 或 real|
|`model`|VARCHAR(100)|NOT NULL|模型或模拟规则版本|
|`status`|VARCHAR(20)|NOT NULL|pending succeeded failed|
|`tags_json`|TEXT|NULL|成功建议标签 JSON|
|`error_code`|VARCHAR(50)|NULL|脱敏失败代码|
|`input_tokens`|INT|NOT NULL DEFAULT 0|提供方返回的输入 token 数|
|`output_tokens`|INT|NOT NULL DEFAULT 0|提供方返回的输出 token 数|
|`reserved_at`|DATETIME(6)|NOT NULL|额度预占时间与月归属|
|`completed_at`|DATETIME(6)|NULL|完成时间|
|`expires_at`|DATETIME(6)|NOT NULL|预占超时回收时间|

约束与索引：

```sql
PRIMARY KEY(id), UNIQUE KEY uk_ai_request(user_id,request_id), KEY ix_ai_quota(user_id,reserved_at,status), FOREIGN KEY(user_id) REFERENCES users(id)
```
