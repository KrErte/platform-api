package ee.parandiplaan.auth;

import ee.parandiplaan.audit.AuditService;
import ee.parandiplaan.auth.dto.AuthResponse;
import ee.parandiplaan.auth.dto.LoginRequest;
import ee.parandiplaan.auth.dto.RegisterRequest;
import ee.parandiplaan.common.security.JwtService;
import ee.parandiplaan.notification.EmailService;
import ee.parandiplaan.progress.UserProgress;
import ee.parandiplaan.progress.UserProgressRepository;
import ee.parandiplaan.session.SessionService;
import ee.parandiplaan.session.UserSession;
import ee.parandiplaan.subscription.Subscription;
import ee.parandiplaan.subscription.SubscriptionRepository;
import ee.parandiplaan.user.User;
import ee.parandiplaan.user.UserRepository;
import ee.parandiplaan.vault.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserProgressRepository userProgressRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final TotpService totpService;
    private final VaultEntryRepository vaultEntryRepository;
    private final VaultAttachmentRepository vaultAttachmentRepository;
    private final EncryptionService encryptionService;
    private final StorageService storageService;
    private final AuditService auditService;
    private final SessionService sessionService;
    private final VaultEscrowService vaultEscrowService;

    @Transactional
    public AuthResponse register(RegisterRequest request, String ip, String userAgent) {
        if (userRepository.existsByEmail(request.email().toLowerCase())) {
            throw new IllegalArgumentException("See e-posti aadress on juba kasutusel");
        }

        User user = new User();
        user.setEmail(request.email().toLowerCase().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setEmailVerificationToken(UUID.randomUUID());
        user.setEncryptionSalt(EncryptionService.generateSalt());
        user = userRepository.save(user);

        // Create trial subscription
        Subscription sub = new Subscription();
        sub.setUser(user);
        Instant now = Instant.now();
        sub.setCurrentPeriodStart(now);
        sub.setCurrentPeriodEnd(now.plus(Subscription.TRIAL_DURATION_DAYS, ChronoUnit.DAYS));
        subscriptionRepository.save(sub);

        // Create empty progress
        UserProgress progress = new UserProgress();
        progress.setUser(user);
        userProgressRepository.save(progress);

        // Escrow vault key for future shared access
        vaultEscrowService.escrowVaultKey(user, request.password());

        log.info("New user registered: {}", user.getEmail());

        // Send verification email
        emailService.sendVerificationEmail(
                user.getEmail(),
                user.getFullName(),
                user.getEmailVerificationToken().toString()
        );

        return buildAuthResponseWithSession(user, ip, userAgent);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String ip, String userAgent) {
        User user = userRepository.findByEmail(request.email().toLowerCase().trim())
                .orElseThrow(() -> new IllegalArgumentException("Vale e-post või parool"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Vale e-post või parool");
        }

        // Check 2FA if enabled
        if (user.isTotpEnabled()) {
            if (request.totpCode() == null || request.totpCode().isBlank()) {
                throw new IllegalArgumentException("2FA kood on nõutud");
            }
            if (!totpService.validate(user, request.totpCode())) {
                throw new IllegalArgumentException("Vale 2FA kood");
            }
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        // Re-escrow vault key on every login
        vaultEscrowService.escrowVaultKey(user, request.password());

        log.info("User logged in: {}", user.getEmail());
        auditService.log(user, "LOGIN", "Kasutaja logis sisse", ip);

        return buildAuthResponseWithSession(user, ip, userAgent);
    }

    @Transactional
    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtService.isTokenValid(refreshToken)) {
            throw new IllegalArgumentException("Kehtetu refresh token");
        }

        if (!"refresh".equals(jwtService.getTokenType(refreshToken))) {
            throw new IllegalArgumentException("Vale tokeni tüüp");
        }

        // Validate session — denies refresh if session is revoked
        UserSession session = sessionService.validateAndTouchSession(refreshToken);

        UUID userId = jwtService.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Kasutajat ei leitud"));

        // Generate new tokens with session context
        String newRefreshToken = jwtService.generateRefreshToken(userId);
        String accessToken = jwtService.generateAccessToken(userId, user.getEmail(), session.getId());

        // Rotate the refresh token hash
        sessionService.updateRefreshTokenHash(session.getId(), newRefreshToken);

        return new AuthResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                accessToken,
                newRefreshToken,
                user.isEmailVerified()
        );
    }

    @Transactional
    public void verifyEmail(UUID token) {
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Kehtetu kinnitustoken"));

        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        userRepository.save(user);

        log.info("Email verified: {}", user.getEmail());
    }

    @Transactional
    public void forgotPassword(String email) {
        var userOpt = userRepository.findByEmail(email.toLowerCase().trim());
        if (userOpt.isEmpty()) {
            log.info("Password reset requested for non-existent email: {}", email);
            return; // Silent return — email enumeration protection
        }

        User user = userOpt.get();
        UUID resetToken = UUID.randomUUID();
        user.setPasswordResetToken(resetToken);
        user.setPasswordResetTokenExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
        userRepository.save(user);

        emailService.sendPasswordResetEmail(user.getEmail(), user.getFullName(), resetToken.toString());
        log.info("Password reset token generated for: {}", user.getEmail());
    }

    @Transactional
    public AuthResponse resetPassword(UUID token, String newPassword, String ip, String userAgent) {
        User user = userRepository.findByPasswordResetToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Kehtetu või aegunud lähtestamislink"));

        if (user.getPasswordResetTokenExpiresAt() == null ||
                Instant.now().isAfter(user.getPasswordResetTokenExpiresAt())) {
            throw new IllegalArgumentException("Lähtestamislink on aegunud. Palun taotlege uus.");
        }

        // Delete all vault attachments (MinIO files + DB records)
        List<VaultAttachment> attachments = vaultAttachmentRepository.findAllByUserId(user.getId());
        for (VaultAttachment att : attachments) {
            try {
                storageService.delete(att.getStorageKey());
            } catch (Exception e) {
                log.error("Failed to delete MinIO file during password reset: {}", att.getStorageKey(), e);
            }
        }
        vaultAttachmentRepository.deleteAll(attachments);

        // Delete all vault entries
        vaultEntryRepository.deleteAllByUserId(user.getId());

        // Update password and clear escrow (vault data is gone)
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiresAt(null);
        user.setEncryptedVaultKey(null);
        user.setVaultKeyEscrowedAt(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        // Revoke all existing sessions
        sessionService.revokeAllSessions(user.getId());

        log.info("Password reset completed for: {} (vault data cleared)", user.getEmail());
        return buildAuthResponseWithSession(user, ip, userAgent);
    }

    @Transactional
    public AuthResponse changePassword(User user, String currentPassword, String newPassword, String ip, String userAgent) {
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Praegune parool on vale");
        }

        String oldSalt = user.getEncryptionSalt();
        // Generate new salt (migrates legacy NULL salt users to per-user salt)
        String newSalt = EncryptionService.generateSalt();

        // Re-encrypt all vault entries
        List<VaultEntry> entries = vaultEntryRepository.findAllByUserId(user.getId());
        for (VaultEntry entry : entries) {
            // Decrypt with old password + old salt
            String decTitle = encryptionService.decrypt(entry.getTitle(), entry.getTitleIv(), currentPassword, oldSalt);
            String decData = encryptionService.decrypt(entry.getEncryptedData(), entry.getEncryptionIv(), currentPassword, oldSalt);

            // Re-encrypt with new password + new salt
            EncryptionService.EncryptionResult titleEnc = encryptionService.encrypt(decTitle, newPassword, newSalt);
            EncryptionService.EncryptionResult dataEnc = encryptionService.encrypt(decData, newPassword, newSalt);

            entry.setTitle(titleEnc.ciphertext());
            entry.setTitleIv(titleEnc.iv());
            entry.setEncryptedData(dataEnc.ciphertext());
            entry.setEncryptionIv(dataEnc.iv());

            // Re-encrypt notes if present
            if (entry.getNotesEncrypted() != null && entry.getNotesIv() != null) {
                String decNotes = encryptionService.decrypt(entry.getNotesEncrypted(), entry.getNotesIv(), currentPassword, oldSalt);
                EncryptionService.EncryptionResult notesEnc = encryptionService.encrypt(decNotes, newPassword, newSalt);
                entry.setNotesEncrypted(notesEnc.ciphertext());
                entry.setNotesIv(notesEnc.iv());
            }

            vaultEntryRepository.save(entry);
        }

        // Re-encrypt all attachments (file names + MinIO file content)
        List<VaultAttachment> attachments = vaultAttachmentRepository.findAllByUserId(user.getId());
        for (VaultAttachment att : attachments) {
            // Re-encrypt file name
            String decFileName = encryptionService.decrypt(att.getFileNameEncrypted(), att.getFileNameIv(), currentPassword, oldSalt);
            EncryptionService.EncryptionResult fileNameEnc = encryptionService.encrypt(decFileName, newPassword, newSalt);
            att.setFileNameEncrypted(fileNameEnc.ciphertext());
            att.setFileNameIv(fileNameEnc.iv());

            // Re-encrypt file content in MinIO (IV is prepended: first 12 bytes)
            try {
                var inputStream = storageService.download(att.getStorageKey());
                byte[] combined = inputStream.readAllBytes();
                inputStream.close();

                byte[] iv = java.util.Arrays.copyOfRange(combined, 0, 12);
                byte[] cipherBytes = java.util.Arrays.copyOfRange(combined, 12, combined.length);
                String ivBase64 = java.util.Base64.getEncoder().encodeToString(iv);

                // Decrypt with old password + old salt
                byte[] plainBytes = encryptionService.decryptBytes(cipherBytes, ivBase64, currentPassword, oldSalt);

                // Re-encrypt with new password + new salt
                EncryptionService.EncryptionResultBytes reEnc = encryptionService.encryptBytes(plainBytes, newPassword, newSalt);
                byte[] newIv = java.util.Base64.getDecoder().decode(reEnc.iv());
                byte[] newCombined = java.nio.ByteBuffer.allocate(12 + reEnc.cipherBytes().length)
                        .put(newIv)
                        .put(reEnc.cipherBytes())
                        .array();

                storageService.upload(att.getStorageKey(), newCombined, "application/octet-stream");
            } catch (Exception e) {
                log.error("Failed to re-encrypt attachment file during password change: {}", att.getId(), e);
            }

            vaultAttachmentRepository.save(att);
        }

        // Update password hash and salt
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setEncryptionSalt(newSalt);
        userRepository.save(user);

        // Re-escrow with new password
        vaultEscrowService.escrowVaultKey(user, newPassword);

        // Revoke all sessions and create fresh one
        sessionService.revokeAllSessions(user.getId());

        log.info("Password changed for: {} ({} entries re-encrypted)", user.getEmail(), entries.size());
        return buildAuthResponseWithSession(user, ip, userAgent);
    }

    private AuthResponse buildAuthResponseWithSession(User user, String ip, String userAgent) {
        String refreshToken = jwtService.generateRefreshToken(user.getId());
        UserSession session = sessionService.createSession(user, refreshToken, ip, userAgent);
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), session.getId());

        return new AuthResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                accessToken,
                refreshToken,
                user.isEmailVerified()
        );
    }

}
