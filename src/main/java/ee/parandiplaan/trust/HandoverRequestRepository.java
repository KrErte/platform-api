package ee.parandiplaan.trust;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HandoverRequestRepository extends JpaRepository<HandoverRequest, UUID> {

    List<HandoverRequest> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, String status);

    List<HandoverRequest> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<HandoverRequest> findByIdAndUserId(UUID id, UUID userId);
}
