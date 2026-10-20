package com.smartknowledge.backend.note.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/** notes：字段说明见数据库迁移及中文数据字典。 */
@TableName("notes")
public class Notes {
    /** 客户端可生成的笔记 UUID */
    @TableId(type = IdType.INPUT)
    public String id;
    /** 所属用户 */
    public String userId;
    /** 笔记标题 */
    public String title;
    /** Markdown 正文 */
    public String content;
    /** 服务端版本号 */
    public Long version;
    /** 删除墓碑 */
    public Boolean deleted;
    /** 恢复前原笔记 UUID，仅作来源信息 */
    public String sourceNoteId;
    /** 当前完整快照 UTF8 字节数 */
    public Long snapshotBytes;
    /** 创建时间 */
    public LocalDateTime createdAt;
    /** 更新时间 */
    public LocalDateTime updatedAt;
    /** 删除时间 */
    public LocalDateTime deletedAt;
}
