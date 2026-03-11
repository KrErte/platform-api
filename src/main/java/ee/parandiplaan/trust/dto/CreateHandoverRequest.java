package ee.parandiplaan.trust.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateHandoverRequest(
        @NotNull(message = "Usalduskontakti ID on kohustuslik")
        UUID trustedContactId,

        @NotNull(message = "Kutse token on kohustuslik")
        UUID inviteToken,

        String reason
) {}
