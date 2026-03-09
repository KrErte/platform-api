package ee.parandiplaan.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ResetPasswordRequest(
        @NotNull UUID token,
        @NotBlank @Size(min = 8) String newPassword
) {}
