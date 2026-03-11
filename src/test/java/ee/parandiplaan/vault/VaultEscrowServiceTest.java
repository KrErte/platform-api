package ee.parandiplaan.vault;

import ee.parandiplaan.user.User;
import ee.parandiplaan.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VaultEscrowServiceTest {

    private VaultEscrowService escrowService;
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        escrowService = new VaultEscrowService(userRepository, "TestEscrowMasterKeyThatIsAtLeast32BytesLong!!");
    }

    @Test
    void escrowAndRecoverRoundtrip() {
        User user = new User();
        user.setEmail("test@example.com");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        String vaultKey = "MySecretVaultPassword123";
        escrowService.escrowVaultKey(user, vaultKey);

        assertNotNull(user.getEncryptedVaultKey());
        assertNotNull(user.getVaultKeyEscrowedAt());

        String recovered = escrowService.recoverVaultKey(user);
        assertEquals(vaultKey, recovered);
    }

    @Test
    void recoverWithoutEscrowThrowsException() {
        User user = new User();
        user.setEmail("nokey@example.com");
        user.setEncryptedVaultKey(null);

        assertThrows(IllegalStateException.class, () ->
                escrowService.recoverVaultKey(user));
    }

    @Test
    void hasEscrowedKeyReturnsFalseWhenNull() {
        User user = new User();
        user.setEncryptedVaultKey(null);

        assertFalse(escrowService.hasEscrowedKey(user));
    }

    @Test
    void hasEscrowedKeyReturnsTrueWhenPresent() {
        User user = new User();
        user.setEmail("present@example.com");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        escrowService.escrowVaultKey(user, "someKey");

        assertTrue(escrowService.hasEscrowedKey(user));
    }

    @Test
    void differentMasterKeyCannotRecover() {
        User user = new User();
        user.setEmail("diffkey@example.com");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        escrowService.escrowVaultKey(user, "MyVaultKey");

        // Create a service with different master key
        VaultEscrowService otherService = new VaultEscrowService(userRepository, "CompletelyDifferentMasterKey!!!!!");

        assertThrows(RuntimeException.class, () ->
                otherService.recoverVaultKey(user));
    }

    @Test
    void escrowOverwritesPreviousKey() {
        User user = new User();
        user.setEmail("overwrite@example.com");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        escrowService.escrowVaultKey(user, "OldPassword");
        String oldEncrypted = user.getEncryptedVaultKey();

        escrowService.escrowVaultKey(user, "NewPassword");
        String newEncrypted = user.getEncryptedVaultKey();

        assertNotEquals(oldEncrypted, newEncrypted);
        assertEquals("NewPassword", escrowService.recoverVaultKey(user));
    }
}
