package ee.parandiplaan.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    Optional<UserSession> findByRefreshTokenHash(String refreshTokenHash);

    List<UserSession> findByUserIdAndRevokedAtIsNullOrderByLastUsedAtDesc(UUID userId);

    boolean existsByUserIdAndIpAddressAndRevokedAtIsNull(UUID userId, String ipAddress);

    @Modifying
    @Query("UPDATE UserSession s SET s.revokedAt = :now WHERE s.user.id = :userId AND s.id <> :excludeId AND s.revokedAt IS NULL")
    void revokeAllExcept(UUID userId, UUID excludeId, Instant now);

    @Modifying
    @Query("UPDATE UserSession s SET s.revokedAt = :now WHERE s.user.id = :userId AND s.revokedAt IS NULL")
    void revokeAll(UUID userId, Instant now);
}
