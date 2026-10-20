package com.smartknowledge.backend.backup.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/** backup_items：字段说明见数据库迁移及中文数据字典。 */
@TableName("backup_items")
public class BackupItems {
    /** 备份项 UUID */
    @TableId(type = IdType.INPUT)
    public String id;
    /** 所属用户 */
    public String userId;
    /** 所属备份 UUID */
    public String backupId;
    /** 原笔记 UUID，仅作快照来源 */
    public String noteId;
    /** 完整笔记及标签 JSON */
    public String snapshot;
    /** UTF8 字节数 */
    public Long snapshotBytes;
    /** 快照 SHA256 */
    public String checksum;
    /** 创建时间 */
    public LocalDateTime createdAt;
}
