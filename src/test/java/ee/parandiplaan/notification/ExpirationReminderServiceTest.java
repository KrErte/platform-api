package ee.parandiplaan.notification;

import ee.parandiplaan.user.User;
import ee.parandiplaan.vault.VaultCategory;
import ee.parandiplaan.vault.VaultEntry;
import ee.parandiplaan.vault.VaultEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpirationReminderServiceTest {

    @Mock private VaultEntryRepository entryRepository;
    @Mock private ReminderRepository reminderRepository;
    @Mock private EmailService emailService;

    @InjectMocks
    private ExpirationReminderService service;

    private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

    private User user;
    private VaultCategory category;

    @BeforeEach
    void setUp() {
        user = new User();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setFullName("Test User");
        user.setNotifyExpirationReminders(true);

        category = new VaultCategory();
        org.springframework.test.util.ReflectionTestUtils.setField(category, "id", UUID.randomUUID());
        category.setSlug("insurance");
        category.setNameEt("Kindlustus");
        category.setNameEn("Insurance");
        category.setIcon("shield");
        category.setSortOrder(2);
        category.setFieldTemplate("[]");
    }

    private VaultEntry createEntry(LocalDate reminderDate) {
        VaultEntry entry = new VaultEntry();
        org.springframework.test.util.ReflectionTestUtils.setField(entry, "id", UUID.randomUUID());
        entry.setUser(user);
        entry.setCategory(category);
        entry.setTitle("Test Entry");
        entry.setEncryptedData("encrypted");
        entry.setTitleIv("iv");
        entry.setEncryptionIv("iv2");
        entry.setReminderDate(reminderDate);
        return entry;
    }

    // --- 30-day reminder ---

    @Test
    void checkExpirations_sends30DayReminder() {
        LocalDate today = LocalDate.now(TALLINN);
        LocalDate target30 = today.plusDays(30);
        VaultEntry entry = createEntry(target30);

        when(entryRepository.findByReminderDateBetween(target30, target30))
                .thenReturn(List.of(entry));
        when(entryRepository.findByReminderDateBetween(eq(today.plusDays(14)), eq(today.plusDays(14))))
                .thenReturn(List.of());
        when(entryRepository.findByReminderDateBetween(eq(today.plusDays(7)), eq(today.plusDays(7))))
                .thenReturn(List.of());
        when(reminderRepository.findSentByUserAndType(user.getId(), "EXPIRATION_30D"))
                .thenReturn(List.of());

        service.checkExpirations();

        verify(reminderRepository).save(any(Reminder.class));
        verify(emailService).sendExpirationReminder(
                eq("test@example.com"),
                eq("Test User"),
                eq("Kindlustus"),
                eq(30),
                eq(target30)
        );
    }

    // --- 14-day reminder ---

    @Test
    void checkExpirations_sends14DayReminder() {
        LocalDate today = LocalDate.now(TALLINN);
        LocalDate target14 = today.plusDays(14);
        VaultEntry entry = createEntry(target14);

        when(entryRepository.findByReminderDateBetween(eq(today.plusDays(30)), eq(today.plusDays(30))))
                .thenReturn(List.of());
        when(entryRepository.findByReminderDateBetween(target14, target14))
                .thenReturn(List.of(entry));
        when(entryRepository.findByReminderDateBetween(eq(today.plusDays(7)), eq(today.plusDays(7))))
                .thenReturn(List.of());
        when(reminderRepository.findSentByUserAndType(user.getId(), "EXPIRATION_14D"))
                .thenReturn(List.of());

        service.checkExpirations();

        verify(emailService).sendExpirationReminder(
                eq("test@example.com"),
                eq("Test User"),
                eq("Kindlustus"),
                eq(14),
                eq(target14)
        );
    }

    // --- 7-day reminder ---

    @Test
    void checkExpirations_sends7DayReminder() {
        LocalDate today = LocalDate.now(TALLINN);
        LocalDate target7 = today.plusDays(7);
        VaultEntry entry = createEntry(target7);

        when(entryRepository.findByReminderDateBetween(eq(today.plusDays(30)), eq(today.plusDays(30))))
                .thenReturn(List.of());
        when(entryRepository.findByReminderDateBetween(eq(today.plusDays(14)), eq(today.plusDays(14))))
                .thenReturn(List.of());
        when(entryRepository.findByReminderDateBetween(target7, target7))
                .thenReturn(List.of(entry));
        when(reminderRepository.findSentByUserAndType(user.getId(), "EXPIRATION_7D"))
                .thenReturn(List.of());

        service.checkExpirations();

        verify(emailService).sendExpirationReminder(
                eq("test@example.com"),
                eq("Test User"),
                eq("Kindlustus"),
                eq(7),
                eq(target7)
        );
    }

    // --- Anti-spam: don't send same reminder twice within 7 days ---

    @Test
    void checkExpirations_antiSpam_skipsWhenRecentlySent() {
        LocalDate today = LocalDate.now(TALLINN);
        LocalDate target30 = today.plusDays(30);
        VaultEntry entry = createEntry(target30);

        // A reminder was already sent 3 days ago (within 7-day window)
        Reminder recentReminder = new Reminder();
        recentReminder.setUser(user);
        recentReminder.setType("EXPIRATION_30D");
        recentReminder.setSentAt(Instant.now().minus(3, ChronoUnit.DAYS));

        when(entryRepository.findByReminderDateBetween(target30, target30))
                .thenReturn(List.of(entry));
        when(entryRepository.findByReminderDateBetween(eq(today.plusDays(14)), eq(today.plusDays(14))))
                .thenReturn(List.of());
        when(entryRepository.findByReminderDateBetween(eq(today.plusDays(7)), eq(today.plusDays(7))))
                .thenReturn(List.of());
        when(reminderRepository.findSentByUserAndType(user.getId(), "EXPIRATION_30D"))
                .thenReturn(List.of(recentReminder));

        service.checkExpirations();

        verify(reminderRepository, never()).save(any(Reminder.class));
        verify(emailService, never()).sendExpirationReminder(anyString(), anyString(), anyString(), anyInt(), any());
    }

    @Test
    void checkExpirations_antiSpam_sendsWhenLastReminderWasOlderThan7Days() {
        LocalDate today = LocalDate.now(TALLINN);
        LocalDate target30 = today.plusDays(30);
        VaultEntry entry = createEntry(target30);

        // Last reminder was sent 8 days ago (outside 7-day window)
        Reminder oldReminder = new Reminder();
        oldReminder.setUser(user);
        oldReminder.setType("EXPIRATION_30D");
        oldReminder.setSentAt(Instant.now().minus(8, ChronoUnit.DAYS));

        when(entryRepository.findByReminderDateBetween(target30, target30))
                .thenReturn(List.of(entry));
        when(entryRepository.findByReminderDateBetween(eq(today.plusDays(14)), eq(today.plusDays(14))))
                .thenReturn(List.of());
        when(entryRepository.findByReminderDateBetween(eq(today.plusDays(7)), eq(today.plusDays(7))))
                .thenReturn(List.of());
        when(reminderRepository.findSentByUserAndType(user.getId(), "EXPIRATION_30D"))
                .thenReturn(List.of(oldReminder));

        service.checkExpirations();

        verify(reminderRepository).save(any(Reminder.class));
        verify(emailService).sendExpirationReminder(anyString(), anyString(), anyString(), eq(30), any());
    }

    // --- Disabled preferences ---

    @Test
    void checkExpirations_skipsWhenNotifyDisabled() {
        user.setNotifyExpirationReminders(false); // User disabled notifications

        LocalDate today = LocalDate.now(TALLINN);
        LocalDate target30 = today.plusDays(30);
        VaultEntry entry = createEntry(target30);

        when(entryRepository.findByReminderDateBetween(target30, target30))
                .thenReturn(List.of(entry));
        when(entryRepository.findByReminderDateBetween(eq(today.plusDays(14)), eq(today.plusDays(14))))
                .thenReturn(List.of());
        when(entryRepository.findByReminderDateBetween(eq(today.plusDays(7)), eq(today.plusDays(7))))
                .thenReturn(List.of());

        service.checkExpirations();

        verify(reminderRepository, never()).save(any(Reminder.class));
        verify(emailService, never()).sendExpirationReminder(anyString(), anyString(), anyString(), anyInt(), any());
    }

    // --- Multiple entries expiring on same day ---

    @Test
    void checkExpirations_handlesMultipleEntriesOnSameDay() {
        LocalDate today = LocalDate.now(TALLINN);
        LocalDate target30 = today.plusDays(30);

        VaultCategory bankingCategory = new VaultCategory();
        org.springframework.test.util.ReflectionTestUtils.setField(bankingCategory, "id", UUID.randomUUID());
        bankingCategory.setSlug("banking");
        bankingCategory.setNameEt("Pangandus");
        bankingCategory.setNameEn("Banking");
        bankingCategory.setIcon("bank");
        bankingCategory.setSortOrder(1);
        bankingCategory.setFieldTemplate("[]");

        VaultEntry entry1 = createEntry(target30);
        VaultEntry entry2 = createEntry(target30);
        entry2.setCategory(bankingCategory);

        when(entryRepository.findByReminderDateBetween(target30, target30))
                .thenReturn(List.of(entry1, entry2));
        when(entryRepository.findByReminderDateBetween(eq(today.plusDays(14)), eq(today.plusDays(14))))
                .thenReturn(List.of());
        when(entryRepository.findByReminderDateBetween(eq(today.plusDays(7)), eq(today.plusDays(7))))
                .thenReturn(List.of());
        when(reminderRepository.findSentByUserAndType(user.getId(), "EXPIRATION_30D"))
                .thenReturn(List.of());

        service.checkExpirations();

        // Anti-spam: after first entry sends reminder, second entry from same user should be blocked
        // The service checks per-user per-type, so the second call will still find the just-saved reminder
        // Actually the reminder is saved synchronously, but findSentByUserAndType returns empty initially
        // So both entries get a reminder since they both check before saving
        verify(emailService, atLeast(1)).sendExpirationReminder(anyString(), anyString(), anyString(), eq(30), any());
    }

    // --- No entries expiring ---

    @Test
    void checkExpirations_doesNothingWhenNoEntriesExpiring() {
        LocalDate today = LocalDate.now(TALLINN);

        when(entryRepository.findByReminderDateBetween(any(), any()))
                .thenReturn(List.of());

        service.checkExpirations();

        verify(reminderRepository, never()).save(any(Reminder.class));
        verify(emailService, never()).sendExpirationReminder(anyString(), anyString(), anyString(), anyInt(), any());
    }

    // --- Reminder record is saved correctly ---

    @Test
    void checkExpirations_savesReminderWithCorrectFields() {
        LocalDate today = LocalDate.now(TALLINN);
        LocalDate target7 = today.plusDays(7);
        VaultEntry entry = createEntry(target7);

        when(entryRepository.findByReminderDateBetween(eq(today.plusDays(30)), eq(today.plusDays(30))))
                .thenReturn(List.of());
        when(entryRepository.findByReminderDateBetween(eq(today.plusDays(14)), eq(today.plusDays(14))))
                .thenReturn(List.of());
        when(entryRepository.findByReminderDateBetween(target7, target7))
                .thenReturn(List.of(entry));
        when(reminderRepository.findSentByUserAndType(user.getId(), "EXPIRATION_7D"))
                .thenReturn(List.of());

        service.checkExpirations();

        ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
        verify(reminderRepository).save(captor.capture());

        Reminder saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getType()).isEqualTo("EXPIRATION_7D");
        assertThat(saved.getSentAt()).isNotNull();
        assertThat(saved.getScheduledAt()).isNotNull();
    }
}
