package ee.parandiplaan.user.dto;

public record EmailPreferencesRequest(
        boolean notifyExpirationReminders,
        boolean notifyInactivityWarnings,
        boolean notifySecurityAlerts
) {}
