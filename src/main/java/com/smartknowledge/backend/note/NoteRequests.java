package com.smartknowledge.backend.note;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public final class NoteRequests {
    private NoteRequests() {}
    public record Tag(@NotBlank @Size(max=50) String name,@NotBlank @Pattern(regexp="manual|llm") String source) {}
    public record Write(@NotBlank String id,@NotNull @Min(0) Long baseVersion,@NotBlank @Size(max=200) String title,
                        @NotNull String content,@NotNull @Size(max=10) List<@Valid Tag> tags) {}
    public record Version(@NotNull @Min(1) Long baseVersion) {}
    public record Restore(@NotNull @Min(1) Long baseVersion) {}
}
