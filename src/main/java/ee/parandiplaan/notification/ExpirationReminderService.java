package ee.parandiplaan.notification;

import ee.parandiplaan.user.User;
import ee.parandiplaan.vault.VaultEntry;
import ee.parandiplaan.vault.VaultEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpirationReminderService {

    private final VaultEntryRepository entryRepository;
    private final ReminderRepository reminderRepository;
    private final EmailService emailService;

    private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

    /**
     * Runs daily at 09:00 Tallinn time.
     * Checks for vault entries with reminder dates 30, 14, and 7 days from now.
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "Europe/Tallinn")
    @Transactional
    public void checkExpirations() {
        log.info("Starting daily expiration reminder check...");

        LocalDate today = LocalDate.now(TALLINN);

        for (int daysAhead : List.of(30, 14, 7)) {
            LocalDate targetDate = today.plusDays(daysAhead);
            List<VaultEntry> expiring = entryRepository.findByReminderDateBetween(targetDate, targetDate);

            for (VaultEntry entry : expiring) {
                User user = entry.getUser();
                String reminderType = "EXPIRATION_" + daysAhead + "D";

                // Anti-spam: don't send same type for same user within 7 days
                List<Reminder> recent = reminderRepository.findSentByUserAndType(user.getId(), reminderType);
                if (!recent.isEmpty()) {
                    Instant lastSent = recent.getFirst().getSentAt();
                    if (lastSent.isAfter(Instant.now().minus(7, ChronoUnit.DAYS))) {
                        continue;
                    }
                }

                // Record the reminder
                Reminder reminder = new Reminder();
                reminder.setUser(user);
                reminder.setType(reminderType);
                reminder.setScheduledAt(Instant.now());
                reminder.setSentAt(Instant.now());
                reminderRepository.save(reminder);

                // Send email
                String categoryName = entry.getCategory().getNameEt();
                emailService.sendExpirationReminder(
                        user.getEmail(),
                        user.getFullName(),
                        categoryName,
                        daysAhead,
                        entry.getReminderDate()
                );

                log.info("Expiration reminder ({}d) sent to {} for category '{}'",
                        daysAhead, user.getEmail(), categoryName);
            }
        }

        log.info("Daily expiration reminder check completed");
    }
}
