package ee.parandiplaan.trust.dto;

import java.time.Instant;
import java.util.UUID;

public record HandoverRequestResponse(
        UUID id,
        UUID trustedContactId,
        String contactName,
        String contactEmail,
        String status,
        String reason,
        Instant requestedAt,
        Instant responseDeadline,
        Instant respondedAt,
        String respondedBy,
        Instant createdAt
) {}
