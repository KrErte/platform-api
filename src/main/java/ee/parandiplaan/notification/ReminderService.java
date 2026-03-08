package ee.parandiplaan.notification;

import ee.parandiplaan.progress.ProgressService;
import ee.parandiplaan.progress.UserProgress;
import ee.parandiplaan.progress.UserProgressRepository;
import ee.parandiplaan.user.User;
import ee.parandiplaan.user.UserRepository;
import ee.parandiplaan.vault.VaultEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReminderService {

    private final UserRepository userRepository;
    private final UserProgressRepository progressRepository;
    private final VaultEntryRepository entryRepository;
    private final ReminderRepository reminderRepository;
    private final EmailService emailService;
    private final ProgressService progressService;

    @Value("${app.url:http://localhost:8080}")
    private String appUrl;

    private static final BigDecimal INCOMPLETE_THRESHOLD = new BigDecimal("50");

    /**
     * Runs every Monday at 10:00 EET.
     * Sends reminders for incomplete setup, inactive users, and annual reviews.
     */
    @Scheduled(cron = "0 0 10 * * MON", zone = "Europe/Tallinn")
    @Transactional
    public void processWeeklyReminders() {
        log.info("Starting weekly reminder processing...");

        List<User> allUsers = userRepository.findAll();

        for (User user : allUsers) {
            checkIncompleteSetup(user);
            checkInactiveUser(user);
            checkAnnualReview(user);
        }

        log.info("Weekly reminder processing completed");
    }

    /**
     * Users with incomplete setup (< 50% progress).
     */
    private void checkIncompleteSetup(User user) {
        UserProgress progress = progressRepository.findByUserId(user.getId()).orElse(null);
        if (progress == null) return;

        if (progress.getProgressPercentage().compareTo(INCOMPLETE_THRESHOLD) >= 0) return;

        // Don't spam — check if we sent this type recently (within 7 days)
        List<Reminder> recent = reminderRepository.findSentByUserAndType(user.getId(), "INCOMPLETE_SETUP");
        if (!recent.isEmpty()) {
            Instant lastSent = recent.getFirst().getSentAt();
            if (lastSent.isAfter(Instant.now().minus(7, ChronoUnit.DAYS))) return;
        }

        int completedCategories = progress.getCompletedCategories();
        int missingCategories = progress.getTotalCategories() - completedCategories;

        Reminder reminder = new Reminder();
        reminder.setUser(user);
        reminder.setType("INCOMPLETE_SETUP");
        reminder.setScheduledAt(Instant.now());
        reminder.setSentAt(Instant.now());
        reminderRepository.save(reminder);

        String subject = "Su Pärandiplaan on " + progress.getProgressPercentage().intValue() + "% valmis — jätkame?";
        String html = """
                <div style="font-family: sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #1B4332;">Tere, %s!</h2>
                    <p>Sinu Pärandiplaan on <strong>%s%%</strong> valmis.</p>
                    <p>Sul on veel <strong>%d kategooriat</strong> täitmata. Iga väike samm loeb!</p>
                    <p style="text-align: center; margin: 30px 0;">
                        <a href="%s" style="background: #2D6A4F; color: white; padding: 12px 30px; text-decoration: none; border-radius: 6px; font-weight: bold;">
                            Jätka seadistamist
                        </a>
                    </p>
                    <p style="color: #666; font-size: 14px;">Mida rohkem infot lisad, seda paremini on su lähedased kaitstud.</p>
                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    <p style="color: #999; font-size: 12px;">Pärandiplaan — Sinu digitaalne pärand, turvaliselt korraldatud</p>
                </div>
                """.formatted(
                user.getFullName(),
                progress.getProgressPercentage().intValue(),
                missingCategories,
                appUrl
        );

        emailService.sendEmail(user.getEmail(), subject, html);
        log.info("Incomplete setup reminder sent to: {}", user.getEmail());
    }

    /**
     * Users who haven't logged in for 30+ days.
     */
    private void checkInactiveUser(User user) {
        Instant lastActivity = user.getLastLoginAt();
        if (lastActivity == null) lastActivity = user.getCreatedAt();

        long daysSince = ChronoUnit.DAYS.between(lastActivity, Instant.now());
        if (daysSince < 30) return;

        // Don't spam — check if we sent this type recently (within 14 days)
        List<Reminder> recent = reminderRepository.findSentByUserAndType(user.getId(), "INACTIVE_USER");
        if (!recent.isEmpty()) {
            Instant lastSent = recent.getFirst().getSentAt();
            if (lastSent.isAfter(Instant.now().minus(14, ChronoUnit.DAYS))) return;
        }

        Reminder reminder = new Reminder();
        reminder.setUser(user);
        reminder.setType("INACTIVE_USER");
        reminder.setScheduledAt(Instant.now());
        reminder.setSentAt(Instant.now());
        reminderRepository.save(reminder);

        String subject = "Kas su Pärandiplaani info on ajakohane?";
        String html = """
                <div style="font-family: sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #1B4332;">Tere, %s!</h2>
                    <p>Sa pole juba <strong>%d päeva</strong> Pärandiplaani sisse loginud.</p>
                    <p>Soovitame regulaarselt kontrollida, et su andmed on endiselt ajakohased.
                    Kas su pangakontod, kindlustused ja kontaktandmed on õiged?</p>
                    <p style="text-align: center; margin: 30px 0;">
                        <a href="%s" style="background: #2D6A4F; color: white; padding: 12px 30px; text-decoration: none; border-radius: 6px; font-weight: bold;">
                            Vaata üle
                        </a>
                    </p>
                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    <p style="color: #999; font-size: 12px;">Pärandiplaan — Sinu digitaalne pärand, turvaliselt korraldatud</p>
                </div>
                """.formatted(user.getFullName(), daysSince, appUrl);

        emailService.sendEmail(user.getEmail(), subject, html);
        log.info("Inactive user reminder sent to: {}", user.getEmail());
    }

    /**
     * Annual review: entries not reviewed in 365+ days.
     */
    private void checkAnnualReview(User user) {
        Instant yearAgo = Instant.now().minus(365, ChronoUnit.DAYS);
        List<?> staleCategories = entryRepository.findCategoryIdsWithStaleEntries(user.getId(), yearAgo);
        if (staleCategories.isEmpty()) return;

        // Don't spam — check if we sent this type recently (within 30 days)
        List<Reminder> recent = reminderRepository.findSentByUserAndType(user.getId(), "ANNUAL_REVIEW");
        if (!recent.isEmpty()) {
            Instant lastSent = recent.getFirst().getSentAt();
            if (lastSent.isAfter(Instant.now().minus(30, ChronoUnit.DAYS))) return;
        }

        Reminder reminder = new Reminder();
        reminder.setUser(user);
        reminder.setType("ANNUAL_REVIEW");
        reminder.setScheduledAt(Instant.now());
        reminder.setSentAt(Instant.now());
        reminderRepository.save(reminder);

        String subject = "Aeg üle vaadata: kas su Pärandiplaani info on ajakohane?";
        String html = """
                <div style="font-family: sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #1B4332;">Aastane ülevaatus, %s</h2>
                    <p>Mõned kirjed sinu Pärandiplaanis pole <strong>üle aasta</strong> üle vaadatud.</p>
                    <p>Soovitame kõik kirjed regulaarselt üle vaadata, et andmed oleksid ajakohased.
                    Kas su pangakontod, kindlustused ja lepingud on endiselt kehtivad?</p>
                    <p style="text-align: center; margin: 30px 0;">
                        <a href="%s" style="background: #2D6A4F; color: white; padding: 12px 30px; text-decoration: none; border-radius: 6px; font-weight: bold;">
                            Alusta ülevaatust
                        </a>
                    </p>
                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    <p style="color: #999; font-size: 12px;">Pärandiplaan — Sinu digitaalne pärand, turvaliselt korraldatud</p>
                </div>
                """.formatted(user.getFullName(), appUrl);

        emailService.sendEmail(user.getEmail(), subject, html);
        log.info("Annual review reminder sent to: {}", user.getEmail());
    }
}
