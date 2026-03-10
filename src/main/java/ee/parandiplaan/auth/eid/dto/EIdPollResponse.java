package ee.parandiplaan.auth.eid.dto;

import ee.parandiplaan.auth.dto.AuthResponse;

public record EIdPollResponse(
        String status, // RUNNING, COMPLETE, ERROR
        AuthResponse auth,
        String error
) {
    public static EIdPollResponse running() {
        return new EIdPollResponse("RUNNING", null, null);
    }

    public static EIdPollResponse complete(AuthResponse auth) {
        return new EIdPollResponse("COMPLETE", auth, null);
    }

    public static EIdPollResponse error(String error) {
        return new EIdPollResponse("ERROR", null, error);
    }
}
