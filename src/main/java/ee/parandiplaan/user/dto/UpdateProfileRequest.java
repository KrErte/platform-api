package ee.parandiplaan.user.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record UpdateProfileRequest(
        @NotBlank String fullName,
        String phone,
        LocalDate dateOfBirth,
        String country,
        String language
) {}
