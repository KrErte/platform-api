package ee.parandiplaan.auth.eid.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MIdStartRequest(
        @NotBlank(message = "Isikukood on kohustuslik")
        @Pattern(regexp = "^[0-9]{11}$", message = "Isikukood peab olema 11-kohaline number")
        String personalCode,

        @NotBlank(message = "Telefoninumber on kohustuslik")
        @Pattern(regexp = "^\\+372[0-9]{7,8}$", message = "Telefoninumber peab olema Eesti number (+372...)")
        String phoneNumber
) {}
