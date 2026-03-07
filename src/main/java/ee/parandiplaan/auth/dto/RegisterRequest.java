package ee.parandiplaan.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, message = "Parool peab olema vähemalt 8 tähemärki") String password,
        @NotBlank String fullName
) {}
