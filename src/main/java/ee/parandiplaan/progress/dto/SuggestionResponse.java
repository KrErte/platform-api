package ee.parandiplaan.progress.dto;

import java.util.UUID;

public record SuggestionResponse(
        String type,
        int priority,
        UUID categoryId,
        String titleEt,
        String descriptionEt,
        int pointsToGain
) {}
