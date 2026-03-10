package ee.parandiplaan.vault;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionServiceTest {

    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService();
    }

    @Test
    void encryptDecryptRoundtrip() {
        String plaintext = "Tere, maailm! Hello, world!";
        String key = "testpassword123";

        EncryptionService.EncryptionResult result = encryptionService.encrypt(plaintext, key);

        assertNotNull(result.ciphertext());
        assertNotNull(result.iv());
        assertNotEquals(plaintext, result.ciphertext());

        String decrypted = encryptionService.decrypt(result.ciphertext(), result.iv(), key);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void encryptDecryptBytesRoundtrip() {
        byte[] plainBytes = "Binary test data öäüõ".getBytes();
        String key = "bytespassword456";

        EncryptionService.EncryptionResultBytes result = encryptionService.encryptBytes(plainBytes, key);

        assertNotNull(result.cipherBytes());
        assertNotNull(result.iv());

        byte[] decrypted = encryptionService.decryptBytes(result.cipherBytes(), result.iv(), key);
        assertArrayEquals(plainBytes, decrypted);
    }

    @Test
    void differentKeysProduceDifferentCiphertext() {
        String plaintext = "Same plaintext";
        String key1 = "password1";
        String key2 = "password2";

        EncryptionService.EncryptionResult result1 = encryptionService.encrypt(plaintext, key1);
        EncryptionService.EncryptionResult result2 = encryptionService.encrypt(plaintext, key2);

        assertNotEquals(result1.ciphertext(), result2.ciphertext());
    }

    @Test
    void wrongKeyFailsDecryption() {
        String plaintext = "Secret message";
        String correctKey = "correctKey";
        String wrongKey = "wrongKey";

        EncryptionService.EncryptionResult result = encryptionService.encrypt(plaintext, correctKey);

        assertThrows(RuntimeException.class, () ->
                encryptionService.decrypt(result.ciphertext(), result.iv(), wrongKey));
    }

    @Test
    void perUserSaltProducesDifferentCiphertext() {
        String plaintext = "Same plaintext";
        String key = "samepassword";
        String salt1 = "salt1111111111111111111111111111";
        String salt2 = "salt2222222222222222222222222222";

        EncryptionService.EncryptionResult result1 = encryptionService.encrypt(plaintext, key, salt1);
        EncryptionService.EncryptionResult result2 = encryptionService.encrypt(plaintext, key, salt2);

        // Same key + different salt = can't decrypt with wrong salt
        String decrypted1 = encryptionService.decrypt(result1.ciphertext(), result1.iv(), key, salt1);
        assertEquals(plaintext, decrypted1);

        assertThrows(RuntimeException.class, () ->
                encryptionService.decrypt(result1.ciphertext(), result1.iv(), key, salt2));
    }

    @Test
    void nullSaltFallsBackToStaticSalt() {
        String plaintext = "Backward compat test";
        String key = "legacypassword";

        // Encrypt with explicit null salt (should use static)
        EncryptionService.EncryptionResult result = encryptionService.encrypt(plaintext, key, null);

        // Decrypt with 2-param method (also uses static salt)
        String decrypted = encryptionService.decrypt(result.ciphertext(), result.iv(), key);
        assertEquals(plaintext, decrypted);

        // And vice versa
        EncryptionService.EncryptionResult result2 = encryptionService.encrypt(plaintext, key);
        String decrypted2 = encryptionService.decrypt(result2.ciphertext(), result2.iv(), key, null);
        assertEquals(plaintext, decrypted2);
    }

    @Test
    void generateSaltProducesUniqueValues() {
        String salt1 = EncryptionService.generateSalt();
        String salt2 = EncryptionService.generateSalt();

        assertNotNull(salt1);
        assertNotNull(salt2);
        assertEquals(64, salt1.length()); // 32 bytes = 64 hex chars
        assertEquals(64, salt2.length());
        assertNotEquals(salt1, salt2);
    }

    @Test
    void encryptDecryptWithPerUserSaltRoundtrip() {
        String plaintext = "Per-user salt roundtrip";
        String key = "userpassword";
        String salt = EncryptionService.generateSalt();

        EncryptionService.EncryptionResult result = encryptionService.encrypt(plaintext, key, salt);
        String decrypted = encryptionService.decrypt(result.ciphertext(), result.iv(), key, salt);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void encryptDecryptBytesWithSaltRoundtrip() {
        byte[] plainBytes = "Byte salt test".getBytes();
        String key = "bytekey";
        String salt = EncryptionService.generateSalt();

        EncryptionService.EncryptionResultBytes result = encryptionService.encryptBytes(plainBytes, key, salt);
        byte[] decrypted = encryptionService.decryptBytes(result.cipherBytes(), result.iv(), key, salt);
        assertArrayEquals(plainBytes, decrypted);
    }
}
