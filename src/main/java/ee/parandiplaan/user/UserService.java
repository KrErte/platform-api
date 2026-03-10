package ee.parandiplaan.user;

import ee.parandiplaan.audit.AuditService;
import ee.parandiplaan.notification.EmailService;
import ee.parandiplaan.vault.StorageService;
import ee.parandiplaan.vault.VaultAttachment;
import ee.parandiplaan.vault.VaultAttachmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VaultAttachmentRepository vaultAttachmentRepository;
    private final StorageService storageService;
    private final AuditService auditService;
    private final EmailService emailService;

    @Transactional
    public void deleteAccount(User user, String password) {
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Vale parool");
        }

        // Delete MinIO files
        List<VaultAttachment> attachments = vaultAttachmentRepository.findAllByUserId(user.getId());
        for (VaultAttachment attachment : attachments) {
            storageService.delete(attachment.getStorageKey());
        }

        // Audit log (before deleting user so the user reference is still valid)
        auditService.log(user, "ACCOUNT_DELETED", "Konto kustutatud");

        String email = user.getEmail();
        String fullName = user.getFullName();

        // Delete user — child data cascades via ON DELETE CASCADE
        userRepository.delete(user);

        // Send confirmation email
        emailService.sendAccountDeletedEmail(email, fullName);

        log.info("Account deleted for user: {}", email);
    }
}
