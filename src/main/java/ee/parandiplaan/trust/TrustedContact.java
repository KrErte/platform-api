package ee.parandiplaan.trust;

import ee.parandiplaan.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trusted_contacts")
@Getter
@Setter
@NoArgsConstructor
public class TrustedContact {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String email;

    private String phone;

    private String relationship;

    @Column(name = "access_level", nullable = false)
    private String accessLevel = "FULL";

    @Column(name = "activation_mode", nullable = false)
    private String activationMode = "MANUAL";

    @Column(name = "inactivity_days")
    private Integer inactivityDays = 90;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "allowed_categories")
    private UUID[] allowedCategories;

    @Column(name = "server_key_share")
    private String serverKeyShare;

    @Column(name = "invite_token")
    private UUID inviteToken;

    @Column(name = "invite_accepted", nullable = false)
    private boolean inviteAccepted = false;

    @Column(name = "invite_accepted_at")
    private Instant inviteAcceptedAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = Instant.now();
        if (inviteToken == null) inviteToken = UUID.randomUUID();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
