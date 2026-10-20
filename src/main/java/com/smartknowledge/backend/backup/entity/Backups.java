package com.smartknowledge.backend.backup.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/** backups：字段说明见数据库迁移及中文数据字典。 */
@TableName("backups")
public class Backups {
    /** 备份 UUID */
    @TableId(type = IdType.INPUT)
    public String id;
    /** 所属用户 */
    public String userId;
    /** 备份名称 */
    public String name;
    /** 备份状态 ready */
    public String status;
    /** 完整笔记数量 */
    public Integer noteCount;
    /** 快照字节总数 */
    public Long sizeBytes;
    /** 有序快照清单 SHA256 */
    public String checksum;
    /** 创建时间 */
    public LocalDateTime createdAt;
    /** 过期时间 */
    public LocalDateTime expiresAt;
    /** 完成时间 */
    public LocalDateTime completedAt;
}
