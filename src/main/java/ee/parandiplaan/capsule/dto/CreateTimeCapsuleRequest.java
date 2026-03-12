package ee.parandiplaan.capsule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateTimeCapsuleRequest(
        @NotNull UUID recipientContactId,
        @NotBlank String encryptedTitle,
        @NotBlank String encryptedMessage,
        @NotBlank String triggerType,
        LocalDate triggerDate
) {}
