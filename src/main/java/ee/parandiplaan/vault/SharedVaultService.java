package ee.parandiplaan.vault;

import ee.parandiplaan.audit.AuditService;
import ee.parandiplaan.notification.EmailService;
import ee.parandiplaan.trust.HandoverRequest;
import ee.parandiplaan.trust.SharedVaultToken;
import ee.parandiplaan.trust.SharedVaultTokenRepository;
import ee.parandiplaan.trust.TrustedContact;
import ee.parandiplaan.user.User;
import ee.parandiplaan.vault.dto.AttachmentResponse;
import ee.parandiplaan.vault.dto.VaultEntryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SharedVaultService {

    private final SharedVaultTokenRepository tokenRepository;
    private final VaultEntryRepository entryRepository;
    private final VaultAttachmentRepository attachmentRepository;
    private final VaultEscrowService vaultEscrowService;
    private final EncryptionService encryptionService;
    private final StorageService storageService;
    private final EmailService emailService;
    private final AuditService auditService;

    @Value("${app.shared-vault.token-expiry-days:30}")
    private int tokenExpiryDays;

    @Value("${app.url:http://localhost:8080}")
    private String appUrl;

    private static final SecureRandom secureRandom = new SecureRandom();
    private static final int IV_LENGTH = 12;

    @Transactional
    public String createSharedAccess(HandoverRequest handover) {
        User user = handover.getUser();
        TrustedContact contact = handover.getTrustedContact();

        if (!vaultEscrowService.hasEscrowedKey(user)) {
            throw new IllegalStateException("No escrowed vault key — shared access cannot be created");
        }

        // Check for existing active token for this handover + contact
        Optional<SharedVaultToken> existing = tokenRepository
                .findByHandoverRequestIdAndTrustedContactId(handover.getId(), contact.getId());
        if (existing.isPresent() && existing.get().isValid()) {
            log.info("Active shared token already exists for handover {} and contact {}", handover.getId(), contact.getId());
            return null; // Already has active access
        }

        // Generate raw token
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = HexFormat.of().formatHex(tokenBytes);
        String tokenHash = sha256(rawToken);

        SharedVaultToken token = new SharedVaultToken();
        token.setHandoverRequest(handover);
        token.setTrustedContact(contact);
        token.setUser(user);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(Instant.now().plus(tokenExpiryDays, ChronoUnit.DAYS));
        tokenRepository.save(token);

        auditService.log(user, "SHARED_ACCESS_CREATED", contact.getFullName());
        log.info("Shared vault access created for user {} → contact {}", user.getEmail(), contact.getEmail());

        return rawToken;
    }

    public void sendSharedAccessEmail(TrustedContact contact, String ownerName, String rawToken) {
        String sharedUrl = appUrl + "/jagatud-tresor.html?token=" + rawToken;
        emailService.sendSharedVaultEmail(contact.getEmail(), contact.getFullName(), ownerName, sharedUrl);
    }

    @Transactional
    public SharedVaultToken validateAndTouch(String rawToken) {
        String tokenHash = sha256(rawToken);
        SharedVaultToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Vigane või aegunud jagamislink"));

        if (!token.isValid()) {
            throw new IllegalArgumentException("Jagamislink on aegunud või tühistatud");
        }

        token.setLastAccessedAt(Instant.now());
        token.setAccessCount(token.getAccessCount() + 1);
        tokenRepository.save(token);

        return token;
    }

    @Transactional(readOnly = true)
    public List<VaultEntryResponse> listSharedEntries(SharedVaultToken token) {
        User user = token.getUser();
        String vaultKey = vaultEscrowService.recoverVaultKey(user);
        String salt = user.getEncryptionSalt();

        List<VaultEntry> entries = entryRepository.findByUserIdOrderByCreatedAtDesc(user.getId());

        // Apply access scoping
        TrustedContact contact = token.getTrustedContact();
        if ("LIMITED".equals(contact.getAccessLevel()) && contact.getAllowedCategories() != null) {
            Set<UUID> allowed = Set.of(contact.getAllowedCategories());
            entries = entries.stream()
                    .filter(e -> allowed.contains(e.getCategory().getId()))
                    .toList();
        }

        return entries.stream()
                .map(e -> decryptEntry(e, vaultKey, salt))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> listSharedAttachments(SharedVaultToken token, UUID entryId) {
        User user = token.getUser();

        // Verify entry belongs to user and is accessible
        VaultEntry entry = entryRepository.findByIdAndUserId(entryId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Kirjet ei leitud"));

        // Check access scoping
        TrustedContact contact = token.getTrustedContact();
        if ("LIMITED".equals(contact.getAccessLevel()) && contact.getAllowedCategories() != null) {
            Set<UUID> allowed = Set.of(contact.getAllowedCategories());
            if (!allowed.contains(entry.getCategory().getId())) {
                throw new IllegalArgumentException("Ligipääs sellele kirjele on piiratud");
            }
        }

        String vaultKey = vaultEscrowService.recoverVaultKey(user);
        String salt = user.getEncryptionSalt();

        return attachmentRepository.findByVaultEntryIdOrderByCreatedAtDesc(entryId)
                .stream()
                .map(a -> decryptAttachmentMeta(a, vaultKey, salt))
                .toList();
    }

    @Transactional(readOnly = true)
    public VaultAttachmentService.DownloadResult downloadSharedAttachment(SharedVaultToken token, UUID attachmentId) {
        User user = token.getUser();

        VaultAttachment attachment = attachmentRepository.findByIdAndUserId(attachmentId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Manust ei leitud"));

        // Check access scoping on the parent entry
        VaultEntry entry = attachment.getVaultEntry();
        TrustedContact contact = token.getTrustedContact();
        if ("LIMITED".equals(contact.getAccessLevel()) && contact.getAllowedCategories() != null) {
            Set<UUID> allowed = Set.of(contact.getAllowedCategories());
            if (!allowed.contains(entry.getCategory().getId())) {
                throw new IllegalArgumentException("Ligipääs sellele manusele on piiratud");
            }
        }

        String vaultKey = vaultEscrowService.recoverVaultKey(user);
        String salt = user.getEncryptionSalt();

        try {
            InputStream stream = storageService.download(attachment.getStorageKey());
            byte[] combined = stream.readAllBytes();
            stream.close();

            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] cipherBytes = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);
            String ivBase64 = Base64.getEncoder().encodeToString(iv);

            byte[] plainBytes = encryptionService.decryptBytes(cipherBytes, ivBase64, vaultKey, salt);
            String fileName = encryptionService.decrypt(
                    attachment.getFileNameEncrypted(), attachment.getFileNameIv(), vaultKey, salt);

            return new VaultAttachmentService.DownloadResult(plainBytes, fileName, attachment.getMimeType());
        } catch (Exception e) {
            throw new RuntimeException("Faili allalaadimine ebaõnnestus", e);
        }
    }

    @Transactional(readOnly = true)
    public List<SharedVaultToken> listActiveTokens(User user) {
        return tokenRepository.findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(user.getId());
    }

    @Transactional
    public void revokeToken(User user, UUID tokenId) {
        SharedVaultToken token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new IllegalArgumentException("Tokenit ei leitud"));

        if (!token.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Tokenit ei leitud");
        }

        token.setRevokedAt(Instant.now());
        tokenRepository.save(token);
        auditService.log(user, "SHARED_ACCESS_REVOKED", token.getTrustedContact().getFullName());
    }

    private VaultEntryResponse decryptEntry(VaultEntry entry, String vaultKey, String salt) {
        String decryptedTitle = encryptionService.decrypt(entry.getTitle(), entry.getTitleIv(), vaultKey, salt);
        String decryptedData = encryptionService.decrypt(entry.getEncryptedData(), entry.getEncryptionIv(), vaultKey, salt);

        String decryptedNotes = null;
        if (entry.getNotesEncrypted() != null && entry.getNotesIv() != null) {
            decryptedNotes = encryptionService.decrypt(entry.getNotesEncrypted(), entry.getNotesIv(), vaultKey, salt);
        }

        VaultCategory cat = entry.getCategory();
        return new VaultEntryResponse(
                entry.getId(), cat.getId(), cat.getSlug(), cat.getIcon(),
                decryptedTitle, decryptedData, decryptedNotes,
                entry.isComplete(), entry.isHasAttachments(),
                entry.getReminderDate(), entry.getLastReviewedAt(),
                entry.getCreatedAt(), entry.getUpdatedAt()
        );
    }

    private AttachmentResponse decryptAttachmentMeta(VaultAttachment attachment, String vaultKey, String salt) {
        String fileName = encryptionService.decrypt(
                attachment.getFileNameEncrypted(), attachment.getFileNameIv(), vaultKey, salt);
        return new AttachmentResponse(
                attachment.getId(), attachment.getVaultEntry().getId(),
                fileName, attachment.getFileSizeBytes(), attachment.getMimeType(),
                attachment.getCreatedAt()
        );
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 hashing failed", e);
        }
    }
}
