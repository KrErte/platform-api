package ee.parandiplaan.capsule.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record UpdateTimeCapsuleRequest(
        @NotBlank String encryptedTitle,
        @NotBlank String encryptedMessage,
        @NotBlank String triggerType,
        LocalDate triggerDate
) {}
