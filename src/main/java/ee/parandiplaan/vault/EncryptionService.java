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

@Service
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;
    private static final int KEY_LENGTH = 256;
    private static final int PBKDF2_ITERATIONS = 100_000;
    private static final String KEY_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String SALT = "parandiplaan-vault-salt"; // MVP: static salt, v2: per-user salt

    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptionResult encrypt(String plaintext, String userKey) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            SecretKey key = deriveKey(userKey);
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

    public String decrypt(String ciphertext, String iv, String userKey) {
        try {
            SecretKey key = deriveKey(userKey);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(GCM_TAG_LENGTH, Base64.getDecoder().decode(iv)));

            byte[] plaintext = cipher.doFinal(Base64.getDecoder().decode(ciphertext));
            return new String(plaintext, "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    private SecretKey deriveKey(String userKey) throws Exception {
        KeySpec spec = new PBEKeySpec(userKey.toCharArray(), SALT.getBytes("UTF-8"), PBKDF2_ITERATIONS, KEY_LENGTH);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_ALGORITHM);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    public EncryptionResultBytes encryptBytes(byte[] plainBytes, String userKey) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            SecretKey key = deriveKey(userKey);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] cipherBytes = cipher.doFinal(plainBytes);

            return new EncryptionResultBytes(cipherBytes, Base64.getEncoder().encodeToString(iv));
        } catch (Exception e) {
            throw new RuntimeException("Byte encryption failed", e);
        }
    }

    public byte[] decryptBytes(byte[] cipherBytes, String iv, String userKey) {
        try {
            SecretKey key = deriveKey(userKey);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(GCM_TAG_LENGTH, Base64.getDecoder().decode(iv)));

            return cipher.doFinal(cipherBytes);
        } catch (Exception e) {
            throw new RuntimeException("Byte decryption failed", e);
        }
    }

    public record EncryptionResult(String ciphertext, String iv) {}

    public record EncryptionResultBytes(byte[] cipherBytes, String iv) {}
}
