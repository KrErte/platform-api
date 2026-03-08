package ee.parandiplaan.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InactivityCheckRepository extends JpaRepository<InactivityCheck, UUID> {

    Optional<InactivityCheck> findByResponseToken(UUID responseToken);

    @Query("SELECT ic FROM InactivityCheck ic WHERE ic.user.id = :userId AND ic.checkType = :checkType " +
           "AND ic.respondedAt IS NULL ORDER BY ic.sentAt DESC")
    List<InactivityCheck> findUnrespondedByUserAndType(UUID userId, String checkType);

    @Query("SELECT ic FROM InactivityCheck ic WHERE ic.user.id = :userId " +
           "AND ic.respondedAt IS NULL ORDER BY ic.sentAt DESC")
    List<InactivityCheck> findUnrespondedByUser(UUID userId);
}
