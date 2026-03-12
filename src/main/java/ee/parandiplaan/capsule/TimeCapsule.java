package ee.parandiplaan.capsule;

import ee.parandiplaan.trust.TrustedContact;
import ee.parandiplaan.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "time_capsules")
@Getter
@Setter
@NoArgsConstructor
public class TimeCapsule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_contact_id", nullable = false)
    private TrustedContact recipientContact;

    @Column(name = "encrypted_title", nullable = false)
    private String encryptedTitle;

    @Column(name = "encrypted_message", nullable = false)
    private String encryptedMessage;

    @Column(name = "trigger_type", nullable = false)
    private String triggerType;

    @Column(name = "trigger_date")
    private LocalDate triggerDate;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
