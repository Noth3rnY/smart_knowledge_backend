package com.smartknowledge.backend.cloud;

import com.smartknowledge.backend.account.SessionUser;
import com.smartknowledge.backend.common.Api;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Profile("demo")
@RestController
@RequestMapping("/api/v1/subscriptions")
public class DemoSubscriptionController {
    private final EntitlementService service;
    public DemoSubscriptionController(EntitlementService service) { this.service=service; }
    public record Activate(@NotBlank String requestId) {}
    @PostMapping("/demo-activate") public Api<?> activate(@AuthenticationPrincipal SessionUser u,@Valid @RequestBody Activate r) { return Api.ok(service.activate(u.userId(),r.requestId())); }
}
