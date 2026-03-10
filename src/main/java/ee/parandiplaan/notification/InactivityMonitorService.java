package ee.parandiplaan.notification;

import ee.parandiplaan.trust.HandoverRequest;
import ee.parandiplaan.trust.HandoverRequestRepository;
import ee.parandiplaan.trust.TrustedContact;
import ee.parandiplaan.trust.TrustedContactRepository;
import ee.parandiplaan.user.User;
import ee.parandiplaan.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InactivityMonitorService {

    private final UserRepository userRepository;
    private final TrustedContactRepository contactRepository;
    private final InactivityCheckRepository checkRepository;
    private final HandoverRequestRepository handoverRepository;
    private final EmailService emailService;

    @Value("${app.url:http://localhost:8080}")
    private String appUrl;

    /**
     * Runs daily at 9:00 EET. Checks users who have INACTIVITY-mode trusted contacts
     * and sends warnings or triggers handover based on inactivity period.
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "Europe/Tallinn")
    @Transactional
    public void checkInactivity() {
        log.info("Starting daily inactivity check...");

        List<User> allUsers = userRepository.findAll();

        for (User user : allUsers) {
            List<TrustedContact> inactivityContacts = contactRepository
                    .findByUserIdOrderByCreatedAtDesc(user.getId())
                    .stream()
                    .filter(c -> "INACTIVITY".equals(c.getActivationMode()))
                    .filter(TrustedContact::isInviteAccepted)
                    .toList();

            if (inactivityContacts.isEmpty()) continue;

            // Use the shortest inactivity period among all INACTIVITY contacts
            int inactivityDays = inactivityContacts.stream()
                    .mapToInt(c -> c.getInactivityDays() != null ? c.getInactivityDays() : 90)
                    .min()
                    .orElse(90);

            Instant lastActivity = user.getLastLoginAt();
            if (lastActivity == null) lastActivity = user.getCreatedAt();

            long daysSinceActivity = ChronoUnit.DAYS.between(lastActivity, Instant.now());

            // WARNING_1: 14 days before deadline
            if (daysSinceActivity >= (inactivityDays - 14) && daysSinceActivity < (inactivityDays - 7)) {
                if (user.isNotifyInactivityWarnings()) {
                    sendWarningIfNotSent(user, "WARNING_1");
                }
            }
            // WARNING_2: 7 days before deadline
            else if (daysSinceActivity >= (inactivityDays - 7) && daysSinceActivity < inactivityDays) {
                if (user.isNotifyInactivityWarnings()) {
                    sendWarningIfNotSent(user, "WARNING_2");
                }
            }
            // FINAL: deadline reached, wait 48h then auto-trigger handover (always sent — security-critical)
            else if (daysSinceActivity >= inactivityDays) {
                handleFinalStage(user, inactivityContacts);
            }
        }

        log.info("Daily inactivity check completed");
    }

    /**
     * User clicks "I'm still here" link in email — resets the timer.
     */
    @Transactional
    public void confirmAlive(UUID responseToken) {
        InactivityCheck check = checkRepository.findByResponseToken(responseToken)
                .orElseThrow(() -> new IllegalArgumentException("Vigane või aegunud token"));

        if (check.getRespondedAt() != null) {
            return; // Already responded
        }

        check.setRespondedAt(Instant.now());
        checkRepository.save(check);

        // Also mark all other unresponded checks for this user as responded
        List<InactivityCheck> unresponded = checkRepository.findUnrespondedByUser(check.getUser().getId());
        for (InactivityCheck other : unresponded) {
            other.setRespondedAt(Instant.now());
        }
        checkRepository.saveAll(unresponded);

        // Update last login to reset inactivity timer
        User user = check.getUser();
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        log.info("User confirmed alive via inactivity check: {}", user.getEmail());
    }

    private void sendWarningIfNotSent(User user, String checkType) {
        List<InactivityCheck> existing = checkRepository.findUnrespondedByUserAndType(user.getId(), checkType);
        if (!existing.isEmpty()) return; // Already sent this warning

        InactivityCheck check = new InactivityCheck();
        check.setUser(user);
        check.setCheckType(checkType);
        check = checkRepository.save(check);

        String stillHereUrl = appUrl + "/api/v1/handover/still-here/" + check.getResponseToken();

        if ("WARNING_1".equals(checkType)) {
            sendWarning1Email(user, stillHereUrl);
        } else {
            sendWarning2Email(user, stillHereUrl);
        }

        log.info("Inactivity {} sent to user: {}", checkType, user.getEmail());
    }

    private void handleFinalStage(User user, List<TrustedContact> contacts) {
        List<InactivityCheck> existing = checkRepository.findUnrespondedByUserAndType(user.getId(), "FINAL");
        if (!existing.isEmpty()) {
            // FINAL already sent — check if 48h passed
            InactivityCheck finalCheck = existing.getFirst();
            long hoursSinceFinal = ChronoUnit.HOURS.between(finalCheck.getSentAt(), Instant.now());
            if (hoursSinceFinal >= 48) {
                autoTriggerHandover(user, contacts);
            }
            return;
        }

        // Send FINAL warning
        InactivityCheck check = new InactivityCheck();
        check.setUser(user);
        check.setCheckType("FINAL");
        check = checkRepository.save(check);

        String stillHereUrl = appUrl + "/api/v1/handover/still-here/" + check.getResponseToken();
        sendFinalEmail(user, stillHereUrl);

        log.info("Inactivity FINAL warning sent to user: {}", user.getEmail());
    }

    private void autoTriggerHandover(User user, List<TrustedContact> contacts) {
        for (TrustedContact contact : contacts) {
            // Check if there's already an approved or pending handover
            boolean hasActive = handoverRepository
                    .findByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), "PENDING")
                    .stream()
                    .anyMatch(r -> r.getTrustedContact().getId().equals(contact.getId()));
            boolean hasApproved = handoverRepository
                    .findByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), "AUTO_APPROVED")
                    .stream()
                    .anyMatch(r -> r.getTrustedContact().getId().equals(contact.getId()));

            if (hasActive || hasApproved) continue;

            HandoverRequest handover = new HandoverRequest();
            handover.setTrustedContact(contact);
            handover.setUser(user);
            handover.setStatus("AUTO_APPROVED");
            handover.setReason("Automaatne üleandmine inaktiivsuse tõttu");
            handover.setRespondedAt(Instant.now());
            handover.setRespondedBy("SYSTEM");
            handoverRepository.save(handover);

            // Notify the trusted contact
            emailService.sendHandoverApprovedEmail(
                    contact.getEmail(),
                    contact.getFullName(),
                    user.getFullName()
            );

            log.info("Auto-triggered handover for user {} to contact {}", user.getEmail(), contact.getEmail());
        }
    }

    private void sendWarning1Email(User user, String stillHereUrl) {
        String subject = "Kas kõik on korras? Logi Pärandiplaani sisse";
        String html = """
                <div style="font-family: sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #1B4332;">Kas kõik on korras, %s?</h2>
                    <p>Me märkasime, et sa pole mõnda aega Pärandiplaani sisse loginud.</p>
                    <p>Sinu inaktiivsuskontrolli seaded näevad ette, et pikema inaktiivsuse korral
                    saavad sinu usalduskontaktid juurdepääsu sinu andmetele.</p>
                    <p>Kui kõik on korras, kliki allolevat nuppu:</p>
                    <p style="text-align: center; margin: 30px 0;">
                        <a href="%s" style="background: #2D6A4F; color: white; padding: 12px 30px; text-decoration: none; border-radius: 6px; font-weight: bold;">
                            Olen endiselt siin
                        </a>
                    </p>
                    <p style="color: #666; font-size: 14px;">Või lihtsalt logi oma kontole sisse.</p>
                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    <p style="color: #999; font-size: 12px;">Pärandiplaan — Sinu digitaalne pärand, turvaliselt korraldatud</p>
                </div>
                """.formatted(user.getFullName(), stillHereUrl);

        emailService.sendEmail(user.getEmail(), subject, html);
    }

    private void sendWarning2Email(User user, String stillHereUrl) {
        String subject = "Tähtis: Sinu Pärandiplaani inaktiivsuskontroll";
        String html = """
                <div style="font-family: sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #1B4332;">Tähtis meeldetuletus, %s</h2>
                    <p><strong>Sa pole pikka aega Pärandiplaani sisse loginud.</strong></p>
                    <p>Sinu seadete järgi anname peagi sinu usalduskontaktidele juurdepääsu sinu andmetele.</p>
                    <p>Kui kõik on korras ja sa soovid seda peatada, kliki kohe allolevat nuppu:</p>
                    <p style="text-align: center; margin: 30px 0;">
                        <a href="%s" style="background: #D4A843; color: white; padding: 12px 30px; text-decoration: none; border-radius: 6px; font-weight: bold;">
                            Olen endiselt siin
                        </a>
                    </p>
                    <p style="color: #666; font-size: 14px;">Kui sa ei reageeri, anname sinu usalduskontaktidele ligipääsu peagi.</p>
                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    <p style="color: #999; font-size: 12px;">Pärandiplaan — Sinu digitaalne pärand, turvaliselt korraldatud</p>
                </div>
                """.formatted(user.getFullName(), stillHereUrl);

        emailService.sendEmail(user.getEmail(), subject, html);
    }

    private void sendFinalEmail(User user, String stillHereUrl) {
        String subject = "Viimane hoiatus: Sinu usalduskontaktid saavad peagi ligipääsu";
        String html = """
                <div style="font-family: sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #C0392B;">Viimane hoiatus, %s</h2>
                    <p><strong>Sinu inaktiivsusperiood on täis saanud.</strong></p>
                    <p>48 tunni pärast anname sinu usalduskontaktidele automaatselt juurdepääsu sinu Pärandiplaani andmetele.</p>
                    <p>Kui kõik on korras, kliki <strong>kohe</strong> allolevat nuppu:</p>
                    <p style="text-align: center; margin: 30px 0;">
                        <a href="%s" style="background: #C0392B; color: white; padding: 14px 35px; text-decoration: none; border-radius: 6px; font-weight: bold; font-size: 16px;">
                            Olen endiselt siin — peata üleandmine
                        </a>
                    </p>
                    <p style="color: #666; font-size: 14px;">Pärast 48 tundi toimub üleandmine automaatselt.</p>
                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    <p style="color: #999; font-size: 12px;">Pärandiplaan — Sinu digitaalne pärand, turvaliselt korraldatud</p>
                </div>
                """.formatted(user.getFullName(), stillHereUrl);

        emailService.sendEmail(user.getEmail(), subject, html);
    }
}
