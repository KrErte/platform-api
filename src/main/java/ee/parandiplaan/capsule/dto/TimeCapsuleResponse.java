package ee.parandiplaan.capsule.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TimeCapsuleResponse(
        UUID id,
        UUID recipientContactId,
        String recipientName,
        String encryptedTitle,
        String encryptedMessage,
        String triggerType,
        LocalDate triggerDate,
        String status,
        Instant deliveredAt,
        Instant createdAt,
        Instant updatedAt
) {}
