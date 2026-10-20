package com.smartknowledge.backend.backup;

import com.smartknowledge.backend.account.SessionUser;
import com.smartknowledge.backend.cloud.EntitlementService;
import com.smartknowledge.backend.common.*;
import com.smartknowledge.backend.note.NoteService;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.smartknowledge.backend.common.SqlMapper.*;

@Service
public class BackupService {
    private final SqlMapper db;
    private final NoteService notes;
    private final EntitlementService rights;
    private final Json json;
    private final Clock clock;
    private final int days,max;
    public BackupService(SqlMapper db,NoteService notes,EntitlementService rights,Json json,Clock clock,@Value("${app.backup-retention-days}") int days,@Value("${app.backup-max-count}") int max) { this.db=db; this.notes=notes; this.rights=rights; this.json=json; this.clock=clock; this.days=days; this.max=max; }
    @Transactional public Object create(SessionUser user,String name) {
        db.lock(user.userId()); var plan=rights.require(user.userId(),"backup"); Instant now=clock.instant();
        if(db.count("SELECT COUNT(*) FROM backups WHERE user_id=? AND expires_at>?",user.userId(),now)>=max) throw new BusinessException(409,"BACKUP_LIMIT","备份数量已达上限，请先删除旧备份");
        var rows=db.list("SELECT * FROM notes WHERE user_id=? AND deleted=FALSE ORDER BY id",user.userId());
        List<String> snapshots=rows.stream().map(notes::snapshot).map(json::write).toList(); long bytes=snapshots.stream().mapToLong(Json::bytes).sum(); rights.checkStorage(user.userId(),bytes,plan);
        String id=UUID.randomUUID().toString();
        db.update("INSERT INTO backups(id,user_id,name,status,note_count,size_bytes,checksum,created_at,expires_at,completed_at) VALUES(?,?,?,'ready',?,?,?,?,?,?)",id,user.userId(),name.trim(),rows.size(),bytes,checksum(snapshots),now,now.plusSeconds(days*86400L),now);
        for(int index=0;index<rows.size();index++) {
            String snapshot=snapshots.get(index); db.update("INSERT INTO backup_items(id,user_id,backup_id,note_id,snapshot,snapshot_bytes,checksum,created_at) VALUES(?,?,?,?,?,?,?,?)",UUID.randomUUID().toString(),user.userId(),id,rows.get(index).get("id"),snapshot,Json.bytes(snapshot),Json.hash(snapshot),now);
        }
        return detail(user.userId(),id);
    }
    public Object list(String user,int page,int size) {
        if(page<1 || size<1 || size>100) throw BusinessException.invalid("分页参数不正确");
        return new Api.Page<>(db.list("SELECT * FROM backups WHERE user_id=? AND expires_at>? ORDER BY created_at DESC,id LIMIT ? OFFSET ?",user,clock.instant(),size,(page-1L)*size),db.count("SELECT COUNT(*) FROM backups WHERE user_id=? AND expires_at>?",user,clock.instant()),page,size);
    }
    public Map<String,Object> detail(String user,String id) { return db.required("SELECT * FROM backups WHERE user_id=? AND id=? AND expires_at>?",user,uuid(id),clock.instant()); }
    public Object download(String user,String id) {
        id=uuid(id);
        var metadata=detail(user,id); var items=items(user,id);
        List<String> snapshots=items.stream().map(i->(String)i.get("snapshot")).toList();
        if(!checksum(snapshots).equals(metadata.get("checksum"))) throw new BusinessException(409,"BACKUP_CORRUPT","备份完整性校验失败");
        return Map.of("formatVersion",1,"backup",metadata,"notes",snapshots.stream().map(json::read).toList());
    }
    private List<Map<String,Object>> items(String user,String id) {
        var rows=db.list("SELECT * FROM backup_items WHERE user_id=? AND backup_id=? ORDER BY note_id",user,id);
        for(var row:rows) if(!Json.hash((String)row.get("snapshot")).equals(row.get("checksum"))) throw new BusinessException(409,"BACKUP_CORRUPT","备份项完整性校验失败");
        return rows;
    }
    private String checksum(List<String> snapshots) { return Json.hash(String.join("\n",snapshots.stream().map(Json::hash).toList())); }
    @Transactional public Object restore(SessionUser user,String id) {
        id=uuid(id);
        db.lock(user.userId()); var plan=rights.require(user.userId(),"backup"); download(user.userId(),id);
        List<Object> restored=new ArrayList<>();
        for(var item:items(user.userId(),id)) {
            String next=UUID.randomUUID().toString(); var snapshot=json.map((String)item.get("snapshot"));
            restored.add(notes.mutate(user,notes.fromSnapshot(snapshot,next,0),false,"restore",(String)item.get("noteId"),plan));
        }
        return Map.of("notes",restored);
    }
    @Transactional public void delete(SessionUser user,String id) {
        id=uuid(id);
        db.lock(user.userId()); db.required("SELECT id FROM backups WHERE user_id=? AND id=?",user.userId(),uuid(id)); db.update("DELETE FROM backups WHERE user_id=? AND id=?",user.userId(),id);
    }
    public Object versions(String user,String id,int page,int size) {
        id=uuid(id);
        notes.get(user,id); if(page<1 || size<1 || size>100) throw BusinessException.invalid("分页参数不正确");
        var rows=db.list("SELECT id,note_id,version,snapshot,source,created_at,expires_at FROM note_versions WHERE user_id=? AND note_id=? AND expires_at>? ORDER BY version DESC LIMIT ? OFFSET ?",user,id,clock.instant(),size,(page-1L)*size);
        rows.forEach(row->row.put("snapshot",json.read((String)row.get("snapshot"))));
        return new Api.Page<>(rows,db.count("SELECT COUNT(*) FROM note_versions WHERE user_id=? AND note_id=? AND expires_at>?",user,id,clock.instant()),page,size);
    }
    @Transactional public Object restoreVersion(SessionUser user,String noteId,String versionId,long baseVersion) {
        noteId=uuid(noteId); versionId=uuid(versionId);
        db.lock(user.userId()); var plan=rights.require(user.userId(),"backup");
        var version=db.required("SELECT * FROM note_versions WHERE user_id=? AND note_id=? AND id=? AND expires_at>?",user.userId(),uuid(noteId),uuid(versionId),clock.instant());
        var snapshot=json.map((String)version.get("snapshot")); return notes.mutate(user,notes.fromSnapshot(snapshot,noteId,baseVersion),flag(snapshot,"deleted"),"restore",null,plan);
    }
}
