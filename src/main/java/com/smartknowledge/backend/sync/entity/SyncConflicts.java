package com.smartknowledge.backend.sync.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/** sync_conflicts：字段说明见数据库迁移及中文数据字典。 */
@TableName("sync_conflicts")
public class SyncConflicts {
    /** 冲突 UUID */
    @TableId(type = IdType.INPUT)
    public String id;
    /** 所属用户 */
    public String userId;
    /** 笔记 UUID */
    public String noteId;
    /** 提交设备会话 UUID */
    public String deviceId;
    /** 提交方基础版本 */
    public Long baseVersion;
    /** 冲突发生时云端版本 */
    public Long serverVersion;
    /** 提交方内容及标签快照 */
    public String submittedSnapshot;
    /** 云端内容及标签快照 */
    public String serverSnapshot;
    /** open 或 resolved */
    public String status;
    /** server submitted merged */
    public String resolution;
    /** 创建时间 */
    public LocalDateTime createdAt;
    /** 解决时间 */
    public LocalDateTime resolvedAt;
}
