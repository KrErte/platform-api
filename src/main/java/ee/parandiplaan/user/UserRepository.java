package ee.parandiplaan.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByEmailVerificationToken(UUID token);

    Optional<User> findByPasswordResetToken(UUID token);

    Optional<User> findByPersonalCode(String personalCode);

    boolean existsByPersonalCode(String personalCode);

    long countByRole(String role);

    @Query("SELECT u FROM User u WHERE LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<User> searchUsers(String search, Pageable pageable);

    Page<User> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query(value = "SELECT TO_CHAR(created_at, 'YYYY-MM') AS month, COUNT(*) AS cnt " +
                   "FROM users GROUP BY TO_CHAR(created_at, 'YYYY-MM') ORDER BY month DESC LIMIT 12",
           nativeQuery = true)
    List<Object[]> countRegistrationsByMonth();
}
