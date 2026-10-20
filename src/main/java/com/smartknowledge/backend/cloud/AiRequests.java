package com.smartknowledge.backend.cloud;

import jakarta.validation.constraints.*;
import java.util.List;

public final class AiRequests {
    private AiRequests() {}
    public record Tags(@NotBlank String requestId,@NotBlank @Size(max=200) String title,@NotNull String content,
                       @NotNull @Size(max=10) List<@NotBlank @Size(max=50) String> existingTags) {}
}
