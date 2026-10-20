package com.smartknowledge.backend.account.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/** user_preferences：字段说明见数据库迁移及中文数据字典。 */
@TableName("user_preferences")
public class UserPreferences {
    /** 设置 UUID */
    @TableId(type = IdType.INPUT)
    public String id;
    /** 所属用户 */
    public String userId;
    /** 同步授权 */
    public Boolean syncEnabled;
    /** 同步最近授权时间 */
    public LocalDateTime syncConsentAt;
    /** 同步授权说明版本 */
    public String syncConsentVersion;
    /** 备份授权 */
    public Boolean backupEnabled;
    /** 备份最近授权时间 */
    public LocalDateTime backupConsentAt;
    /** 备份授权说明版本 */
    public String backupConsentVersion;
    /** AI 授权 */
    public Boolean aiEnabled;
    /** AI 最近授权时间 */
    public LocalDateTime aiConsentAt;
    /** AI 授权说明版本 */
    public String aiConsentVersion;
    /** 用户内已提交同步序号 */
    public Long syncRevision;
    /** 创建时间 */
    public LocalDateTime createdAt;
    /** 更新时间 */
    public LocalDateTime updatedAt;
}
