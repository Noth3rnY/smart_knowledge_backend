package com.smartknowledge.backend.cloud;

import com.smartknowledge.backend.common.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.smartknowledge.backend.common.SqlMapper.*;

@Service
public class AiLedgerService {
    private final SqlMapper db;
    private final EntitlementService rights;
    private final Clock clock;
    private final Json json;
    private final int timeout;
    public AiLedgerService(SqlMapper db,EntitlementService rights,Clock clock,Json json,@Value("${app.ai.timeout-seconds}") int timeout) { this.db=db; this.rights=rights; this.clock=clock; this.json=json; this.timeout=timeout; }
    public record Reservation(String id,Map<String,Object> replay) {}
    @Transactional public Reservation reserve(String user,AiRequests.Tags input,String provider,String model) {
        db.lock(user); String request=uuid(input.requestId()), hash=Json.hash(json.write(input)); Instant now=clock.instant();
        db.update("UPDATE ai_requests SET status='failed',error_code='AI_TIMEOUT',completed_at=? WHERE user_id=? AND status='pending' AND expires_at<=?",now,user,now);
        var old=db.one("SELECT * FROM ai_requests WHERE user_id=? AND request_id=?",user,request);
        if(old!=null) {
            if(!hash.equals(old.get("payloadHash"))) throw new BusinessException(409,"IDEMPOTENCY_MISMATCH","请求号已用于其他内容");
            if("succeeded".equals(old.get("status"))) return new Reservation((String)old.get("id"),result(old));
            if("pending".equals(old.get("status"))) throw new BusinessException(409,"AI_PENDING","请求正在处理中，请使用相同请求号稍后查询");
            throw new BusinessException(502,(String)old.get("errorCode"),"此请求已失败且未扣额度；重试生成请使用新请求号");
        }
        var plan=rights.require(user,"ai"); Instant month=LocalDate.now(clock).withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        long used=db.count("SELECT COUNT(*) FROM ai_requests WHERE user_id=? AND reserved_at>=? AND status IN ('pending','succeeded')",user,month);
        if(used>=number(plan,"aiMonthlyLimit")) throw new BusinessException(429,"AI_QUOTA_EXCEEDED","本月 AI 标签额度已用完");
        String id=UUID.randomUUID().toString();
        db.update("INSERT INTO ai_requests(id,user_id,request_id,payload_hash,provider,model,status,reserved_at,expires_at) VALUES(?,?,?,?,?,?,'pending',?,?)",id,user,request,hash,provider,model,now,now.plusSeconds(Math.max(timeout,1)+60L));
        return new Reservation(id,null);
    }
    @Transactional public Map<String,Object> success(String user,String id,List<String> tags,int inputTokens,int outputTokens) {
        db.lock(user); Instant now=clock.instant();
        int changed=db.update("UPDATE ai_requests SET status='succeeded',tags_json=?,input_tokens=?,output_tokens=?,completed_at=? WHERE user_id=? AND id=? AND status='pending' AND expires_at>?",json.write(tags),inputTokens,outputTokens,now,user,id,now);
        if(changed!=1) throw new BusinessException(502,"AI_TIMEOUT","请求预占已超时，未扣额度");
        return result(db.required("SELECT * FROM ai_requests WHERE user_id=? AND id=?",user,id));
    }
    @Transactional public void failure(String user,String id,String code) {
        db.lock(user); db.update("UPDATE ai_requests SET status='failed',error_code=?,completed_at=? WHERE user_id=? AND id=? AND status='pending'",code,clock.instant(),user,id);
    }
    private Map<String,Object> result(Map<String,Object> row) { return Map.of("requestId",row.get("requestId"),"tags",json.read((String)row.get("tagsJson")),"provider",row.get("provider"),"model",row.get("model"),"simulated","mock".equals(row.get("provider"))); }
}
