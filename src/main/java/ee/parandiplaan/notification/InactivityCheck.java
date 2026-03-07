package ee.parandiplaan.notification;

import ee.parandiplaan.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inactivity_checks")
@Getter
@Setter
@NoArgsConstructor
public class InactivityCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "check_type", nullable = false)
    private String checkType;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "response_token", nullable = false)
    private UUID responseToken;

    @PrePersist
    protected void onCreate() {
        sentAt = Instant.now();
        if (responseToken == null) responseToken = UUID.randomUUID();
    }
}
