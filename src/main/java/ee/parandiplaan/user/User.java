package ee.parandiplaan.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    private String phone;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(nullable = false)
    private String country = "EST";

    @Column(nullable = false)
    private String language = "et";

    @Column(name = "totp_secret")
    private String totpSecret;

    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled = false;

    @Column(name = "totp_backup_codes")
    private String totpBackupCodes;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "email_verification_token")
    private UUID emailVerificationToken;

    @Column(name = "password_reset_token")
    private UUID passwordResetToken;

    @Column(name = "password_reset_token_expires_at")
    private Instant passwordResetTokenExpiresAt;

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted = false;

    @Column(name = "notify_expiration_reminders", nullable = false)
    private boolean notifyExpirationReminders = true;

    @Column(name = "notify_inactivity_warnings", nullable = false)
    private boolean notifyInactivityWarnings = true;

    @Column(name = "notify_security_alerts", nullable = false)
    private boolean notifySecurityAlerts = true;

    @Column(name = "encryption_salt", length = 64)
    private String encryptionSalt;

    @Column(name = "personal_code", length = 20, unique = true)
    private String personalCode;

    @Column(name = "encrypted_vault_key")
    private String encryptedVaultKey;

    @Column(name = "vault_key_escrowed_at")
    private Instant vaultKeyEscrowedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
