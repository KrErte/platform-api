package ee.parandiplaan.auth;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class TotpService {

    private final UserRepository userRepository;

    private static final String ISSUER = "Pärandiplaan";
    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();

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
        userRepository.save(user);
        log.info("2FA disabled for user: {}", user.getEmail());
    }

    public boolean validate(User user, String code) {
        if (!user.isTotpEnabled() || user.getTotpSecret() == null) {
            return true; // 2FA not enabled, skip validation
        }
        return verifyCode(user.getTotpSecret(), code);
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
}
