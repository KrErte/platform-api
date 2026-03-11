package ee.parandiplaan.trust;

import ee.parandiplaan.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shared_vault_tokens")
@Getter
@Setter
@NoArgsConstructor
public class SharedVaultToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handover_request_id", nullable = false)
    private HandoverRequest handoverRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trusted_contact_id", nullable = false)
    private TrustedContact trustedContact;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt;

    @Column(name = "access_count", nullable = false)
    private int accessCount = 0;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public boolean isValid() {
        return revokedAt == null && Instant.now().isBefore(expiresAt);
    }
}
