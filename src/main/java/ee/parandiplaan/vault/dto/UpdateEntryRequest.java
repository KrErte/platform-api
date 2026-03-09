package ee.parandiplaan.vault.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record UpdateEntryRequest(
        @NotBlank String title,
        @NotBlank String data,
        String notes,
        Boolean isComplete,
        LocalDate reminderDate
) {}
