package ee.parandiplaan.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        String action,
        String detail,
        Instant createdAt
) {}
