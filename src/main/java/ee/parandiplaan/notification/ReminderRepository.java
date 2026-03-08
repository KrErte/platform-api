package ee.parandiplaan.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ReminderRepository extends JpaRepository<Reminder, UUID> {

    @Query("SELECT r FROM Reminder r WHERE r.scheduledAt <= :now AND r.sentAt IS NULL AND r.dismissedAt IS NULL")
    List<Reminder> findDueReminders(Instant now);

    @Query("SELECT r FROM Reminder r WHERE r.user.id = :userId AND r.type = :type " +
           "AND r.sentAt IS NOT NULL ORDER BY r.sentAt DESC")
    List<Reminder> findSentByUserAndType(UUID userId, String type);

    boolean existsByUserIdAndTypeAndSentAtIsNull(UUID userId, String type);
}
