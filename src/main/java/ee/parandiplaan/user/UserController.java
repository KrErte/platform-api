package ee.parandiplaan.user;

import ee.parandiplaan.common.security.CurrentUser;
import ee.parandiplaan.notification.EmailService;
import ee.parandiplaan.user.dto.DeleteAccountRequest;
import ee.parandiplaan.user.dto.EmailPreferencesRequest;
import ee.parandiplaan.user.dto.UpdateProfileRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final UserService userService;
    private final GdprExportService gdprExportService;
    private final EmailService emailService;

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@CurrentUser User user) {
        return ResponseEntity.ok(buildUserResponse(user));
    }

    @PostMapping("/me/complete-onboarding")
    public ResponseEntity<Map<String, Object>> completeOnboarding(@CurrentUser User user) {
        user.setOnboardingCompleted(true);
        user = userRepository.save(user);
        return ResponseEntity.ok(buildUserResponse(user));
    }

    @PutMapping("/me")
    public ResponseEntity<Map<String, Object>> updateProfile(
            @CurrentUser User user,
            @Valid @RequestBody UpdateProfileRequest request) {
        user.setFullName(request.fullName().trim());
        user.setPhone(request.phone());
        user.setDateOfBirth(request.dateOfBirth());
        if (request.country() != null) {
            user.setCountry(request.country());
        }
        if (request.language() != null) {
            user.setLanguage(request.language());
        }
        user = userRepository.save(user);
        return ResponseEntity.ok(buildUserResponse(user));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Map<String, String>> deleteAccount(
            @CurrentUser User user,
            @Valid @RequestBody DeleteAccountRequest request) {
        userService.deleteAccount(user, request.password());
        return ResponseEntity.ok(Map.of("message", "Konto kustutatud"));
    }

    @PutMapping("/me/email-preferences")
    public ResponseEntity<Map<String, Object>> updateEmailPreferences(
            @CurrentUser User user,
            @RequestBody EmailPreferencesRequest request) {
        user.setNotifyExpirationReminders(request.notifyExpirationReminders());
        user.setNotifyInactivityWarnings(request.notifyInactivityWarnings());
        user.setNotifySecurityAlerts(request.notifySecurityAlerts());
        user.setNotifySms(request.notifySms());
        user = userRepository.save(user);
        return ResponseEntity.ok(buildUserResponse(user));
    }

    @PutMapping("/me/email")
    public ResponseEntity<Map<String, Object>> updateEmail(
            @CurrentUser User user,
            @RequestBody Map<String, String> body) {
        String newEmail = body.get("email");
        if (newEmail == null || newEmail.isBlank()) {
            throw new IllegalArgumentException("E-posti aadress on kohustuslik");
        }
        newEmail = newEmail.toLowerCase().trim();

        if (userRepository.existsByEmail(newEmail)) {
            throw new IllegalArgumentException("See e-posti aadress on juba kasutusel");
        }

        user.setEmail(newEmail);
        user.setEmailVerified(false);
        user.setEmailVerificationToken(UUID.randomUUID());
        user = userRepository.save(user);

        emailService.sendVerificationEmail(
                user.getEmail(),
                user.getFullName(),
                user.getEmailVerificationToken().toString()
        );

        return ResponseEntity.ok(buildUserResponse(user));
    }

    @GetMapping("/me/export")
    public ResponseEntity<Map<String, Object>> exportMyData(@CurrentUser User user) {
        return ResponseEntity.ok(gdprExportService.exportAllUserData(user));
    }

    private Map<String, Object> buildUserResponse(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("email", user.getEmail());
        map.put("fullName", user.getFullName());
        map.put("phone", user.getPhone() != null ? user.getPhone() : "");
        map.put("dateOfBirth", user.getDateOfBirth() != null ? user.getDateOfBirth().toString() : null);
        map.put("country", user.getCountry());
        map.put("language", user.getLanguage());
        map.put("emailVerified", user.isEmailVerified());
        map.put("totpEnabled", user.isTotpEnabled());
        map.put("onboardingCompleted", user.isOnboardingCompleted());
        map.put("notifyExpirationReminders", user.isNotifyExpirationReminders());
        map.put("notifyInactivityWarnings", user.isNotifyInactivityWarnings());
        map.put("notifySecurityAlerts", user.isNotifySecurityAlerts());
        map.put("notifySms", user.isNotifySms());
        map.put("vaultKeyEscrowed", user.getEncryptedVaultKey() != null);
        map.put("vaultKeyEscrowedAt", user.getVaultKeyEscrowedAt() != null ? user.getVaultKeyEscrowedAt().toString() : null);
        map.put("isEidUser", user.getPersonalCode() != null);
        map.put("needsEmail", user.getEmail() != null && user.getEmail().endsWith("@eid.parandiplaan.ee"));
        map.put("createdAt", user.getCreatedAt().toString());
        return map;
    }
}
