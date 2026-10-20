package com.smartknowledge.backend.account;

import com.smartknowledge.backend.common.Api;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AccountController {
    private final AccountService service;
    public AccountController(AccountService service) { this.service=service; }
    @PostMapping("/auth/register") public Api<?> register(@Valid @RequestBody AccountRequests.Register r) { return Api.ok(service.register(r)); }
    @PostMapping("/auth/login") public Api<?> login(@Valid @RequestBody AccountRequests.Login r) { return Api.ok(service.login(r)); }
    @PostMapping("/auth/refresh") public Api<?> refresh(@Valid @RequestBody AccountRequests.Refresh r) { return Api.ok(service.refresh(r.refreshToken())); }
    @PostMapping("/auth/logout") public Api<?> logout(@AuthenticationPrincipal SessionUser u) { service.revoke(u,u.deviceId()); return Api.ok(null); }
    @GetMapping("/me") public Api<?> me(@AuthenticationPrincipal SessionUser u) { return Api.ok(service.me(u)); }
    @PatchMapping("/me") public Api<?> profile(@AuthenticationPrincipal SessionUser u,@Valid @RequestBody AccountRequests.Profile r) { return Api.ok(service.profile(u,r)); }
    @PutMapping("/me/preferences") public Api<?> preferences(@AuthenticationPrincipal SessionUser u,@Valid @RequestBody AccountRequests.Preferences r) { return Api.ok(service.preferences(u,r)); }
    @GetMapping("/devices") public Api<?> devices(@AuthenticationPrincipal SessionUser u) { return Api.ok(service.devices(u)); }
    @DeleteMapping("/devices/{id}") public Api<?> revoke(@AuthenticationPrincipal SessionUser u,@PathVariable String id) { service.revoke(u,id); return Api.ok(null); }
}
