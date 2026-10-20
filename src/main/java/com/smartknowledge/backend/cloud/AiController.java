package com.smartknowledge.backend.cloud;

import com.smartknowledge.backend.account.SessionUser;
import com.smartknowledge.backend.common.Api;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {
    private final AiService service;
    public AiController(AiService service) { this.service=service; }
    @PostMapping("/tags") public Api<?> generate(@AuthenticationPrincipal SessionUser u,@Valid @RequestBody AiRequests.Tags r) { return Api.ok(service.generate(u.userId(),r)); }
}
