package ee.parandiplaan.trust;

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

    private static final int FREE_CONTACT_LIMIT = 1;
    private static final int PLUS_CONTACT_LIMIT = 5;

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
        contactRepository.delete(contact);
        progressService.recalculate(user);
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

        // TODO: Send invite email via Resend

        return toResponse(contact);
    }

    private void checkPlanLimit(User user) {
        Subscription sub = subscriptionRepository.findByUserId(user.getId()).orElse(null);
        String plan = (sub != null) ? sub.getPlan() : "FREE";

        long count = contactRepository.countByUserId(user.getId());

        int limit = switch (plan) {
            case "PLUS" -> PLUS_CONTACT_LIMIT;
            case "FAMILY" -> Integer.MAX_VALUE;
            default -> FREE_CONTACT_LIMIT;
        };

        if (count >= limit) {
            throw new IllegalStateException(
                    "Sinu plaanil on maksimaalselt " + limit + " usalduskontakti. Uuenda plaani!");
        }
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
