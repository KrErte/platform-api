package ee.parandiplaan.vault.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateEntryRequest(
        @NotNull UUID categoryId,
        @NotBlank @Size(max = 5000) String title,
        @NotBlank @Size(max = 50000) String data,
        @Size(max = 10000) String notes,
        LocalDate reminderDate
) {}
