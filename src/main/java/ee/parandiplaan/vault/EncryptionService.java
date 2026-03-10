package ee.parandiplaan.vault;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;
    private static final int KEY_LENGTH = 256;
    private static final int PBKDF2_ITERATIONS = 100_000;
    private static final String KEY_ALGORITHM = "PBKDF2WithHmacSHA256";
    static final String STATIC_SALT = "parandiplaan-vault-salt";

    private static final SecureRandom secureRandom = new SecureRandom();

    // --- Salt generation ---

    public static String generateSalt() {
        byte[] saltBytes = new byte[32];
        secureRandom.nextBytes(saltBytes);
        return HexFormat.of().formatHex(saltBytes);
    }

    // --- Backward-compatible 2-param methods (use static salt) ---

    public EncryptionResult encrypt(String plaintext, String userKey) {
        return encrypt(plaintext, userKey, null);
    }

    public String decrypt(String ciphertext, String iv, String userKey) {
        return decrypt(ciphertext, iv, userKey, null);
    }

    public EncryptionResultBytes encryptBytes(byte[] plainBytes, String userKey) {
        return encryptBytes(plainBytes, userKey, null);
    }

    public byte[] decryptBytes(byte[] cipherBytes, String iv, String userKey) {
        return decryptBytes(cipherBytes, iv, userKey, null);
    }

    // --- Per-user salt methods ---

    public EncryptionResult encrypt(String plaintext, String userKey, String salt) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            SecretKey key = deriveKey(userKey, salt);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes("UTF-8"));

            return new EncryptionResult(
                    Base64.getEncoder().encodeToString(ciphertext),
                    Base64.getEncoder().encodeToString(iv)
            );
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String ciphertext, String iv, String userKey, String salt) {
        try {
            SecretKey key = deriveKey(userKey, salt);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(GCM_TAG_LENGTH, Base64.getDecoder().decode(iv)));

            byte[] plaintext = cipher.doFinal(Base64.getDecoder().decode(ciphertext));
            return new String(plaintext, "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    public EncryptionResultBytes encryptBytes(byte[] plainBytes, String userKey, String salt) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            SecretKey key = deriveKey(userKey, salt);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] cipherBytes = cipher.doFinal(plainBytes);

            return new EncryptionResultBytes(cipherBytes, Base64.getEncoder().encodeToString(iv));
        } catch (Exception e) {
            throw new RuntimeException("Byte encryption failed", e);
        }
    }

    public byte[] decryptBytes(byte[] cipherBytes, String iv, String userKey, String salt) {
        try {
            SecretKey key = deriveKey(userKey, salt);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(GCM_TAG_LENGTH, Base64.getDecoder().decode(iv)));

            return cipher.doFinal(cipherBytes);
        } catch (Exception e) {
            throw new RuntimeException("Byte decryption failed", e);
        }
    }

    private SecretKey deriveKey(String userKey, String salt) throws Exception {
        String effectiveSalt = (salt != null) ? salt : STATIC_SALT;
        KeySpec spec = new PBEKeySpec(userKey.toCharArray(), effectiveSalt.getBytes("UTF-8"), PBKDF2_ITERATIONS, KEY_LENGTH);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_ALGORITHM);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    public record EncryptionResult(String ciphertext, String iv) {}

    public record EncryptionResultBytes(byte[] cipherBytes, String iv) {}
}
