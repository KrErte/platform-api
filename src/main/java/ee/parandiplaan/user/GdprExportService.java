package ee.parandiplaan.user;

import ee.parandiplaan.audit.AuditLog;
import ee.parandiplaan.audit.AuditLogRepository;
import ee.parandiplaan.session.UserSession;
import ee.parandiplaan.session.UserSessionRepository;
import ee.parandiplaan.subscription.Subscription;
import ee.parandiplaan.subscription.SubscriptionRepository;
import ee.parandiplaan.trust.HandoverRequest;
import ee.parandiplaan.trust.HandoverRequestRepository;
import ee.parandiplaan.trust.TrustedContact;
import ee.parandiplaan.trust.TrustedContactRepository;
import ee.parandiplaan.vault.VaultEntry;
import ee.parandiplaan.vault.VaultEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class GdprExportService {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final VaultEntryRepository vaultEntryRepository;
    private final TrustedContactRepository trustedContactRepository;
    private final HandoverRequestRepository handoverRequestRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserSessionRepository sessionRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> exportAllUserData(User user) {
        Map<String, Object> export = new LinkedHashMap<>();

        // Profile
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", user.getId());
        profile.put("email", user.getEmail());
        profile.put("fullName", user.getFullName());
        profile.put("phone", user.getPhone());
        profile.put("dateOfBirth", user.getDateOfBirth() != null ? user.getDateOfBirth().toString() : null);
        profile.put("country", user.getCountry());
        profile.put("language", user.getLanguage());
        profile.put("role", user.getRole());
        profile.put("emailVerified", user.isEmailVerified());
        profile.put("totpEnabled", user.isTotpEnabled());
        profile.put("onboardingCompleted", user.isOnboardingCompleted());
        profile.put("createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
        profile.put("lastLoginAt", user.getLastLoginAt() != null ? user.getLastLoginAt().toString() : null);
        export.put("profile", profile);

        // Email preferences
        Map<String, Object> prefs = new LinkedHashMap<>();
        prefs.put("notifyExpirationReminders", user.isNotifyExpirationReminders());
        prefs.put("notifyInactivityWarnings", user.isNotifyInactivityWarnings());
        prefs.put("notifySecurityAlerts", user.isNotifySecurityAlerts());
        prefs.put("notifySms", user.isNotifySms());
        export.put("emailPreferences", prefs);

        // Subscription
        subscriptionRepository.findByUserId(user.getId()).ifPresent(sub -> {
            Map<String, Object> subMap = new LinkedHashMap<>();
            subMap.put("plan", sub.getPlan());
            subMap.put("status", sub.getStatus());
            subMap.put("currentPeriodStart", sub.getCurrentPeriodStart() != null ? sub.getCurrentPeriodStart().toString() : null);
            subMap.put("currentPeriodEnd", sub.getCurrentPeriodEnd() != null ? sub.getCurrentPeriodEnd().toString() : null);
            subMap.put("createdAt", sub.getCreatedAt() != null ? sub.getCreatedAt().toString() : null);
            export.put("subscription", subMap);
        });

        // Vault entries (encrypted metadata only — no decrypted data)
        List<VaultEntry> entries = vaultEntryRepository.findAllByUserId(user.getId());
        List<Map<String, Object>> entryList = new ArrayList<>();
        for (VaultEntry e : entries) {
            Map<String, Object> em = new LinkedHashMap<>();
            em.put("id", e.getId());
            em.put("categoryId", e.getCategory() != null ? e.getCategory().getId() : null);
            em.put("complete", e.isComplete());
            em.put("reminderDate", e.getReminderDate() != null ? e.getReminderDate().toString() : null);
            em.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
            em.put("updatedAt", e.getUpdatedAt() != null ? e.getUpdatedAt().toString() : null);
            em.put("note", "Encrypted data not included in export for security");
            entryList.add(em);
        }
        export.put("vaultEntries", entryList);

        // Trusted contacts
        List<TrustedContact> contacts = trustedContactRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        List<Map<String, Object>> contactList = new ArrayList<>();
        for (TrustedContact c : contacts) {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("id", c.getId());
            cm.put("fullName", c.getFullName());
            cm.put("email", c.getEmail());
            cm.put("phone", c.getPhone());
            cm.put("relationship", c.getRelationship());
            cm.put("status", c.isInviteAccepted() ? "ACCEPTED" : "PENDING");
            cm.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
            contactList.add(cm);
        }
        export.put("trustedContacts", contactList);

        // Handover requests
        List<HandoverRequest> handovers = handoverRequestRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        List<Map<String, Object>> handoverList = new ArrayList<>();
        for (HandoverRequest h : handovers) {
            Map<String, Object> hm = new LinkedHashMap<>();
            hm.put("id", h.getId());
            hm.put("status", h.getStatus());
            hm.put("requesterEmail", h.getTrustedContact() != null ? h.getTrustedContact().getEmail() : null);
            hm.put("createdAt", h.getCreatedAt() != null ? h.getCreatedAt().toString() : null);
            handoverList.add(hm);
        }
        export.put("handoverRequests", handoverList);

        // Audit logs (last 1000)
        var auditPage = auditLogRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, 1000));
        List<Map<String, Object>> auditList = new ArrayList<>();
        for (AuditLog a : auditPage.getContent()) {
            Map<String, Object> am = new LinkedHashMap<>();
            am.put("action", a.getAction());
            am.put("detail", a.getDetail());
            am.put("ipAddress", a.getIpAddress());
            am.put("createdAt", a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
            auditList.add(am);
        }
        export.put("auditLogs", auditList);

        // Sessions
        List<UserSession> sessions = sessionRepository.findByUserIdAndRevokedAtIsNullOrderByLastUsedAtDesc(user.getId());
        List<Map<String, Object>> sessionList = new ArrayList<>();
        for (UserSession s : sessions) {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("id", s.getId());
            sm.put("deviceLabel", s.getDeviceLabel());
            sm.put("ipAddress", s.getIpAddress());
            sm.put("createdAt", s.getCreatedAt() != null ? s.getCreatedAt().toString() : null);
            sm.put("lastUsedAt", s.getLastUsedAt() != null ? s.getLastUsedAt().toString() : null);
            sessionList.add(sm);
        }
        export.put("sessions", sessionList);

        export.put("exportedAt", java.time.Instant.now().toString());

        return export;
    }
}
