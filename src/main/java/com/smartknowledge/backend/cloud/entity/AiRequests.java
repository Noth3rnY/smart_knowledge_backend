package com.smartknowledge.backend.cloud.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/** ai_requests：字段说明见数据库迁移及中文数据字典。 */
@TableName("ai_requests")
public class AiRequests {
    /** AI 请求记录 UUID */
    @TableId(type = IdType.INPUT)
    public String id;
    /** 所属用户 */
    public String userId;
    /** 客户端幂等请求 UUID */
    public String requestId;
    /** 请求摘要，不保存正文 */
    public String payloadHash;
    /** mock 或 real */
    public String provider;
    /** 模型或模拟规则版本 */
    public String model;
    /** pending succeeded failed */
    public String status;
    /** 成功建议标签 JSON */
    public String tagsJson;
    /** 脱敏失败代码 */
    public String errorCode;
    /** 提供方返回的输入 token 数 */
    public Integer inputTokens;
    /** 提供方返回的输出 token 数 */
    public Integer outputTokens;
    /** 额度预占时间与月归属 */
    public LocalDateTime reservedAt;
    /** 完成时间 */
    public LocalDateTime completedAt;
    /** 预占超时回收时间 */
    public LocalDateTime expiresAt;
}
