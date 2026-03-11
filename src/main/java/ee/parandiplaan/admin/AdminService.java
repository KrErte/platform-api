package ee.parandiplaan.admin;

import ee.parandiplaan.admin.dto.AdminStatsResponse;
import ee.parandiplaan.admin.dto.AdminUserResponse;
import ee.parandiplaan.audit.AuditLog;
import ee.parandiplaan.audit.AuditLogRepository;
import ee.parandiplaan.audit.AuditLogResponse;
import ee.parandiplaan.subscription.Subscription;
import ee.parandiplaan.subscription.SubscriptionRepository;
import ee.parandiplaan.trust.TrustedContactRepository;
import ee.parandiplaan.user.User;
import ee.parandiplaan.user.UserRepository;
import ee.parandiplaan.vault.VaultEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final VaultEntryRepository vaultEntryRepository;
    private final TrustedContactRepository trustedContactRepository;
    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public AdminStatsResponse getStats() {
        long totalUsers = userRepository.count();
        long adminUsers = userRepository.countByRole("ADMIN");
        long totalEntries = vaultEntryRepository.count();
        long totalContacts = trustedContactRepository.count();

        Map<String, Long> subsByPlan = new LinkedHashMap<>();
        for (String plan : new String[]{"TRIAL", "PLUS", "FAMILY"}) {
            subsByPlan.put(plan, subscriptionRepository.countByPlan(plan));
        }

        Map<String, Long> subsByStatus = new LinkedHashMap<>();
        for (String status : new String[]{"ACTIVE", "CANCELLED", "EXPIRED"}) {
            subsByStatus.put(status, subscriptionRepository.countByStatus(status));
        }

        Map<String, Long> regByMonth = new LinkedHashMap<>();
        for (Object[] row : userRepository.countRegistrationsByMonth()) {
            regByMonth.put((String) row[0], ((Number) row[1]).longValue());
        }

        return new AdminStatsResponse(totalUsers, adminUsers, totalEntries, totalContacts,
                subsByPlan, subsByStatus, regByMonth);
    }

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> getUsers(String search, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<User> users = (search != null && !search.isBlank())
                ? userRepository.searchUsers(search.trim(), pageable)
                : userRepository.findAllByOrderByCreatedAtDesc(pageable);

        return users.map(this::toAdminUserResponse);
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getUserDetail(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Kasutajat ei leitud"));
        return toAdminUserResponse(user);
    }

    @Transactional
    public AdminUserResponse changeUserRole(UUID userId, String newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Kasutajat ei leitud"));
        user.setRole(newRole);
        userRepository.save(user);
        return toAdminUserResponse(user);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getAuditLogs(int page, int size) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
                .map(this::toAuditResponse);
    }

    private AdminUserResponse toAdminUserResponse(User user) {
        Subscription sub = subscriptionRepository.findByUserId(user.getId()).orElse(null);
        long entryCount = vaultEntryRepository.countByUserId(user.getId());
        long contactCount = trustedContactRepository.countByUserId(user.getId());

        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.isEmailVerified(),
                user.isTotpEnabled(),
                sub != null ? sub.getPlan() : "NONE",
                sub != null ? sub.getStatus() : "NONE",
                entryCount,
                contactCount,
                user.getLastLoginAt(),
                user.getCreatedAt()
        );
    }

    private AuditLogResponse toAuditResponse(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getAction(), log.getDetail(), log.getCreatedAt());
    }
}
