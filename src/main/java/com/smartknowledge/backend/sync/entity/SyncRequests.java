package com.smartknowledge.backend.sync.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/** sync_requests：字段说明见数据库迁移及中文数据字典。 */
@TableName("sync_requests")
public class SyncRequests {
    /** 记录 UUID */
    @TableId(type = IdType.INPUT)
    public String id;
    /** 所属用户 */
    public String userId;
    /** 提交设备会话 UUID */
    public String deviceId;
    /** 客户端幂等请求 UUID */
    public String requestId;
    /** 规范化请求 SHA256 */
    public String payloadHash;
    /** 处理状态 */
    public String status;
    /** 可重放的完整结果 */
    public String responseJson;
    /** 创建时间 */
    public LocalDateTime createdAt;
    /** 完成时间 */
    public LocalDateTime completedAt;
}
