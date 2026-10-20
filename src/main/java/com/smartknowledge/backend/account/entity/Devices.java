package com.smartknowledge.backend.account.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/** devices：字段说明见数据库迁移及中文数据字典。 */
@TableName("devices")
public class Devices {
    /** 会话设备 UUID */
    @TableId(type = IdType.INPUT)
    public String id;
    /** 所属用户 */
    public String userId;
    /** 客户端设备 UUID */
    public String deviceUuid;
    /** 设备名称 */
    public String name;
    /** 设备平台 */
    public String platform;
    /** 访问令牌 SHA256 */
    public String accessTokenHash;
    /** 访问令牌到期时间 */
    public LocalDateTime accessExpiresAt;
    /** 刷新令牌 SHA256 */
    public String refreshTokenHash;
    /** 刷新令牌到期时间 */
    public LocalDateTime refreshExpiresAt;
    /** 撤销时间 */
    public LocalDateTime revokedAt;
    /** 最近登录或刷新时间 */
    public LocalDateTime lastSeenAt;
    /** 创建时间 */
    public LocalDateTime createdAt;
    /** 更新时间 */
    public LocalDateTime updatedAt;
}
