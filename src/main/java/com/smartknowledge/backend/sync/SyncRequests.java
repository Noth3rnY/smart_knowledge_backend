package com.smartknowledge.backend.sync;

import com.smartknowledge.backend.note.NoteRequests;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public final class SyncRequests {
    private SyncRequests() {}
    public record Change(@NotBlank @Pattern(regexp="upsert|delete") String operation,@NotNull @Valid NoteRequests.Write note) {}
    public record Push(@NotBlank String deviceUuid,@NotBlank String requestId,@NotEmpty @Size(max=100) List<@Valid Change> changes) {}
    public record Resolve(@NotNull @Min(1) Long serverVersion,@NotBlank @Pattern(regexp="server|submitted|merged") String choice,@Valid NoteRequests.Write merged) {}
}
