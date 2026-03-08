package ee.parandiplaan.vault;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VaultAttachmentRepository extends JpaRepository<VaultAttachment, UUID> {

    List<VaultAttachment> findByVaultEntryIdOrderByCreatedAtDesc(UUID vaultEntryId);

    Optional<VaultAttachment> findByIdAndUserId(UUID id, UUID userId);

    long countByVaultEntryId(UUID vaultEntryId);

    @Query("SELECT COALESCE(SUM(a.fileSizeBytes), 0) FROM VaultAttachment a WHERE a.user.id = :userId")
    long sumFileSizeBytesByUserId(UUID userId);
}
