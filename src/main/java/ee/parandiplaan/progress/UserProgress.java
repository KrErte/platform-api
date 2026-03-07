package ee.parandiplaan.progress;

import ee.parandiplaan.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_progress")
@Getter
@Setter
@NoArgsConstructor
public class UserProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "total_categories", nullable = false)
    private int totalCategories = 0;

    @Column(name = "completed_categories", nullable = false)
    private int completedCategories = 0;

    @Column(name = "total_entries", nullable = false)
    private int totalEntries = 0;

    @Column(name = "completed_entries", nullable = false)
    private int completedEntries = 0;

    @Column(name = "progress_percentage", nullable = false)
    private BigDecimal progressPercentage = BigDecimal.ZERO;

    @Column(name = "last_calculated_at", nullable = false)
    private Instant lastCalculatedAt;

    @PrePersist
    protected void onCreate() {
        lastCalculatedAt = Instant.now();
    }
}
