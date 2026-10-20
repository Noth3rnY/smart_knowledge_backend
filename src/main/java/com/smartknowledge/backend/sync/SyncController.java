package com.smartknowledge.backend.sync;

import com.smartknowledge.backend.account.SessionUser;
import com.smartknowledge.backend.common.Api;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {
    private final SyncService service;
    public SyncController(SyncService service) { this.service=service; }
    @PostMapping("/push") public Api<?> push(@AuthenticationPrincipal SessionUser u,@Valid @RequestBody SyncRequests.Push r) { return Api.ok(service.push(u,r)); }
    @GetMapping("/pull") public Api<?> pull(@AuthenticationPrincipal SessionUser u,@RequestParam(defaultValue="0") long after,@RequestParam(defaultValue="50") int size) { return Api.ok(service.pull(u.userId(),after,size)); }
    @GetMapping("/conflicts") public Api<?> conflicts(@AuthenticationPrincipal SessionUser u,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) { return Api.ok(service.conflicts(u.userId(),page,size)); }
    @PostMapping("/conflicts/{id}/resolve") public Api<?> resolve(@AuthenticationPrincipal SessionUser u,@PathVariable String id,@Valid @RequestBody SyncRequests.Resolve r) { return Api.ok(service.resolve(u,id,r)); }
}
