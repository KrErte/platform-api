package ee.parandiplaan.auth.eid.dto;

public record EIdStartResponse(
        String sessionId,
        String verificationCode
) {}
