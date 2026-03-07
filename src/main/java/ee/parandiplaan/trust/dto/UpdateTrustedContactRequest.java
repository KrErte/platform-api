package ee.parandiplaan.trust.dto;

import jakarta.validation.constraints.Size;

public record UpdateTrustedContactRequest(
        @Size(max = 255)
        String fullName,

        String phone,

        String relationship,

        String accessLevel,

        String activationMode,

        Integer inactivityDays
) {}
