package ee.parandiplaan.trust;

import ee.parandiplaan.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "handover_requests")
@Getter
@Setter
@NoArgsConstructor
public class HandoverRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trusted_contact_id", nullable = false)
    private TrustedContact trustedContact;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String status = "PENDING";

    private String reason;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "response_deadline")
    private Instant responseDeadline;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "responded_by")
    private String respondedBy;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = requestedAt = Instant.now();
    }
}
