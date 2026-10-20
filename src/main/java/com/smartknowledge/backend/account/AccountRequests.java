package com.smartknowledge.backend.account;

import jakarta.validation.constraints.*;

public final class AccountRequests {
    private AccountRequests() {}
    public record Register(@NotBlank @Pattern(regexp="[A-Za-z0-9_]{3,64}") String username,
                           @NotBlank @Size(min=8,max=72) String password, @NotBlank @Size(max=100) String nickname) {}
    public record Login(@NotBlank @Size(max=64) String username, @NotBlank @Size(max=72) String password, @NotBlank String deviceUuid,
                        @NotBlank @Size(max=100) String deviceName, @NotBlank @Size(max=30) String platform) {}
    public record Refresh(@NotBlank @Size(max=200) String refreshToken) {}
    public record Profile(@NotBlank @Size(max=100) String nickname) {}
    public record Preferences(@NotNull Boolean syncEnabled, @NotNull Boolean backupEnabled, @NotNull Boolean aiEnabled,
                              @NotBlank String consentVersion) {}
}
