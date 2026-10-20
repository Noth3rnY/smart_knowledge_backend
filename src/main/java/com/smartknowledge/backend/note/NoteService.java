package com.smartknowledge.backend.note;

import com.smartknowledge.backend.account.SessionUser;
import com.smartknowledge.backend.cloud.EntitlementService;
import com.smartknowledge.backend.common.*;
import com.smartknowledge.backend.note.entity.Tags;
import com.smartknowledge.backend.note.mapper.TagsMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.smartknowledge.backend.common.SqlMapper.*;

@Service
public class NoteService {
    private final SqlMapper db;
    private final Json json;
    private final Clock clock;
    private final EntitlementService rights;
    private final TagsMapper tags;
    private final long maxBytes;
    public NoteService(SqlMapper db,Json json,Clock clock,EntitlementService rights,TagsMapper tags,@Value("${app.body-max-bytes}") long maxBytes) {
        this.db=db; this.json=json; this.clock=clock; this.rights=rights; this.tags=tags; this.maxBytes=maxBytes;
    }
    public List<Map<String,Object>> tags(String user) {
        return tags.selectList(new QueryWrapper<Tags>().eq("user_id",user).orderByAsc("name")).stream().map(tag -> {
            Map<String,Object> value=new LinkedHashMap<>(); value.put("id",tag.id); value.put("name",tag.name); value.put("firstSource",tag.firstSource);
            value.put("lastUsedAt",tag.lastUsedAt.toInstant(ZoneOffset.UTC)); value.put("createdAt",tag.createdAt.toInstant(ZoneOffset.UTC)); value.put("updatedAt",tag.updatedAt.toInstant(ZoneOffset.UTC)); return value;
        }).toList();
    }
    public Map<String,Object> get(String user,String id) {
        return snapshot(db.required("SELECT * FROM notes WHERE user_id=? AND id=?",user,uuid(id)));
    }
    public Map<String,Object> snapshot(Map<String,Object> note) {
        Map<String,Object> result=new LinkedHashMap<>();
        for(String key:List.of("id","title","content","version","deleted","sourceNoteId","createdAt","updatedAt","deletedAt")) result.put(key,note.get(key));
        result.put("deleted",flag(note,"deleted"));
        result.put("tags",db.list("SELECT t.name,nt.source FROM note_tags nt JOIN tags t ON t.id=nt.tag_id AND t.user_id=nt.user_id WHERE nt.user_id=? AND nt.note_id=? ORDER BY t.name",note.get("userId"),note.get("id")));
        return result;
    }
    public Api.Page<Map<String,Object>> list(String user,String keyword,List<String> tagNames,Instant from,Instant to,int page,int size) {
        if(page<1 || size<1 || size>100 || keyword!=null && keyword.length()>200 || tagNames!=null && tagNames.size()>10) throw BusinessException.invalid("分页或筛选参数不正确");
        if(from!=null && to!=null && !from.isBefore(to)) throw BusinessException.invalid("起始时间必须早于结束时间");
        StringBuilder where=new StringBuilder(" WHERE n.user_id=? AND n.deleted=FALSE"); List<Object> args=new ArrayList<>(); args.add(user);
        if(keyword!=null && !keyword.isBlank()) {
            where.append(" AND (LOCATE(?,n.title)>0 OR LOCATE(?,n.content)>0)"); args.add(keyword.trim()); args.add(keyword.trim());
        }
        if(tagNames!=null && !tagNames.isEmpty()) {
            where.append(" AND EXISTS(SELECT 1 FROM note_tags nt JOIN tags t ON t.id=nt.tag_id AND t.user_id=nt.user_id WHERE nt.user_id=n.user_id AND nt.note_id=n.id AND t.name IN (").append(String.join(",",Collections.nCopies(tagNames.size(),"?"))).append("))"); args.addAll(tagNames);
        }
        if(from!=null) { where.append(" AND n.updated_at>=?"); args.add(from); }
        if(to!=null) { where.append(" AND n.updated_at<?"); args.add(to); }
        long total=db.count("SELECT COUNT(*) FROM notes n"+where,args.toArray());
        args.add(size); args.add((page-1L)*size);
        var rows=db.list("SELECT n.* FROM notes n"+where+" ORDER BY n.updated_at DESC,n.id LIMIT ? OFFSET ?",args.toArray());
        return new Api.Page<>(rows.stream().map(this::snapshot).toList(),total,page,size);
    }
    public void validate(NoteRequests.Write input) {
        uuid(input.id());
        if(input.baseVersion()==null || input.baseVersion()<0 || input.title()==null || input.title().isBlank() || input.title().length()>200 || input.content()==null || input.tags()==null || input.tags().size()>10) throw BusinessException.invalid("笔记参数不正确");
        if(Json.bytes(input.content())>maxBytes) throw new BusinessException(413,"CONTENT_TOO_LARGE","笔记正文超过允许的字节数");
        Set<String> names=new HashSet<>();
        for(var tag:input.tags()) {
            if(tag==null || tag.name()==null || tag.name().isBlank() || tag.name().trim().length()>50 || !Set.of("manual","llm").contains(tag.source())) throw BusinessException.invalid("标签格式不正确");
            if(!names.add(tag.name().trim())) throw BusinessException.invalid("标签名称不能重复");
        }
    }
    /** Caller holds the user lock; all mutation entry points converge here. */
    public Map<String,Object> mutate(SessionUser user,NoteRequests.Write input,boolean deleted,String source,String sourceNoteId,Map<String,Object> plan) {
        validate(input); String id=uuid(input.id());
        var old=db.one("SELECT * FROM notes WHERE user_id=? AND id=?",user.userId(),id);
        if(old==null && input.baseVersion()!=0) throw BusinessException.missing();
        if(old!=null && number(old,"version")!=input.baseVersion()) throw new BusinessException(409,"VERSION_CONFLICT","笔记已被其他设备修改",snapshot(old));
        if(old==null && deleted) throw BusinessException.missing();
        if(old!=null && flag(old,"deleted") && deleted) throw new BusinessException(409,"ALREADY_DELETED","笔记已删除");
        Instant now=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS); long version=old==null?1:number(old,"version")+1;
        Map<String,Object> data=new LinkedHashMap<>();
        data.put("id",id); data.put("title",input.title().trim()); data.put("content",input.content()); data.put("version",version); data.put("deleted",deleted);
        data.put("sourceNoteId",old==null?sourceNoteId:old.get("sourceNoteId")); data.put("createdAt",old==null?now:old.get("createdAt")); data.put("updatedAt",now); data.put("deletedAt",deleted?now:null);
        var normalized=input.tags().stream().map(t->Map.of("name",t.name().trim(),"source",t.source())).sorted(Comparator.comparing(t->t.get("name"))).toList();
        data.put("tags",normalized); String serialized=json.write(data); long bytes=Json.bytes(serialized);
        rights.checkStorage(user.userId(),bytes*2-(old==null?0:number(old,"snapshotBytes")),plan);
        if(old==null) db.update("INSERT INTO notes(id,user_id,title,content,version,deleted,source_note_id,snapshot_bytes,created_at,updated_at,deleted_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",id,user.userId(),input.title().trim(),input.content(),version,deleted,sourceNoteId,bytes,now,now,deleted?now:null);
        else db.update("UPDATE notes SET title=?,content=?,version=?,deleted=?,snapshot_bytes=?,updated_at=?,deleted_at=? WHERE user_id=? AND id=?",input.title().trim(),input.content(),version,deleted,bytes,now,deleted?now:null,user.userId(),id);
        db.update("DELETE FROM note_tags WHERE user_id=? AND note_id=?",user.userId(),id);
        for(var tag:normalized) {
            var existing=db.one("SELECT id FROM tags WHERE user_id=? AND name=?",user.userId(),tag.get("name")); String tagId;
            if(existing==null) {
                tagId=UUID.randomUUID().toString(); db.update("INSERT INTO tags(id,user_id,name,first_source,last_used_at,created_at,updated_at) VALUES(?,?,?,?,?,?,?)",tagId,user.userId(),tag.get("name"),tag.get("source"),now,now,now);
            } else { tagId=(String)existing.get("id"); db.update("UPDATE tags SET last_used_at=?,updated_at=? WHERE user_id=? AND id=?",now,now,user.userId(),tagId); }
            db.update("INSERT INTO note_tags(id,user_id,note_id,tag_id,source,created_at,updated_at) VALUES(?,?,?,?,?,?,?)",UUID.randomUUID().toString(),user.userId(),id,tagId,tag.get("source"),now,now);
        }
        db.update("INSERT INTO note_versions(id,user_id,note_id,version,snapshot,snapshot_bytes,source,created_at,expires_at) VALUES(?,?,?,?,?,?,?,?,?)",UUID.randomUUID().toString(),user.userId(),id,version,serialized,bytes,source,now,now.plusSeconds(number(plan,"historyDays")*86400));
        db.update("UPDATE user_preferences SET sync_revision=sync_revision+1,updated_at=? WHERE user_id=?",now,user.userId());
        long revision=db.count("SELECT sync_revision FROM user_preferences WHERE user_id=?",user.userId());
        db.update("INSERT INTO sync_changes(id,user_id,revision,note_id,operation,snapshot,device_id,created_at) VALUES(?,?,?,?,?,?,?,?)",UUID.randomUUID().toString(),user.userId(),revision,id,deleted?"delete":"upsert",serialized,user.deviceId(),now);
        return data;
    }
    @Transactional public Object save(SessionUser user,NoteRequests.Write input,boolean create) {
        db.lock(user.userId()); var plan=rights.require(user.userId(),"sync"); validate(input);
        if(create && input.baseVersion()!=0) throw BusinessException.invalid("新建笔记的 baseVersion 必须为 0");
        if(!create && input.baseVersion()<1) throw BusinessException.invalid("更新笔记必须携带当前 baseVersion");
        return mutate(user,input,false,"api",null,plan);
    }
    @Transactional public Object delete(SessionUser user,String id,long baseVersion) {
        db.lock(user.userId()); var current=get(user.userId(),id);
        // Deleting stored content remains available after consent is withdrawn or subscription expires.
        var active=rights.active(user.userId());
        var plan=Map.<String,Object>of("storageBytes",Long.MAX_VALUE,"historyDays",active==null?30L:number(active,"historyDays"));
        return mutate(user,fromSnapshot(current,id,baseVersion),true,"api",null,plan);
    }
    @SuppressWarnings("unchecked")
    public NoteRequests.Write fromSnapshot(Map<String,Object> snapshot,String id,long baseVersion) {
        List<NoteRequests.Tag> tags=((List<Map<String,Object>>)snapshot.get("tags")).stream().map(t->new NoteRequests.Tag((String)t.get("name"),(String)t.get("source"))).toList();
        return new NoteRequests.Write(id,baseVersion,(String)snapshot.get("title"),(String)snapshot.get("content"),tags);
    }
}
