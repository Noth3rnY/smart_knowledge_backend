package com.smartknowledge.backend.cloud.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/** user_subscriptions：字段说明见数据库迁移及中文数据字典。 */
@TableName("user_subscriptions")
public class UserSubscriptions {
    /** 订阅 UUID */
    @TableId(type = IdType.INPUT)
    public String id;
    /** 所属用户 */
    public String userId;
    /** 套餐 UUID */
    public String planId;
    /** active 状态，过期按时间判断 */
    public String status;
    /** 生效时间 */
    public LocalDateTime startsAt;
    /** 到期时间 */
    public LocalDateTime expiresAt;
    /** 开通来源 demo */
    public String source;
    /** 开通幂等请求 UUID */
    public String requestId;
    /** 创建时间 */
    public LocalDateTime createdAt;
    /** 更新时间 */
    public LocalDateTime updatedAt;
}
