package com.smartknowledge.backend.backup;

import com.smartknowledge.backend.account.SessionUser;
import com.smartknowledge.backend.common.*;
import com.smartknowledge.backend.note.NoteRequests;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class BackupController {
    private final BackupService service;
    public BackupController(BackupService service) { this.service=service; }
    public record Create(@NotBlank @Size(max=100) String name) {}
    @PostMapping("/backups") public Api<?> create(@AuthenticationPrincipal SessionUser u,@Valid @RequestBody Create r) { return Api.ok(service.create(u,r.name())); }
    @GetMapping("/backups") public Api<?> list(@AuthenticationPrincipal SessionUser u,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) { return Api.ok(service.list(u.userId(),page,size)); }
    @GetMapping("/backups/{id}") public Api<?> detail(@AuthenticationPrincipal SessionUser u,@PathVariable String id) { return Api.ok(service.detail(u.userId(),id)); }
    @GetMapping("/backups/{id}/download") public ResponseEntity<?> download(@AuthenticationPrincipal SessionUser u,@PathVariable String id) { return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=backup-"+com.smartknowledge.backend.common.SqlMapper.uuid(id)+".json").body(Api.ok(service.download(u.userId(),id))); }
    @PostMapping("/backups/{id}/restore") public Api<?> restore(@AuthenticationPrincipal SessionUser u,@PathVariable String id) { return Api.ok(service.restore(u,id)); }
    @DeleteMapping("/backups/{id}") public Api<?> delete(@AuthenticationPrincipal SessionUser u,@PathVariable String id) { service.delete(u,id); return Api.ok(null); }
    @GetMapping("/notes/{id}/versions") public Api<?> versions(@AuthenticationPrincipal SessionUser u,@PathVariable String id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) { return Api.ok(service.versions(u.userId(),id,page,size)); }
    @PostMapping("/notes/{id}/versions/{versionId}/restore") public Api<?> restoreVersion(@AuthenticationPrincipal SessionUser u,@PathVariable String id,@PathVariable String versionId,@Valid @RequestBody NoteRequests.Restore r) { return Api.ok(service.restoreVersion(u,id,versionId,r.baseVersion())); }
}
