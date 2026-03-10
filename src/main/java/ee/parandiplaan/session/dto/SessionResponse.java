package ee.parandiplaan.session.dto;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        String deviceLabel,
        String ipAddress,
        Instant createdAt,
        Instant lastUsedAt,
        boolean current
) {}
