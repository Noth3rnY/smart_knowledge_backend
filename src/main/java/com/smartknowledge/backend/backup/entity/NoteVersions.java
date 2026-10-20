package com.smartknowledge.backend.backup.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/** note_versions：字段说明见数据库迁移及中文数据字典。 */
@TableName("note_versions")
public class NoteVersions {
    /** 历史 UUID */
    @TableId(type = IdType.INPUT)
    public String id;
    /** 所属用户 */
    public String userId;
    /** 原笔记 UUID */
    public String noteId;
    /** 快照版本号 */
    public Long version;
    /** 含标题正文标签的完整快照 */
    public String snapshot;
    /** UTF8 字节数 */
    public Long snapshotBytes;
    /** api sync restore conflict */
    public String source;
    /** 创建时间 */
    public LocalDateTime createdAt;
    /** 保留截止时间 */
    public LocalDateTime expiresAt;
}
