package ee.parandiplaan.trust.dto;

import java.time.Instant;
import java.util.UUID;

public record TrustedContactResponse(
        UUID id,
        String fullName,
        String email,
        String phone,
        String relationship,
        String accessLevel,
        String activationMode,
        Integer inactivityDays,
        UUID[] allowedCategories,
        boolean inviteAccepted,
        Instant inviteAcceptedAt,
        UUID inviteToken,
        Instant createdAt,
        Instant updatedAt
) {}
