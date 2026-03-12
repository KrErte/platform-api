package ee.parandiplaan.trust;

import ee.parandiplaan.audit.AuditService;
import ee.parandiplaan.capsule.TimeCapsuleService;
import ee.parandiplaan.notification.EmailService;
import ee.parandiplaan.trust.dto.CreateHandoverRequest;
import ee.parandiplaan.trust.dto.HandoverRequestResponse;
import ee.parandiplaan.user.User;
import ee.parandiplaan.user.UserRepository;
import ee.parandiplaan.vault.SharedVaultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class HandoverRequestService {

    private final HandoverRequestRepository handoverRepository;
    private final TrustedContactRepository contactRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final AuditService auditService;
    private final SharedVaultService sharedVaultService;
    private final TimeCapsuleService timeCapsuleService;

    private static final int RESPONSE_DEADLINE_HOURS = 72;

    /**
     * Trusted contact initiates a handover request.
     * The contact must have accepted the invite.
     */
    @Transactional
    public HandoverRequestResponse createRequest(CreateHandoverRequest request) {
        TrustedContact contact = contactRepository.findById(request.trustedContactId())
                .orElseThrow(() -> new IllegalArgumentException("Usalduskontakti ei leitud"));

        if (!contact.getInviteToken().equals(request.inviteToken())) {
            throw new IllegalArgumentException("Vigane kutse token");
        }

        if (!contact.isInviteAccepted()) {
            throw new IllegalStateException("Kutse pole veel vastu võetud");
        }

        // Check for existing pending request
        List<HandoverRequest> existing = handoverRepository
                .findByUserIdAndStatusOrderByCreatedAtDesc(contact.getUser().getId(), "PENDING");
        boolean hasPendingFromSameContact = existing.stream()
                .anyMatch(r -> r.getTrustedContact().getId().equals(contact.getId()));
        if (hasPendingFromSameContact) {
            throw new IllegalStateException("Sul on juba aktiivne taotlus");
        }

        HandoverRequest handover = new HandoverRequest();
        handover.setTrustedContact(contact);
        handover.setUser(contact.getUser());
        handover.setReason(request.reason());
        handover.setResponseDeadline(Instant.now().plus(RESPONSE_DEADLINE_HOURS, ChronoUnit.HOURS));

        handover = handoverRepository.save(handover);

        // Notify user via email
        User owner = contact.getUser();
        emailService.sendHandoverRequestEmail(
                owner.getEmail(),
                owner.getFullName(),
                contact.getFullName(),
                request.reason()
        );

        return toResponse(handover);
    }

    /**
     * User views all handover requests for their account.
     */
    @Transactional(readOnly = true)
    public List<HandoverRequestResponse> listRequests(User user) {
        return handoverRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * User views pending handover requests only.
     */
    @Transactional(readOnly = true)
    public List<HandoverRequestResponse> listPendingRequests(User user) {
        return handoverRepository.findByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), "PENDING")
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * User approves a handover request.
     */
    @Transactional
    public HandoverRequestResponse approveRequest(User user, UUID requestId) {
        HandoverRequest handover = handoverRepository.findByIdAndUserId(requestId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Taotlust ei leitud"));

        if (!"PENDING".equals(handover.getStatus())) {
            throw new IllegalStateException("Taotlus pole enam aktiivne");
        }

        handover.setStatus("APPROVED");
        handover.setRespondedAt(Instant.now());
        handover.setRespondedBy("USER");
        handover = handoverRepository.save(handover);
        auditService.log(user, "HANDOVER_APPROVED", handover.getTrustedContact().getFullName());

        // Create shared vault access and notify contact
        TrustedContact contact = handover.getTrustedContact();
        try {
            String rawToken = sharedVaultService.createSharedAccess(handover);
            if (rawToken != null) {
                sharedVaultService.sendSharedAccessEmail(contact, user.getFullName(), rawToken);
            } else {
                // Active token already exists — just send approval notification
                emailService.sendHandoverApprovedEmail(contact.getEmail(), contact.getFullName(), user.getFullName());
            }
        } catch (IllegalStateException e) {
            log.warn("Shared access failed for handover {}: {}", handover.getId(), e.getMessage());
            // Still notify contact about approval, even if vault sharing failed
            emailService.sendHandoverApprovedEmail(contact.getEmail(), contact.getFullName(), user.getFullName());
        }

        // Deliver any HANDOVER-triggered time capsules
        try {
            timeCapsuleService.deliverHandoverCapsules(user.getId());
        } catch (Exception e) {
            log.warn("Time capsule delivery failed for handover {}: {}", handover.getId(), e.getMessage());
        }

        return toResponse(handover);
    }

    /**
     * User denies a handover request.
     */
    @Transactional
    public HandoverRequestResponse denyRequest(User user, UUID requestId) {
        HandoverRequest handover = handoverRepository.findByIdAndUserId(requestId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Taotlust ei leitud"));

        if (!"PENDING".equals(handover.getStatus())) {
            throw new IllegalStateException("Taotlus pole enam aktiivne");
        }

        handover.setStatus("DENIED");
        handover.setRespondedAt(Instant.now());
        handover.setRespondedBy("USER");
        handover = handoverRepository.save(handover);
        auditService.log(user, "HANDOVER_DENIED", handover.getTrustedContact().getFullName());

        // Notify contact
        TrustedContact contact = handover.getTrustedContact();
        emailService.sendHandoverDeniedEmail(
                contact.getEmail(),
                contact.getFullName(),
                user.getFullName()
        );

        return toResponse(handover);
    }

    private HandoverRequestResponse toResponse(HandoverRequest handover) {
        TrustedContact contact = handover.getTrustedContact();
        return new HandoverRequestResponse(
                handover.getId(),
                contact.getId(),
                contact.getFullName(),
                contact.getEmail(),
                handover.getStatus(),
                handover.getReason(),
                handover.getRequestedAt(),
                handover.getResponseDeadline(),
                handover.getRespondedAt(),
                handover.getRespondedBy(),
                handover.getCreatedAt()
        );
    }
}
