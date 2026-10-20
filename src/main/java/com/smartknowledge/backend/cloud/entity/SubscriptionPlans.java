package com.smartknowledge.backend.cloud.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/** subscription_plans：字段说明见数据库迁移及中文数据字典。 */
@TableName("subscription_plans")
public class SubscriptionPlans {
    /** 套餐 UUID */
    @TableId(type = IdType.INPUT)
    public String id;
    /** 套餐代码 */
    public String code;
    /** 套餐名称 */
    public String name;
    /** 价格，人民币分 */
    public Integer priceCents;
    /** 有效天数 */
    public Integer durationDays;
    /** 云端存储限额 */
    public Long storageBytes;
    /** 历史保留天数 */
    public Integer historyDays;
    /** UTC 自然月 AI 次数 */
    public Integer aiMonthlyLimit;
    /** 是否可开通 */
    public Boolean enabled;
    /** 创建时间 */
    public LocalDateTime createdAt;
    /** 更新时间 */
    public LocalDateTime updatedAt;
}
