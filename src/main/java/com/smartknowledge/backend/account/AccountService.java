package com.smartknowledge.backend.account;

import com.smartknowledge.backend.common.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.smartknowledge.backend.common.SqlMapper.*;

@Service
public class AccountService {
    public static final String CONSENT_VERSION = "2026-10-v1";
    private final SqlMapper db;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final int accessMinutes;
    private final int refreshDays;
    private final SecureRandom random = new SecureRandom();
    public AccountService(SqlMapper db, PasswordEncoder encoder, Clock clock,
                          @Value("${app.access-ttl-minutes}") int accessMinutes, @Value("${app.refresh-ttl-days}") int refreshDays) {
        this.db=db; this.encoder=encoder; this.clock=clock; this.accessMinutes=accessMinutes; this.refreshDays=refreshDays;
    }
    @Transactional
    public Object register(AccountRequests.Register input) {
        if(Json.bytes(input.password()) > 72) throw BusinessException.invalid("密码 UTF-8 编码不能超过 72 字节");
        String id=UUID.randomUUID().toString(); Instant now=clock.instant();
        db.update("INSERT INTO users(id,username,password_hash,nickname,created_at,updated_at) VALUES(?,?,?,?,?,?)",id,input.username().toLowerCase(Locale.ROOT),encoder.encode(input.password()),input.nickname().trim(),now,now);
        db.update("INSERT INTO user_preferences(id,user_id,created_at,updated_at) VALUES(?,?,?,?)",UUID.randomUUID().toString(),id,now,now);
        return Map.of("id",id,"username",input.username().toLowerCase(Locale.ROOT));
    }
    @Transactional
    public Object login(AccountRequests.Login input) {
        if(Json.bytes(input.password())>72) throw unauthorized();
        String deviceUuid=uuid(input.deviceUuid());
        var user=db.one("SELECT * FROM users WHERE username=?",input.username().toLowerCase(Locale.ROOT));
        if(user==null || !encoder.matches(input.password(),(String)user.get("passwordHash")) || !"active".equals(user.get("status"))) throw unauthorized();
        String userId=(String)user.get("id"); db.lock(userId);
        var device=db.one("SELECT id FROM devices WHERE user_id=? AND device_uuid=?",userId,deviceUuid);
        String id=device==null?UUID.randomUUID().toString():(String)device.get("id");
        String access=token(), refresh=token(); Instant now=clock.instant();
        Instant ae=now.plusSeconds(accessMinutes*60L), re=now.plusSeconds(refreshDays*86400L);
        if(device==null) db.update("INSERT INTO devices(id,user_id,device_uuid,name,platform,access_token_hash,access_expires_at,refresh_token_hash,refresh_expires_at,last_seen_at,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",id,userId,deviceUuid,input.deviceName(),input.platform(),Json.hash(access),ae,Json.hash(refresh),re,now,now,now);
        else db.update("UPDATE devices SET name=?,platform=?,access_token_hash=?,access_expires_at=?,refresh_token_hash=?,refresh_expires_at=?,revoked_at=NULL,last_seen_at=?,updated_at=? WHERE user_id=? AND id=?",input.deviceName(),input.platform(),Json.hash(access),ae,Json.hash(refresh),re,now,now,userId,id);
        return tokens(access,refresh,ae,re,id,userId);
    }
    @Transactional
    public Object refresh(String refresh) {
        var candidate=db.one("SELECT user_id FROM devices WHERE refresh_token_hash=?",Json.hash(refresh));
        if(candidate==null) throw unauthorized(); String user=(String)candidate.get("userId"); db.lock(user);
        Instant now=clock.instant();
        var device=db.one("SELECT d.* FROM devices d JOIN users u ON u.id=d.user_id WHERE d.user_id=? AND d.refresh_token_hash=? AND d.revoked_at IS NULL AND d.refresh_expires_at>? AND u.status='active'",user,Json.hash(refresh),now);
        if(device==null) throw unauthorized();
        String access=token(), next=token(); Instant ae=now.plusSeconds(accessMinutes*60L), re=now.plusSeconds(refreshDays*86400L);
        db.update("UPDATE devices SET access_token_hash=?,access_expires_at=?,refresh_token_hash=?,refresh_expires_at=?,last_seen_at=?,updated_at=? WHERE user_id=? AND id=?",Json.hash(access),ae,Json.hash(next),re,now,now,user,device.get("id"));
        return tokens(access,next,ae,re,(String)device.get("id"),user);
    }
    public SessionUser authenticate(String access) {
        if(access==null || access.length()>200) return null;
        var row=db.one("SELECT d.id,d.user_id,d.device_uuid FROM devices d JOIN users u ON u.id=d.user_id WHERE d.access_token_hash=? AND d.revoked_at IS NULL AND d.access_expires_at>? AND u.status='active'",Json.hash(access),clock.instant());
        return row==null?null:new SessionUser((String)row.get("userId"),(String)row.get("id"),(String)row.get("deviceUuid"));
    }
    public Object me(SessionUser user) {
        var profile=db.required("SELECT id,username,nickname,status,created_at,updated_at FROM users WHERE id=?",user.userId());
        profile.put("preferences",db.required("SELECT * FROM user_preferences WHERE user_id=?",user.userId()));
        profile.put("consent",Map.of("version",CONSENT_VERSION,"sync","上传笔记原文与标签，用于跨设备保存和同步。","backup","在服务端保存笔记快照，用于备份与恢复。","ai","发送本次提交的标题、正文和标签给配置的 AI 提供方，仅用于生成标签。"));
        return profile;
    }
    @Transactional
    public Object profile(SessionUser user,AccountRequests.Profile input) {
        db.lock(user.userId()); db.update("UPDATE users SET nickname=?,updated_at=? WHERE id=?",input.nickname().trim(),clock.instant(),user.userId()); return me(user);
    }
    @Transactional
    public Object preferences(SessionUser user,AccountRequests.Preferences input) {
        if(!CONSENT_VERSION.equals(input.consentVersion())) throw BusinessException.invalid("请阅读并接受当前版本云服务说明");
        db.lock(user.userId()); Instant now=clock.instant();
        db.update("UPDATE user_preferences SET sync_enabled=?,sync_consent_at=IF(?, ?,sync_consent_at),sync_consent_version=IF(?,?,sync_consent_version),backup_enabled=?,backup_consent_at=IF(?,?,backup_consent_at),backup_consent_version=IF(?,?,backup_consent_version),ai_enabled=?,ai_consent_at=IF(?,?,ai_consent_at),ai_consent_version=IF(?,?,ai_consent_version),updated_at=? WHERE user_id=?",
            input.syncEnabled(),input.syncEnabled(),now,input.syncEnabled(),CONSENT_VERSION,input.backupEnabled(),input.backupEnabled(),now,input.backupEnabled(),CONSENT_VERSION,input.aiEnabled(),input.aiEnabled(),now,input.aiEnabled(),CONSENT_VERSION,now,user.userId());
        return me(user);
    }
    public Object devices(SessionUser user) { return db.list("SELECT id,device_uuid,name,platform,revoked_at,last_seen_at,created_at FROM devices WHERE user_id=? ORDER BY created_at,id",user.userId()); }
    @Transactional
    public void revoke(SessionUser user,String id) {
        id=uuid(id); db.lock(user.userId()); db.required("SELECT id FROM devices WHERE user_id=? AND id=?",user.userId(),id);
        db.update("UPDATE devices SET revoked_at=?,updated_at=? WHERE user_id=? AND id=?",clock.instant(),clock.instant(),user.userId(),id);
    }
    private String token() { byte[] bytes=new byte[32]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private Object tokens(String access,String refresh,Instant ae,Instant re,String device,String user) { return Map.of("accessToken",access,"refreshToken",refresh,"tokenType","Bearer","accessExpiresAt",ae,"refreshExpiresAt",re,"deviceId",device,"userId",user); }
    private BusinessException unauthorized() { return new BusinessException(401,"UNAUTHORIZED","账号、密码或令牌无效"); }
}
