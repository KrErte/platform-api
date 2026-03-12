package ee.parandiplaan.capsule;

import ee.parandiplaan.audit.AuditService;
import ee.parandiplaan.capsule.dto.CreateTimeCapsuleRequest;
import ee.parandiplaan.capsule.dto.TimeCapsuleResponse;
import ee.parandiplaan.capsule.dto.UpdateTimeCapsuleRequest;
import ee.parandiplaan.notification.EmailService;
import ee.parandiplaan.trust.TrustedContact;
import ee.parandiplaan.trust.TrustedContactRepository;
import ee.parandiplaan.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TimeCapsuleService {

    private final TimeCapsuleRepository capsuleRepository;
    private final TrustedContactRepository contactRepository;
    private final EmailService emailService;
    private final AuditService auditService;

    private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

    @Transactional
    public TimeCapsuleResponse create(User user, CreateTimeCapsuleRequest request) {
        TrustedContact contact = contactRepository.findByIdAndUserId(
                request.recipientContactId(), user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Usalduskontakti ei leitud"));

        if (!"HANDOVER".equals(request.triggerType()) && !"DATE".equals(request.triggerType())) {
            throw new IllegalArgumentException("Vigane päästiku tüüp");
        }

        if ("DATE".equals(request.triggerType())) {
            if (request.triggerDate() == null) {
                throw new IllegalArgumentException("Kuupäev on kohustuslik DATE-tüübi puhul");
            }
            if (request.triggerDate().isBefore(LocalDate.now(TALLINN))) {
                throw new IllegalArgumentException("Kuupäev peab olema tulevikus");
            }
        }

        TimeCapsule capsule = new TimeCapsule();
        capsule.setUser(user);
        capsule.setRecipientContact(contact);
        capsule.setEncryptedTitle(request.encryptedTitle());
        capsule.setEncryptedMessage(request.encryptedMessage());
        capsule.setTriggerType(request.triggerType());
        capsule.setTriggerDate(request.triggerDate());

        capsule = capsuleRepository.save(capsule);
        auditService.log(user, "CAPSULE_CREATED", contact.getFullName());
        return toResponse(capsule);
    }

    @Transactional
    public TimeCapsuleResponse update(User user, UUID capsuleId, UpdateTimeCapsuleRequest request) {
        TimeCapsule capsule = capsuleRepository.findByIdAndUserId(capsuleId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Ajakapslit ei leitud"));

        if (!"PENDING".equals(capsule.getStatus())) {
            throw new IllegalStateException("Ainult ootel ajakapsleid saab muuta");
        }

        if (!"HANDOVER".equals(request.triggerType()) && !"DATE".equals(request.triggerType())) {
            throw new IllegalArgumentException("Vigane päästiku tüüp");
        }

        if ("DATE".equals(request.triggerType())) {
            if (request.triggerDate() == null) {
                throw new IllegalArgumentException("Kuupäev on kohustuslik DATE-tüübi puhul");
            }
            if (request.triggerDate().isBefore(LocalDate.now(TALLINN))) {
                throw new IllegalArgumentException("Kuupäev peab olema tulevikus");
            }
        }

        capsule.setEncryptedTitle(request.encryptedTitle());
        capsule.setEncryptedMessage(request.encryptedMessage());
        capsule.setTriggerType(request.triggerType());
        capsule.setTriggerDate(request.triggerDate());

        capsule = capsuleRepository.save(capsule);
        auditService.log(user, "CAPSULE_UPDATED", capsule.getRecipientContact().getFullName());
        return toResponse(capsule);
    }

    @Transactional
    public void cancel(User user, UUID capsuleId) {
        TimeCapsule capsule = capsuleRepository.findByIdAndUserId(capsuleId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Ajakapslit ei leitud"));

        if (!"PENDING".equals(capsule.getStatus())) {
            throw new IllegalStateException("Ainult ootel ajakapsleid saab tühistada");
        }

        capsule.setStatus("CANCELLED");
        capsuleRepository.save(capsule);
        auditService.log(user, "CAPSULE_CANCELLED", capsule.getRecipientContact().getFullName());
    }

    @Transactional(readOnly = true)
    public List<TimeCapsuleResponse> listByUser(User user) {
        return capsuleRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void deliverHandoverCapsules(UUID userId) {
        List<TimeCapsule> capsules = capsuleRepository.findByStatusAndTriggerTypeAndTriggerDateLessThanEqual(
                "PENDING", "HANDOVER", null);

        // Filter by userId since HANDOVER capsules don't have a trigger_date
        List<TimeCapsule> userCapsules = capsuleRepository.findByUserIdAndStatus(userId, "PENDING")
                .stream()
                .filter(c -> "HANDOVER".equals(c.getTriggerType()))
                .toList();

        for (TimeCapsule capsule : userCapsules) {
            deliverCapsule(capsule);
        }

        if (!userCapsules.isEmpty()) {
            log.info("Delivered {} HANDOVER capsules for user {}", userCapsules.size(), userId);
        }
    }

    @Transactional
    public void deliverDateCapsules() {
        LocalDate today = LocalDate.now(TALLINN);
        List<TimeCapsule> dueCapsules = capsuleRepository
                .findByStatusAndTriggerTypeAndTriggerDateLessThanEqual("PENDING", "DATE", today);

        for (TimeCapsule capsule : dueCapsules) {
            deliverCapsule(capsule);
        }

        if (!dueCapsules.isEmpty()) {
            log.info("Delivered {} DATE capsules", dueCapsules.size());
        }
    }

    @Transactional(readOnly = true)
    public List<TimeCapsuleResponse> getDeliveredForContact(UUID contactId) {
        return capsuleRepository.findByRecipientContactIdAndStatus(contactId, "DELIVERED")
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void deliverCapsule(TimeCapsule capsule) {
        capsule.setStatus("DELIVERED");
        capsule.setDeliveredAt(Instant.now());
        capsuleRepository.save(capsule);

        TrustedContact contact = capsule.getRecipientContact();
        User owner = capsule.getUser();

        emailService.sendTimeCapsuleEmail(
                contact.getEmail(),
                contact.getFullName(),
                owner.getFullName()
        );

        log.info("Time capsule {} delivered to {}", capsule.getId(), contact.getEmail());
    }

    private TimeCapsuleResponse toResponse(TimeCapsule capsule) {
        TrustedContact contact = capsule.getRecipientContact();
        return new TimeCapsuleResponse(
                capsule.getId(),
                contact.getId(),
                contact.getFullName(),
                capsule.getEncryptedTitle(),
                capsule.getEncryptedMessage(),
                capsule.getTriggerType(),
                capsule.getTriggerDate(),
                capsule.getStatus(),
                capsule.getDeliveredAt(),
                capsule.getCreatedAt(),
                capsule.getUpdatedAt()
        );
    }
}
