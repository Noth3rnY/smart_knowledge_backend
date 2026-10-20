package com.smartknowledge.backend.sync;

import com.smartknowledge.backend.account.SessionUser;
import com.smartknowledge.backend.cloud.EntitlementService;
import com.smartknowledge.backend.common.*;
import com.smartknowledge.backend.note.NoteService;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.smartknowledge.backend.common.SqlMapper.*;

@Service
public class SyncService {
    private final SqlMapper db;
    private final NoteService notes;
    private final EntitlementService rights;
    private final Json json;
    private final Clock clock;
    public SyncService(SqlMapper db,NoteService notes,EntitlementService rights,Json json,Clock clock) { this.db=db; this.notes=notes; this.rights=rights; this.json=json; this.clock=clock; }
    @Transactional public Object push(SessionUser user,SyncRequests.Push input) {
        if(!uuid(input.deviceUuid()).equals(user.deviceUuid())) throw BusinessException.invalid("提交设备与当前会话不一致");
        String request=uuid(input.requestId()); String hash=Json.hash(json.write(input)); db.lock(user.userId());
        var previous=db.one("SELECT * FROM sync_requests WHERE user_id=? AND request_id=?",user.userId(),request);
        if(previous!=null) {
            if(!hash.equals(previous.get("payloadHash"))) throw new BusinessException(409,"IDEMPOTENCY_MISMATCH","请求号已用于其他内容");
            return json.read((String)previous.get("responseJson"));
        }
        var plan=rights.require(user.userId(),"sync"); Set<String> ids=new HashSet<>();
        for(var change:input.changes()) { notes.validate(change.note()); if(!ids.add(uuid(change.note().id()))) throw BusinessException.invalid("同一批次不能多次修改同一笔记"); }
        List<Object> results=new ArrayList<>();
        for(var change:input.changes()) {
            var note=change.note(); String id=uuid(note.id());
            var old=db.one("SELECT * FROM notes WHERE user_id=? AND id=?",user.userId(),id);
            if(old!=null && number(old,"version")!=note.baseVersion()) {
                String conflict=UUID.randomUUID().toString();
                Map<String,Object> submitted=new LinkedHashMap<>(); submitted.put("id",id); submitted.put("title",note.title()); submitted.put("content",note.content()); submitted.put("tags",note.tags()); submitted.put("deleted","delete".equals(change.operation()));
                db.update("INSERT INTO sync_conflicts(id,user_id,note_id,device_id,base_version,server_version,submitted_snapshot,server_snapshot,created_at) VALUES(?,?,?,?,?,?,?,?,?)",conflict,user.userId(),id,user.deviceId(),note.baseVersion(),old.get("version"),json.write(submitted),json.write(notes.snapshot(old)),clock.instant());
                results.add(Map.of("id",id,"status","conflict","conflictId",conflict));
            } else {
                var saved=notes.mutate(user,note,"delete".equals(change.operation()),"sync",null,plan);
                results.add(Map.of("id",id,"status","applied","note",saved));
            }
        }
        Object response=Map.of("results",results,"revision",db.count("SELECT sync_revision FROM user_preferences WHERE user_id=?",user.userId()));
        db.update("INSERT INTO sync_requests(id,user_id,device_id,request_id,payload_hash,status,response_json,created_at,completed_at) VALUES(?,?,?,?,?,'completed',?,?,?)",UUID.randomUUID().toString(),user.userId(),user.deviceId(),request,hash,json.write(response),clock.instant(),clock.instant());
        return response;
    }
    public Object pull(String user,long after,int size) {
        if(after<0 || size<1 || size>100) throw BusinessException.invalid("同步游标或分页大小不正确");
        var rows=db.list("SELECT revision,operation,snapshot,created_at FROM sync_changes WHERE user_id=? AND revision>? ORDER BY revision LIMIT ?",user,after,size+1);
        boolean more=rows.size()>size; var page=rows.stream().limit(size).map(r->{ var value=new LinkedHashMap<>(r); value.put("note",json.read((String)value.remove("snapshot"))); return value; }).toList();
        long next=page.isEmpty()?after:number(page.get(page.size()-1),"revision");
        return Map.of("items",page,"nextCursor",next,"hasMore",more);
    }
    public Object conflicts(String user,int page,int size) {
        if(page<1 || size<1 || size>100) throw BusinessException.invalid("分页参数不正确");
        var rows=db.list("SELECT * FROM sync_conflicts WHERE user_id=? AND status='open' ORDER BY created_at,id LIMIT ? OFFSET ?",user,size,(page-1L)*size);
        for(var row:rows) { row.put("submittedSnapshot",json.read((String)row.get("submittedSnapshot"))); row.put("serverSnapshot",json.read((String)row.get("serverSnapshot"))); }
        return new Api.Page<>(rows,db.count("SELECT COUNT(*) FROM sync_conflicts WHERE user_id=? AND status='open'",user),page,size);
    }
    @Transactional public Object resolve(SessionUser user,String id,SyncRequests.Resolve input) {
        id=uuid(id);
        db.lock(user.userId()); var plan=rights.require(user.userId(),"sync");
        var conflict=db.required("SELECT * FROM sync_conflicts WHERE user_id=? AND id=?",user.userId(),uuid(id));
        if(!"open".equals(conflict.get("status"))) throw new BusinessException(409,"CONFLICT_RESOLVED","该冲突已经解决");
        String noteId=(String)conflict.get("noteId"); var latest=notes.get(user.userId(),noteId);
        if(number(latest,"version")!=input.serverVersion()) throw new BusinessException(409,"VERSION_CONFLICT","云端再次发生修改，请重新选择",latest);
        Object result=latest;
        if(!"server".equals(input.choice())) {
            Map<String,Object> submitted=json.map((String)conflict.get("submittedSnapshot"));
            var write="merged".equals(input.choice())?input.merged():notes.fromSnapshot(submitted,noteId,input.serverVersion());
            if(write==null || !uuid(write.id()).equals(noteId) || write.baseVersion()==null || !write.baseVersion().equals(input.serverVersion())) throw BusinessException.invalid("合并笔记必须携带当前笔记 ID 和云端版本");
            result=notes.mutate(user,write,"submitted".equals(input.choice()) && flag(submitted,"deleted"),"conflict",null,plan);
        }
        db.update("UPDATE sync_conflicts SET status='resolved',resolution=?,resolved_at=? WHERE user_id=? AND id=?",input.choice(),clock.instant(),user.userId(),id);
        return result;
    }
}
