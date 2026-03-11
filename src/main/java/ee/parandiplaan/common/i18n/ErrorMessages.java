package ee.parandiplaan.common.i18n;

import java.util.Map;

/**
 * Simple ET/EN error message resolver.
 * Usage: ErrorMessages.get("auth.invalid_credentials", "et")
 */
public final class ErrorMessages {

    private ErrorMessages() {}

    private static final Map<String, Map<String, String>> MESSAGES = Map.ofEntries(
            entry("auth.email_taken",
                    "See e-posti aadress on juba kasutusel",
                    "This email address is already in use"),
            entry("auth.invalid_credentials",
                    "Vale e-post või parool",
                    "Invalid email or password"),
            entry("auth.totp_required",
                    "2FA kood on nõutud",
                    "2FA code is required"),
            entry("auth.totp_invalid",
                    "Vale 2FA kood",
                    "Invalid 2FA code"),
            entry("auth.invalid_refresh_token",
                    "Kehtetu refresh token",
                    "Invalid refresh token"),
            entry("auth.wrong_token_type",
                    "Vale tokeni tüüp",
                    "Wrong token type"),
            entry("auth.invalid_verification_token",
                    "Kehtetu kinnitustoken",
                    "Invalid verification token"),
            entry("auth.invalid_reset_token",
                    "Kehtetu või aegunud lähtestamislink",
                    "Invalid or expired reset link"),
            entry("auth.reset_token_expired",
                    "Lähtestamislink on aegunud. Palun taotlege uus.",
                    "Reset link has expired. Please request a new one."),
            entry("auth.wrong_current_password",
                    "Praegune parool on vale",
                    "Current password is incorrect"),
            entry("user.not_found",
                    "Kasutajat ei leitud",
                    "User not found"),
            entry("session.not_found",
                    "Sessioon ei leitud",
                    "Session not found"),
            entry("session.revoked",
                    "Sessioon on tühistatud",
                    "Session has been revoked"),
            entry("vault.entry_not_found",
                    "Kirjet ei leitud",
                    "Entry not found"),
            entry("vault.category_not_found",
                    "Kategooriat ei leitud",
                    "Category not found"),
            entry("trust.contact_not_found",
                    "Kontakti ei leitud",
                    "Contact not found"),
            entry("trust.invite_not_found",
                    "Kutset ei leitud",
                    "Invite not found"),
            entry("subscription.limit_reached",
                    "Oled saavutanud oma plaani limiidi",
                    "You have reached your plan limit"),
            entry("account.deleted",
                    "Konto kustutatud",
                    "Account deleted"),
            entry("general.server_error",
                    "Serveri viga. Palun proovi hiljem uuesti.",
                    "Server error. Please try again later.")
    );

    /**
     * Get a localized error message.
     *
     * @param key  message key like "auth.invalid_credentials"
     * @param lang "et" or "en", defaults to "et"
     * @return the translated message, or the key if not found
     */
    public static String get(String key, String lang) {
        var langMap = MESSAGES.get(key);
        if (langMap == null) return key;
        String resolved = langMap.get(lang != null ? lang : "et");
        return resolved != null ? resolved : langMap.getOrDefault("et", key);
    }

    /** Convenience method — default language is Estonian */
    public static String get(String key) {
        return get(key, "et");
    }

    private static Map.Entry<String, Map<String, String>> entry(String key, String et, String en) {
        return Map.entry(key, Map.of("et", et, "en", en));
    }
}
