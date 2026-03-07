package ee.parandiplaan.vault.dto;

import java.util.UUID;

public record VaultCategoryWithCountResponse(
        UUID id,
        String slug,
        String nameEt,
        String nameEn,
        String icon,
        int sortOrder,
        String fieldTemplate,
        long entryCount,
        long completedCount
) {}
