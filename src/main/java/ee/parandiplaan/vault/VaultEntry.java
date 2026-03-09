package ee.parandiplaan.vault;

import ee.parandiplaan.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "vault_entries")
@Getter
@Setter
@NoArgsConstructor
public class VaultEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private VaultCategory category;

    @Column(nullable = false)
    private String title;

    @Column(name = "encrypted_data", nullable = false)
    private String encryptedData;

    @Column(name = "title_iv", nullable = false)
    private String titleIv;

    @Column(name = "encryption_iv", nullable = false)
    private String encryptionIv;

    @Column(name = "notes_encrypted")
    private String notesEncrypted;

    @Column(name = "notes_iv")
    private String notesIv;

    @Column(name = "has_attachments", nullable = false)
    private boolean hasAttachments = false;

    @Column(name = "is_complete", nullable = false)
    private boolean complete = false;

    @Column(name = "reminder_date")
    private LocalDate reminderDate;

    @Column(name = "last_reviewed_at")
    private Instant lastReviewedAt;

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
