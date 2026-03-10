package ee.parandiplaan.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.samstevens.totp.code.*;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import ee.parandiplaan.user.User;
import ee.parandiplaan.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TotpService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    private static final String ISSUER = "Pärandiplaan";
    private static final String BACKUP_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int BACKUP_CODE_LENGTH = 8;
    private static final int BACKUP_CODE_COUNT = 8;
    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final SecureRandom secureRandom = new SecureRandom();

    public TotpSetupResponse setup(User user) {
        if (user.isTotpEnabled()) {
            throw new IllegalStateException("2FA on juba aktiveeritud");
        }

        String secret = secretGenerator.generate();

        // Save secret (not yet enabled until user verifies with a code)
        user.setTotpSecret(secret);
        userRepository.save(user);

        String qrCodeDataUri = generateQrCodeDataUri(user.getEmail(), secret);

        return new TotpSetupResponse(secret, qrCodeDataUri);
    }

    @Transactional
    public void enable(User user, String code) {
        if (user.isTotpEnabled()) {
            throw new IllegalStateException("2FA on juba aktiveeritud");
        }

        if (user.getTotpSecret() == null) {
            throw new IllegalStateException("2FA seadistamine pole alustatud. Kasuta esmalt /2fa/setup");
        }

        if (!verifyCode(user.getTotpSecret(), code)) {
            throw new IllegalArgumentException("Vale 2FA kood");
        }

        user.setTotpEnabled(true);
        userRepository.save(user);
        log.info("2FA enabled for user: {}", user.getEmail());
    }

    @Transactional
    public void disable(User user, String code) {
        if (!user.isTotpEnabled()) {
            throw new IllegalStateException("2FA pole aktiveeritud");
        }

        if (!verifyCode(user.getTotpSecret(), code)) {
            throw new IllegalArgumentException("Vale 2FA kood");
        }

        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        user.setTotpBackupCodes(null);
        userRepository.save(user);
        log.info("2FA disabled for user: {}", user.getEmail());
    }

    public boolean validate(User user, String code) {
        if (!user.isTotpEnabled() || user.getTotpSecret() == null) {
            return true; // 2FA not enabled, skip validation
        }
        if (verifyCode(user.getTotpSecret(), code)) {
            return true;
        }
        // Try backup code
        return tryBackupCode(user, code);
    }

    @Transactional
    public List<String> generateBackupCodes(User user) {
        if (!user.isTotpEnabled()) {
            throw new IllegalStateException("2FA peab olema aktiveeritud enne varukoodide genereerimist");
        }

        List<String> plainCodes = new ArrayList<>();
        List<String> hashedCodes = new ArrayList<>();

        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            String code = generateRandomCode();
            plainCodes.add(code);
            hashedCodes.add(passwordEncoder.encode(code));
        }

        try {
            user.setTotpBackupCodes(objectMapper.writeValueAsString(hashedCodes));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Varukoodide salvestamine ebaõnnestus", e);
        }
        userRepository.save(user);

        log.info("Backup codes generated for user: {}", user.getEmail());
        return plainCodes;
    }

    private boolean tryBackupCode(User user, String code) {
        if (user.getTotpBackupCodes() == null || user.getTotpBackupCodes().isBlank()) {
            return false;
        }

        try {
            List<String> hashedCodes = objectMapper.readValue(
                    user.getTotpBackupCodes(), new TypeReference<List<String>>() {});

            for (int i = 0; i < hashedCodes.size(); i++) {
                if (passwordEncoder.matches(code.toUpperCase(), hashedCodes.get(i))) {
                    // Consume the code
                    hashedCodes.remove(i);
                    user.setTotpBackupCodes(objectMapper.writeValueAsString(hashedCodes));
                    userRepository.save(user);
                    log.info("Backup code used for user: {} ({} remaining)", user.getEmail(), hashedCodes.size());
                    return true;
                }
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to parse backup codes for user {}: {}", user.getEmail(), e.getMessage());
        }

        return false;
    }

    private String generateRandomCode() {
        StringBuilder sb = new StringBuilder(BACKUP_CODE_LENGTH);
        for (int i = 0; i < BACKUP_CODE_LENGTH; i++) {
            sb.append(BACKUP_CODE_CHARS.charAt(secureRandom.nextInt(BACKUP_CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private boolean verifyCode(String secret, String code) {
        TimeProvider timeProvider = new SystemTimeProvider();
        CodeGenerator codeGenerator = new DefaultCodeGenerator();
        CodeVerifier verifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
        return verifier.isValidCode(secret, code);
    }

    private String generateQrCodeDataUri(String email, String secret) {
        QrData data = new QrData.Builder()
                .label(email)
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();

        try {
            QrGenerator generator = new ZxingPngQrGenerator();
            byte[] imageData = generator.generate(data);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(imageData);
        } catch (QrGenerationException e) {
            log.error("Failed to generate QR code: {}", e.getMessage());
            throw new RuntimeException("QR koodi genereerimine ebaõnnestus");
        }
    }

    public record TotpSetupResponse(String secret, String qrCodeDataUri) {}

    public record BackupCodesResponse(List<String> codes) {}
}
