package ee.parandiplaan.admin.dto;

import java.util.Map;

public record AdminStatsResponse(
        long totalUsers,
        long adminUsers,
        long totalVaultEntries,
        long totalTrustedContacts,
        Map<String, Long> subscriptionsByPlan,
        Map<String, Long> subscriptionsByStatus,
        Map<String, Long> registrationsByMonth
) {}
