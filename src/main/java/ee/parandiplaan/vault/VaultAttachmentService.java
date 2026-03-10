package ee.parandiplaan.vault;

import ee.parandiplaan.subscription.Subscription;
import ee.parandiplaan.subscription.SubscriptionRepository;
import ee.parandiplaan.user.User;
import ee.parandiplaan.vault.dto.AttachmentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VaultAttachmentService {

    private final VaultAttachmentRepository attachmentRepository;
    private final VaultEntryRepository entryRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final EncryptionService encryptionService;
    private final StorageService storageService;

    private static final long TRIAL_STORAGE_LIMIT = 100L * 1024 * 1024;      // 100 MB
    private static final long PLUS_STORAGE_LIMIT = 5L * 1024 * 1024 * 1024;  // 5 GB
    private static final long FAMILY_STORAGE_LIMIT = 25L * 1024 * 1024 * 1024; // 25 GB
    private static final int IV_LENGTH = 12;

    @Transactional
    public AttachmentResponse upload(User user, UUID entryId, MultipartFile file, String encryptionKey) {
        VaultEntry entry = entryRepository.findByIdAndUserId(entryId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Kirjet ei leitud"));

        checkStorageLimit(user, file.getSize());

        try {
            byte[] plainBytes = file.getBytes();

            // Encrypt file content (IV prepended to ciphertext)
            String salt = user.getEncryptionSalt();
            EncryptionService.EncryptionResultBytes encResult = encryptionService.encryptBytes(plainBytes, encryptionKey, salt);
            byte[] iv = java.util.Base64.getDecoder().decode(encResult.iv());
            byte[] combined = ByteBuffer.allocate(IV_LENGTH + encResult.cipherBytes().length)
                    .put(iv)
                    .put(encResult.cipherBytes())
                    .array();

            // Encrypt file name
            EncryptionService.EncryptionResult nameEnc = encryptionService.encrypt(
                    file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown", encryptionKey, salt);

            // Generate storage key
            String storageKey = user.getId() + "/" + entryId + "/" + UUID.randomUUID();

            // Upload encrypted content to MinIO
            storageService.upload(storageKey, combined, "application/octet-stream");

            // Save attachment record
            VaultAttachment attachment = new VaultAttachment();
            attachment.setVaultEntry(entry);
            attachment.setUser(user);
            attachment.setFileNameEncrypted(nameEnc.ciphertext());
            attachment.setFileNameIv(nameEnc.iv());
            attachment.setStorageKey(storageKey);
            attachment.setFileSizeBytes(file.getSize());
            attachment.setMimeType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
            attachment = attachmentRepository.save(attachment);

            // Update hasAttachments flag
            entry.setHasAttachments(true);
            entryRepository.save(entry);

            return toResponse(attachment, encryptionKey, salt);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Faili üleslaadimine ebaõnnestus", e);
        }
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> listAttachments(User user, UUID entryId, String encryptionKey) {
        entryRepository.findByIdAndUserId(entryId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Kirjet ei leitud"));

        String salt = user.getEncryptionSalt();
        return attachmentRepository.findByVaultEntryIdOrderByCreatedAtDesc(entryId)
                .stream()
                .map(a -> toResponse(a, encryptionKey, salt))
                .toList();
    }

    @Transactional(readOnly = true)
    public DownloadResult download(User user, UUID attachmentId, String encryptionKey) {
        VaultAttachment attachment = attachmentRepository.findByIdAndUserId(attachmentId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Manust ei leitud"));

        try {
            // Download encrypted content from MinIO
            InputStream stream = storageService.download(attachment.getStorageKey());
            byte[] combined = stream.readAllBytes();
            stream.close();

            // Extract IV (first 12 bytes) and ciphertext
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] cipherBytes = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);
            String ivBase64 = java.util.Base64.getEncoder().encodeToString(iv);

            // Decrypt file content
            String salt = user.getEncryptionSalt();
            byte[] plainBytes = encryptionService.decryptBytes(cipherBytes, ivBase64, encryptionKey, salt);

            // Decrypt file name
            String fileName = encryptionService.decrypt(
                    attachment.getFileNameEncrypted(), attachment.getFileNameIv(), encryptionKey, salt);

            return new DownloadResult(plainBytes, fileName, attachment.getMimeType());
        } catch (Exception e) {
            throw new RuntimeException("Faili allalaadimine ebaõnnestus", e);
        }
    }

    @Transactional
    public void delete(User user, UUID attachmentId) {
        VaultAttachment attachment = attachmentRepository.findByIdAndUserId(attachmentId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Manust ei leitud"));

        UUID entryId = attachment.getVaultEntry().getId();

        // Delete from MinIO (logs error but doesn't throw)
        storageService.delete(attachment.getStorageKey());

        // Delete from DB
        attachmentRepository.delete(attachment);

        // Update hasAttachments flag
        long remaining = attachmentRepository.countByVaultEntryId(entryId);
        if (remaining == 0) {
            entryRepository.findById(entryId).ifPresent(entry -> {
                entry.setHasAttachments(false);
                entryRepository.save(entry);
            });
        }
    }

    private void checkStorageLimit(User user, long newFileSize) {
        Subscription sub = subscriptionRepository.findByUserId(user.getId()).orElse(null);
        String plan = (sub != null) ? sub.getPlan() : "NONE";

        long limit;
        if ("PLUS".equals(plan)) {
            limit = PLUS_STORAGE_LIMIT;
        } else if ("FAMILY".equals(plan)) {
            limit = FAMILY_STORAGE_LIMIT;
        } else if ("TRIAL".equals(plan) && sub != null) {
            if (sub.isTrialExpired()) {
                sub.setPlan("NONE");
                subscriptionRepository.save(sub);
                throw new IllegalStateException(
                        "Sinu prooviperiood on lõppenud. Vali Plus või Perekond plaan jätkamiseks!");
            }
            limit = TRIAL_STORAGE_LIMIT;
        } else {
            throw new IllegalStateException(
                    "Failide üleslaadimine nõuab aktiivset tellimust. Vali Plus või Perekond plaan!");
        }

        long currentUsage = attachmentRepository.sumFileSizeBytesByUserId(user.getId());
        if (currentUsage + newFileSize > limit) {
            String limitStr = limit >= 1024L * 1024 * 1024
                    ? (limit / (1024L * 1024 * 1024)) + " GB"
                    : (limit / (1024L * 1024)) + " MB";
            throw new IllegalStateException(
                    "Salvestuslimiit (" + limitStr + ") on täis. Uuenda oma plaani või kustuta vanu faile!");
        }
    }

    private AttachmentResponse toResponse(VaultAttachment attachment, String encryptionKey, String salt) {
        String fileName = encryptionService.decrypt(
                attachment.getFileNameEncrypted(), attachment.getFileNameIv(), encryptionKey, salt);
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getVaultEntry().getId(),
                fileName,
                attachment.getFileSizeBytes(),
                attachment.getMimeType(),
                attachment.getCreatedAt()
        );
    }

    public record DownloadResult(byte[] data, String fileName, String mimeType) {}
}
