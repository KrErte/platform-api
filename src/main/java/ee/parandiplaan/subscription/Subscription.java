package ee.parandiplaan.subscription;

import ee.parandiplaan.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class Subscription {

    public static final int TRIAL_DURATION_DAYS = 14;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private String plan = "TRIAL";

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "lemonsqueezy_subscription_id")
    private String lemonsqueezySubscriptionId;

    @Column(name = "lemonsqueezy_customer_id")
    private String lemonsqueezyCustomerId;

    @Column(name = "current_period_start")
    private Instant currentPeriodStart;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public boolean isTrialExpired() {
        return "TRIAL".equals(plan)
                && currentPeriodEnd != null
                && Instant.now().isAfter(currentPeriodEnd);
    }

    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
