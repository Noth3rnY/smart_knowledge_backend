package com.smartknowledge.backend.cloud;

import com.smartknowledge.backend.account.SessionUser;
import com.smartknowledge.backend.common.Api;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class SubscriptionController {
    private final EntitlementService service;
    public SubscriptionController(EntitlementService service) { this.service=service; }
    @GetMapping("/plans") public Api<?> plans() { return Api.ok(service.plans()); }
    @GetMapping("/subscriptions/me") public Api<?> me(@AuthenticationPrincipal SessionUser u) { return Api.ok(service.subscription(u.userId())); }
}
