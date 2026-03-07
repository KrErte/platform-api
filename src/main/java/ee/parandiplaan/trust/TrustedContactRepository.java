package ee.parandiplaan.trust;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrustedContactRepository extends JpaRepository<TrustedContact, UUID> {

    List<TrustedContact> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<TrustedContact> findByIdAndUserId(UUID id, UUID userId);

    Optional<TrustedContact> findByInviteToken(UUID inviteToken);

    long countByUserId(UUID userId);

    List<TrustedContact> findByEmail(String email);
}
