package com.smartknowledge.backend.note.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/** tags：字段说明见数据库迁移及中文数据字典。 */
@TableName("tags")
public class Tags {
    /** 标签 UUID */
    @TableId(type = IdType.INPUT)
    public String id;
    /** 所属用户 */
    public String userId;
    /** 去除首尾空白的标签名 */
    public String name;
    /** 首次建立来源 manual 或 llm */
    public String firstSource;
    /** 最近使用时间 */
    public LocalDateTime lastUsedAt;
    /** 创建时间 */
    public LocalDateTime createdAt;
    /** 更新时间 */
    public LocalDateTime updatedAt;
}
