package ee.parandiplaan.vault.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateEntryRequest(
        @NotNull UUID categoryId,
        @NotBlank String title,
        @NotBlank String data,
        String notes,
        LocalDate reminderDate
) {}
