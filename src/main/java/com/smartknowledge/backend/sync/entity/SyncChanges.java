package com.smartknowledge.backend.sync.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/** sync_changes：字段说明见数据库迁移及中文数据字典。 */
@TableName("sync_changes")
public class SyncChanges {
    /** 变更 UUID */
    @TableId(type = IdType.INPUT)
    public String id;
    /** 所属用户 */
    public String userId;
    /** 用户内递增同步序号 */
    public Long revision;
    /** 笔记 UUID */
    public String noteId;
    /** upsert 或 delete */
    public String operation;
    /** 含标签与删除状态的完整快照 */
    public String snapshot;
    /** 来源设备会话 UUID */
    public String deviceId;
    /** 提交时间 */
    public LocalDateTime createdAt;
}
