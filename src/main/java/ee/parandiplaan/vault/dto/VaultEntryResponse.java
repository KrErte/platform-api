package ee.parandiplaan.vault.dto;

import java.time.Instant;
import java.util.UUID;

public record VaultEntryResponse(
        UUID id,
        UUID categoryId,
        String categorySlug,
        String categoryIcon,
        String title,
        String data,
        String notes,
        boolean isComplete,
        boolean hasAttachments,
        Instant lastReviewedAt,
        Instant createdAt,
        Instant updatedAt
) {}
