package ee.parandiplaan.vault.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateEntryRequest(
        @NotBlank @Size(max = 5000) String title,
        @NotBlank @Size(max = 50000) String data,
        @Size(max = 10000) String notes,
        Boolean isComplete,
        LocalDate reminderDate
) {}
