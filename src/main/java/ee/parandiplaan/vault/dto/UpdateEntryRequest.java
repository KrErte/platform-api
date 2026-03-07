package ee.parandiplaan.vault.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateEntryRequest(
        @NotBlank String title,
        @NotBlank String data,
        String notes,
        Boolean isComplete
) {}
