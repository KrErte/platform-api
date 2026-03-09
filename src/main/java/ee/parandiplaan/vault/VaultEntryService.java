package ee.parandiplaan.vault;

import ee.parandiplaan.progress.ProgressService;
import ee.parandiplaan.subscription.Subscription;
import ee.parandiplaan.subscription.SubscriptionRepository;
import ee.parandiplaan.user.User;
import ee.parandiplaan.vault.dto.CreateEntryRequest;
import ee.parandiplaan.vault.dto.UpdateEntryRequest;
import ee.parandiplaan.vault.dto.VaultEntryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VaultEntryService {

    private final VaultEntryRepository entryRepository;
    private final VaultCategoryRepository categoryRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final EncryptionService encryptionService;
    private final ProgressService progressService;

    private static final int TRIAL_ENTRY_LIMIT = 10;

    @Transactional(readOnly = true)
    public List<VaultEntryResponse> listEntries(User user, UUID categoryId, String encryptionKey) {
        List<VaultEntry> entries;
        if (categoryId != null) {
            entries = entryRepository.findByUserIdAndCategoryIdOrderByCreatedAtDesc(user.getId(), categoryId);
        } else {
            entries = entryRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        }
        return entries.stream().map(e -> toResponse(e, encryptionKey)).toList();
    }

    @Transactional(readOnly = true)
    public VaultEntryResponse getEntry(User user, UUID entryId, String encryptionKey) {
        VaultEntry entry = entryRepository.findByIdAndUserId(entryId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Kirjet ei leitud"));
        return toResponse(entry, encryptionKey);
    }

    @Transactional
    public VaultEntryResponse createEntry(User user, CreateEntryRequest request, String encryptionKey) {
        checkPlanLimit(user);

        VaultCategory category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new IllegalArgumentException("Kategooriat ei leitud"));

        EncryptionService.EncryptionResult titleEnc = encryptionService.encrypt(request.title(), encryptionKey);
        EncryptionService.EncryptionResult dataEnc = encryptionService.encrypt(request.data(), encryptionKey);

        VaultEntry entry = new VaultEntry();
        entry.setUser(user);
        entry.setCategory(category);
        entry.setTitle(titleEnc.ciphertext());
        entry.setTitleIv(titleEnc.iv());
        entry.setEncryptedData(dataEnc.ciphertext());
        entry.setEncryptionIv(dataEnc.iv());

        if (request.notes() != null && !request.notes().isBlank()) {
            EncryptionService.EncryptionResult notesEnc = encryptionService.encrypt(request.notes(), encryptionKey);
            entry.setNotesEncrypted(notesEnc.ciphertext());
            entry.setNotesIv(notesEnc.iv());
        }

        entry.setReminderDate(request.reminderDate());

        entry = entryRepository.save(entry);
        progressService.recalculate(user);
        return toResponse(entry, encryptionKey);
    }

    @Transactional
    public VaultEntryResponse updateEntry(User user, UUID entryId, UpdateEntryRequest request, String encryptionKey) {
        VaultEntry entry = entryRepository.findByIdAndUserId(entryId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Kirjet ei leitud"));

        EncryptionService.EncryptionResult titleEnc = encryptionService.encrypt(request.title(), encryptionKey);
        EncryptionService.EncryptionResult dataEnc = encryptionService.encrypt(request.data(), encryptionKey);

        entry.setTitle(titleEnc.ciphertext());
        entry.setTitleIv(titleEnc.iv());
        entry.setEncryptedData(dataEnc.ciphertext());
        entry.setEncryptionIv(dataEnc.iv());

        if (request.notes() != null && !request.notes().isBlank()) {
            EncryptionService.EncryptionResult notesEnc = encryptionService.encrypt(request.notes(), encryptionKey);
            entry.setNotesEncrypted(notesEnc.ciphertext());
            entry.setNotesIv(notesEnc.iv());
        } else {
            entry.setNotesEncrypted(null);
            entry.setNotesIv(null);
        }

        if (request.isComplete() != null) {
            entry.setComplete(request.isComplete());
        }

        entry.setReminderDate(request.reminderDate());

        entry = entryRepository.save(entry);
        progressService.recalculate(user);
        return toResponse(entry, encryptionKey);
    }

    @Transactional
    public void deleteEntry(User user, UUID entryId) {
        VaultEntry entry = entryRepository.findByIdAndUserId(entryId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Kirjet ei leitud"));
        entryRepository.delete(entry);
        progressService.recalculate(user);
    }

    @Transactional
    public VaultEntryResponse markReviewed(User user, UUID entryId, String encryptionKey) {
        VaultEntry entry = entryRepository.findByIdAndUserId(entryId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Kirjet ei leitud"));
        entry.setLastReviewedAt(Instant.now());
        entry = entryRepository.save(entry);
        return toResponse(entry, encryptionKey);
    }

    private void checkPlanLimit(User user) {
        Subscription sub = subscriptionRepository.findByUserId(user.getId()).orElse(null);
        String plan = (sub != null) ? sub.getPlan() : "NONE";

        if ("PLUS".equals(plan) || "FAMILY".equals(plan)) {
            return; // unlimited
        }

        if ("TRIAL".equals(plan) && sub != null) {
            if (sub.isTrialExpired()) {
                sub.setPlan("NONE");
                subscriptionRepository.save(sub);
                throw new IllegalStateException(
                        "Sinu prooviperiood on lõppenud. Vali Plus või Perekond plaan jätkamiseks!");
            }
            long count = entryRepository.countByUserId(user.getId());
            if (count >= TRIAL_ENTRY_LIMIT) {
                throw new IllegalStateException(
                        "Prooviperioodil saad lisada kuni " + TRIAL_ENTRY_LIMIT + " kirjet. Uuenda plaani!");
            }
            return;
        }

        throw new IllegalStateException(
                "Vault kasutamiseks on vajalik aktiivne tellimus. Vali Plus või Perekond plaan!");
    }

    private VaultEntryResponse toResponse(VaultEntry entry, String encryptionKey) {
        String decryptedTitle = encryptionService.decrypt(entry.getTitle(), entry.getTitleIv(), encryptionKey);
        String decryptedData = encryptionService.decrypt(entry.getEncryptedData(), entry.getEncryptionIv(), encryptionKey);

        String decryptedNotes = null;
        if (entry.getNotesEncrypted() != null && entry.getNotesIv() != null) {
            decryptedNotes = encryptionService.decrypt(entry.getNotesEncrypted(), entry.getNotesIv(), encryptionKey);
        }

        VaultCategory cat = entry.getCategory();
        return new VaultEntryResponse(
                entry.getId(),
                cat.getId(),
                cat.getSlug(),
                cat.getIcon(),
                decryptedTitle,
                decryptedData,
                decryptedNotes,
                entry.isComplete(),
                entry.isHasAttachments(),
                entry.getReminderDate(),
                entry.getLastReviewedAt(),
                entry.getCreatedAt(),
                entry.getUpdatedAt()
        );
    }
}
