package ee.parandiplaan.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 255) String fullName,
        @Size(max = 30) String phone,
        LocalDate dateOfBirth,
        @Size(max = 10) String country,
        @Size(max = 5) String language
) {}
