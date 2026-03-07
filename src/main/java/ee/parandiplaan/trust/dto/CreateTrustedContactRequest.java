package ee.parandiplaan.trust.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTrustedContactRequest(
        @NotBlank(message = "Nimi on kohustuslik")
        @Size(max = 255)
        String fullName,

        @NotBlank(message = "E-post on kohustuslik")
        @Email(message = "Vigane e-posti aadress")
        String email,

        String phone,

        String relationship,

        String accessLevel,

        String activationMode,

        Integer inactivityDays
) {}
