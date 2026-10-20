package com.smartknowledge.backend.note;

import com.smartknowledge.backend.account.SessionUser;
import com.smartknowledge.backend.common.*;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import static com.smartknowledge.backend.common.SqlMapper.uuid;

@RestController
@RequestMapping("/api/v1")
public class NoteController {
    private final NoteService service;
    public NoteController(NoteService service) { this.service=service; }
    @GetMapping("/notes") public Api<?> list(@AuthenticationPrincipal SessionUser u,@RequestParam(required=false) String keyword,@RequestParam(required=false) List<String> tags,@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) { return Api.ok(service.list(u.userId(),keyword,tags,from,to,page,size)); }
    @GetMapping("/notes/{id}") public Api<?> get(@AuthenticationPrincipal SessionUser u,@PathVariable String id) { return Api.ok(service.get(u.userId(),id)); }
    @PostMapping("/notes") public Api<?> create(@AuthenticationPrincipal SessionUser u,@Valid @RequestBody NoteRequests.Write r) { return Api.ok(service.save(u,r,true)); }
    @PutMapping("/notes/{id}") public Api<?> update(@AuthenticationPrincipal SessionUser u,@PathVariable String id,@Valid @RequestBody NoteRequests.Write r) {
        if(!uuid(id).equals(uuid(r.id()))) throw BusinessException.invalid("路径与请求笔记 ID 不一致"); return Api.ok(service.save(u,r,false));
    }
    @DeleteMapping("/notes/{id}") public Api<?> delete(@AuthenticationPrincipal SessionUser u,@PathVariable String id,@Valid @RequestBody NoteRequests.Version r) { return Api.ok(service.delete(u,id,r.baseVersion())); }
    @GetMapping("/tags") public Api<?> tags(@AuthenticationPrincipal SessionUser u) { return Api.ok(service.tags(u.userId())); }
}
