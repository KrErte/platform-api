package ee.parandiplaan.trust.dto;

import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateTrustedContactRequest(
        @Size(max = 255)
        String fullName,

        String phone,

        String relationship,

        String accessLevel,

        String activationMode,

        Integer inactivityDays,

        UUID[] allowedCategories
) {}
