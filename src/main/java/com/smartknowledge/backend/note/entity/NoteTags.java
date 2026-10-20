package com.smartknowledge.backend.note.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/** note_tags：字段说明见数据库迁移及中文数据字典。 */
@TableName("note_tags")
public class NoteTags {
    /** 关联 UUID */
    @TableId(type = IdType.INPUT)
    public String id;
    /** 所属用户 */
    public String userId;
    /** 笔记 UUID */
    public String noteId;
    /** 标签 UUID */
    public String tagId;
    /** 该笔记上的标签来源 manual 或 llm */
    public String source;
    /** 创建时间 */
    public LocalDateTime createdAt;
    /** 更新时间 */
    public LocalDateTime updatedAt;
}
