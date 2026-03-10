package ee.parandiplaan.auth.eid.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EIdStartRequest(
        @NotBlank(message = "Isikukood on kohustuslik")
        @Pattern(regexp = "^[0-9]{11}$", message = "Isikukood peab olema 11-kohaline number")
        String personalCode,

        @Pattern(regexp = "^(EE|LV|LT)$", message = "Lubatud riigid: EE, LV, LT")
        String country
) {
    public EIdStartRequest {
        if (country == null || country.isBlank()) country = "EE";
    }
}
