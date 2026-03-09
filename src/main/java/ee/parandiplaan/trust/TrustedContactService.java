package ee.parandiplaan.trust;

import ee.parandiplaan.audit.AuditService;
import ee.parandiplaan.notification.EmailService;
import ee.parandiplaan.progress.ProgressService;
import ee.parandiplaan.subscription.Subscription;
import ee.parandiplaan.subscription.SubscriptionRepository;
import ee.parandiplaan.trust.dto.CreateTrustedContactRequest;
import ee.parandiplaan.trust.dto.TrustedContactResponse;
import ee.parandiplaan.trust.dto.UpdateTrustedContactRequest;
import ee.parandiplaan.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TrustedContactService {

    private final TrustedContactRepository contactRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ProgressService progressService;
    private final EmailService emailService;
    private final AuditService auditService;

    private static final int PLUS_CONTACT_LIMIT = 5;
    private static final int TRIAL_CONTACT_LIMIT = 1;

    @Transactional(readOnly = true)
    public List<TrustedContactResponse> listContacts(User user) {
        return contactRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TrustedContactResponse getContact(User user, UUID contactId) {
        TrustedContact contact = contactRepository.findByIdAndUserId(contactId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Usalduskontakti ei leitud"));
        return toResponse(contact);
    }

    @Transactional
    public TrustedContactResponse createContact(User user, CreateTrustedContactRequest request) {
        checkPlanLimit(user);

        TrustedContact contact = new TrustedContact();
        contact.setUser(user);
        contact.setFullName(request.fullName());
        contact.setEmail(request.email());
        contact.setPhone(request.phone());
        contact.setRelationship(request.relationship());

        if (request.accessLevel() != null) {
            validateAccessLevel(request.accessLevel());
            contact.setAccessLevel(request.accessLevel());
        }

        if (request.activationMode() != null) {
            validateActivationMode(request.activationMode());
            contact.setActivationMode(request.activationMode());
        }

        if (request.inactivityDays() != null) {
            contact.setInactivityDays(request.inactivityDays());
        }

        contact = contactRepository.save(contact);
        progressService.recalculate(user);
        auditService.log(user, "CONTACT_ADDED", contact.getFullName());

        // Send invite email
        emailService.sendInviteEmail(
                contact.getEmail(),
                contact.getFullName(),
                user.getFullName(),
                contact.getInviteToken().toString()
        );

        return toResponse(contact);
    }

    @Transactional
    public TrustedContactResponse updateContact(User user, UUID contactId, UpdateTrustedContactRequest request) {
        TrustedContact contact = contactRepository.findByIdAndUserId(contactId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Usalduskontakti ei leitud"));

        if (request.fullName() != null) {
            contact.setFullName(request.fullName());
        }
        if (request.phone() != null) {
            contact.setPhone(request.phone());
        }
        if (request.relationship() != null) {
            contact.setRelationship(request.relationship());
        }
        if (request.accessLevel() != null) {
            validateAccessLevel(request.accessLevel());
            contact.setAccessLevel(request.accessLevel());
        }
        if (request.activationMode() != null) {
            validateActivationMode(request.activationMode());
            contact.setActivationMode(request.activationMode());
        }
        if (request.inactivityDays() != null) {
            contact.setInactivityDays(request.inactivityDays());
        }

        contact = contactRepository.save(contact);
        return toResponse(contact);
    }

    @Transactional
    public void deleteContact(User user, UUID contactId) {
        TrustedContact contact = contactRepository.findByIdAndUserId(contactId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Usalduskontakti ei leitud"));
        String name = contact.getFullName();
        contactRepository.delete(contact);
        progressService.recalculate(user);
        auditService.log(user, "CONTACT_REMOVED", name);
    }

    @Transactional
    public TrustedContactResponse acceptInvite(UUID inviteToken) {
        TrustedContact contact = contactRepository.findByInviteToken(inviteToken)
                .orElseThrow(() -> new IllegalArgumentException("Vigane või aegunud kutse"));

        if (contact.isInviteAccepted()) {
            throw new IllegalStateException("Kutse on juba vastu võetud");
        }

        contact.setInviteAccepted(true);
        contact.setInviteAcceptedAt(Instant.now());
        contact = contactRepository.save(contact);
        return toResponse(contact);
    }

    @Transactional
    public TrustedContactResponse resendInvite(User user, UUID contactId) {
        TrustedContact contact = contactRepository.findByIdAndUserId(contactId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Usalduskontakti ei leitud"));

        if (contact.isInviteAccepted()) {
            throw new IllegalStateException("Kutse on juba vastu võetud");
        }

        // Generate new invite token
        contact.setInviteToken(UUID.randomUUID());
        contact = contactRepository.save(contact);

        // Send invite email
        emailService.sendInviteEmail(
                contact.getEmail(),
                contact.getFullName(),
                user.getFullName(),
                contact.getInviteToken().toString()
        );

        return toResponse(contact);
    }

    private void checkPlanLimit(User user) {
        Subscription sub = subscriptionRepository.findByUserId(user.getId()).orElse(null);
        String plan = (sub != null) ? sub.getPlan() : "NONE";

        if ("PLUS".equals(plan) || "FAMILY".equals(plan)) {
            long count = contactRepository.countByUserId(user.getId());
            int limit = "FAMILY".equals(plan) ? Integer.MAX_VALUE : PLUS_CONTACT_LIMIT;
            if (count >= limit) {
                throw new IllegalStateException(
                        "Sinu plaanil on maksimaalselt " + limit + " usalduskontakti. Uuenda plaani!");
            }
            return;
        }

        if ("TRIAL".equals(plan) && sub != null) {
            if (sub.isTrialExpired()) {
                sub.setPlan("NONE");
                subscriptionRepository.save(sub);
                throw new IllegalStateException(
                        "Sinu prooviperiood on lõppenud. Vali Plus või Perekond plaan jätkamiseks!");
            }
            long count = contactRepository.countByUserId(user.getId());
            if (count >= TRIAL_CONTACT_LIMIT) {
                throw new IllegalStateException(
                        "Prooviperioodil saad lisada kuni " + TRIAL_CONTACT_LIMIT + " usalduskontakti. Uuenda plaani!");
            }
            return;
        }

        throw new IllegalStateException(
                "Usalduskontaktide lisamiseks on vajalik aktiivne tellimus. Vali Plus või Perekond plaan!");
    }

    private void validateAccessLevel(String accessLevel) {
        if (!List.of("FULL", "LIMITED").contains(accessLevel)) {
            throw new IllegalArgumentException("Vigane juurdepääsutase: " + accessLevel);
        }
    }

    private void validateActivationMode(String activationMode) {
        if (!List.of("MANUAL", "INACTIVITY").contains(activationMode)) {
            throw new IllegalArgumentException("Vigane aktiveerimisrežiim: " + activationMode);
        }
    }

    private TrustedContactResponse toResponse(TrustedContact contact) {
        return new TrustedContactResponse(
                contact.getId(),
                contact.getFullName(),
                contact.getEmail(),
                contact.getPhone(),
                contact.getRelationship(),
                contact.getAccessLevel(),
                contact.getActivationMode(),
                contact.getInactivityDays(),
                contact.isInviteAccepted(),
                contact.getInviteAcceptedAt(),
                contact.getInviteToken(),
                contact.getCreatedAt(),
                contact.getUpdatedAt()
        );
    }
}
