package ee.parandiplaan.vault;

import ee.parandiplaan.user.User;
import ee.parandiplaan.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

@Service
@Slf4j
public class VaultEscrowService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;
    private static final SecureRandom secureRandom = new SecureRandom();

    private final UserRepository userRepository;
    private final byte[] masterKey;

    public VaultEscrowService(
            UserRepository userRepository,
            @Value("${app.vault-escrow.master-key:DevEscrowKeyThatIsAtLeast32BytesLong!!}") String masterKeyStr) {
        this.userRepository = userRepository;
        this.masterKey = deriveMasterKey(masterKeyStr);
    }

    @Transactional
    public void escrowVaultKey(User user, String vaultKey) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            SecretKey key = new SecretKeySpec(masterKey, "AES");
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] ciphertext = cipher.doFinal(vaultKey.getBytes(StandardCharsets.UTF_8));

            // Combine IV + ciphertext and encode as Base64
            byte[] combined = new byte[IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(ciphertext, 0, combined, IV_LENGTH, ciphertext.length);

            user.setEncryptedVaultKey(Base64.getEncoder().encodeToString(combined));
            user.setVaultKeyEscrowedAt(Instant.now());
            userRepository.save(user);

            log.debug("Vault key escrowed for user: {}", user.getEmail());
        } catch (Exception e) {
            log.error("Failed to escrow vault key for user {}: {}", user.getEmail(), e.getMessage());
            throw new RuntimeException("Vault key escrow failed", e);
        }
    }

    public String recoverVaultKey(User user) {
        if (user.getEncryptedVaultKey() == null) {
            throw new IllegalStateException("No escrowed vault key for user: " + user.getEmail());
        }

        try {
            byte[] combined = Base64.getDecoder().decode(user.getEncryptedVaultKey());
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);

            SecretKey key = new SecretKeySpec(masterKey, "AES");
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to recover vault key for user {}: {}", user.getEmail(), e.getMessage());
            throw new RuntimeException("Vault key recovery failed", e);
        }
    }

    public boolean hasEscrowedKey(User user) {
        return user.getEncryptedVaultKey() != null;
    }

    private static byte[] deriveMasterKey(String masterKeyStr) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(masterKeyStr.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive master key", e);
        }
    }
}
