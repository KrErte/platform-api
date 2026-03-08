package ee.parandiplaan.vault;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VaultEntryRepository extends JpaRepository<VaultEntry, UUID> {

    List<VaultEntry> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<VaultEntry> findByUserIdAndCategoryIdOrderByCreatedAtDesc(UUID userId, UUID categoryId);

    Optional<VaultEntry> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);

    @Query("SELECT COUNT(DISTINCT e.category.id) FROM VaultEntry e WHERE e.user.id = :userId")
    long countDistinctCategoriesByUserId(UUID userId);

    @Query("SELECT COUNT(e) FROM VaultEntry e WHERE e.user.id = :userId AND e.complete = true")
    long countCompletedByUserId(UUID userId);

    @Query("SELECT DISTINCT e.category.id FROM VaultEntry e WHERE e.user.id = :userId " +
           "AND (e.lastReviewedAt IS NULL OR e.lastReviewedAt < :threshold)")
    List<UUID> findCategoryIdsWithStaleEntries(UUID userId, Instant threshold);

    @Query("SELECT DISTINCT e.category.id FROM VaultEntry e WHERE e.user.id = :userId")
    List<UUID> findDistinctCategoryIdsByUserId(UUID userId);

    @Query("SELECT COUNT(e) FROM VaultEntry e WHERE e.user.id = :userId AND e.complete = false")
    long countIncompleteByUserId(UUID userId);
}
