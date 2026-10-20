package com.smartknowledge.backend.account.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/** users：字段说明见数据库迁移及中文数据字典。 */
@TableName("users")
public class Users {
    /** 用户 UUID */
    @TableId(type = IdType.INPUT)
    public String id;
    /** 规范化小写账号 */
    public String username;
    /** BCrypt 密码摘要 */
    public String passwordHash;
    /** 昵称 */
    public String nickname;
    /** 账号状态 */
    public String status;
    /** 创建时间 */
    public LocalDateTime createdAt;
    /** 更新时间 */
    public LocalDateTime updatedAt;
}
