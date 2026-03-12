package ee.parandiplaan.capsule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimeCapsuleRepository extends JpaRepository<TimeCapsule, UUID> {

    List<TimeCapsule> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<TimeCapsule> findByIdAndUserId(UUID id, UUID userId);

    List<TimeCapsule> findByRecipientContactIdAndStatus(UUID contactId, String status);

    List<TimeCapsule> findByStatusAndTriggerTypeAndTriggerDateLessThanEqual(
            String status, String triggerType, LocalDate date);

    List<TimeCapsule> findByUserIdAndStatus(UUID userId, String status);
}
