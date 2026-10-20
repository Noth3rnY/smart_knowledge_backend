package com.smartknowledge.backend.cloud;

import com.smartknowledge.backend.common.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.smartknowledge.backend.common.SqlMapper.*;

@Service
public class EntitlementService {
    private final SqlMapper db;
    private final Clock clock;
    public EntitlementService(SqlMapper db,Clock clock) { this.db=db; this.clock=clock; }
    public Object plans() { return db.list("SELECT * FROM subscription_plans WHERE enabled=TRUE ORDER BY price_cents,id"); }
    public Map<String,Object> active(String user) {
        return db.one("SELECT s.id AS subscription_id,s.expires_at,p.* FROM user_subscriptions s JOIN subscription_plans p ON p.id=s.plan_id WHERE s.user_id=? AND s.status='active' AND s.starts_at<=? AND s.expires_at>? ORDER BY s.expires_at DESC LIMIT 1",user,clock.instant(),clock.instant());
    }
    public Map<String,Object> require(String user,String capability) {
        var prefs=db.required("SELECT * FROM user_preferences WHERE user_id=?",user);
        if(!flag(prefs,capability+"Enabled")) throw new BusinessException(403,"CONSENT_REQUIRED","请先明确授权对应云服务");
        var plan=active(user);
        if(plan==null) throw new BusinessException(403,"SUBSCRIPTION_REQUIRED","此操作需要有效订阅");
        return plan;
    }
    public long usage(String user) {
        return db.count("SELECT COALESCE(SUM(snapshot_bytes),0) FROM notes WHERE user_id=?",user)
            + db.count("SELECT COALESCE(SUM(snapshot_bytes),0) FROM note_versions WHERE user_id=? AND expires_at>?",user,clock.instant())
            + db.count("SELECT COALESCE(SUM(i.snapshot_bytes),0) FROM backup_items i JOIN backups b ON b.id=i.backup_id AND b.user_id=i.user_id WHERE i.user_id=? AND b.expires_at>?",user,clock.instant());
    }
    public void checkStorage(String user,long delta,Map<String,Object> plan) {
        if(delta>0 && usage(user)+delta>number(plan,"storageBytes")) throw new BusinessException(413,"STORAGE_LIMIT","云端存储空间不足");
    }
    public Object subscription(String user) {
        var plan=active(user); Instant month=LocalDate.now(clock).withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        long used=db.count("SELECT COUNT(*) FROM ai_requests WHERE user_id=? AND reserved_at>=? AND status IN ('pending','succeeded') AND (status='succeeded' OR expires_at>?)",user,month,clock.instant());
        Map<String,Object> result=new LinkedHashMap<>(); result.put("active",plan!=null); result.put("plan",plan); result.put("storageUsedBytes",usage(user)); result.put("aiUsedThisMonth",used); result.put("monthStart",month); return result;
    }
    @Transactional
    public Object activate(String user,String request) {
        request=uuid(request); db.lock(user);
        var previous=db.one("SELECT * FROM user_subscriptions WHERE user_id=? AND request_id=?",user,request); if(previous!=null) return previous;
        var existing=active(user); if(existing!=null) throw new BusinessException(409,"ALREADY_SUBSCRIBED","当前已有有效订阅，请使用原请求号重放");
        var plan=db.required("SELECT * FROM subscription_plans WHERE code='annual' AND enabled=TRUE");
        Instant now=clock.instant(); String id=UUID.randomUUID().toString();
        db.update("INSERT INTO user_subscriptions(id,user_id,plan_id,status,starts_at,expires_at,source,request_id,created_at,updated_at) VALUES(?,?,?,'active',?,?,'demo',?,?,?)",id,user,plan.get("id"),now,now.plusSeconds(number(plan,"durationDays")*86400),request,now,now);
        return db.required("SELECT * FROM user_subscriptions WHERE user_id=? AND id=?",user,id);
    }
}
