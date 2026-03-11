package ee.parandiplaan.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<AuditLog> findTop10ByUserIdOrderByCreatedAtDesc(UUID userId);

    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
