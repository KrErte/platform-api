package ee.parandiplaan.admin.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String email,
        String fullName,
        String role,
        boolean emailVerified,
        boolean totpEnabled,
        String subscriptionPlan,
        String subscriptionStatus,
        long vaultEntryCount,
        long trustedContactCount,
        Instant lastLoginAt,
        Instant createdAt
) {}
