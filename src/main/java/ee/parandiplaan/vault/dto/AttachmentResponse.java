package ee.parandiplaan.vault.dto;

import java.time.Instant;
import java.util.UUID;

public record AttachmentResponse(
        UUID id,
        UUID vaultEntryId,
        String fileName,
        long fileSizeBytes,
        String mimeType,
        Instant createdAt
) {}
