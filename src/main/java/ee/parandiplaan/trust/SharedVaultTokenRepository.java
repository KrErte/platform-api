package ee.parandiplaan.trust;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SharedVaultTokenRepository extends JpaRepository<SharedVaultToken, UUID> {

    Optional<SharedVaultToken> findByTokenHash(String tokenHash);

    List<SharedVaultToken> findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(UUID userId);

    Optional<SharedVaultToken> findByHandoverRequestIdAndTrustedContactId(UUID handoverRequestId, UUID trustedContactId);
}
