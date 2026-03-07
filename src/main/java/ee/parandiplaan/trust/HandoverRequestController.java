package ee.parandiplaan.trust;

import ee.parandiplaan.common.security.CurrentUser;
import ee.parandiplaan.trust.dto.CreateHandoverRequest;
import ee.parandiplaan.trust.dto.HandoverRequestResponse;
import ee.parandiplaan.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/handover-requests")
@RequiredArgsConstructor
public class HandoverRequestController {

    private final HandoverRequestService handoverService;

    /**
     * Trusted contact creates a handover request (no auth — uses contact ID).
     */
    @PostMapping
    public ResponseEntity<HandoverRequestResponse> create(
            @Valid @RequestBody CreateHandoverRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(handoverService.createRequest(request));
    }

    /**
     * User lists all their handover requests.
     */
    @GetMapping
    public ResponseEntity<List<HandoverRequestResponse>> list(@CurrentUser User user) {
        return ResponseEntity.ok(handoverService.listRequests(user));
    }

    /**
     * User lists only pending handover requests.
     */
    @GetMapping("/pending")
    public ResponseEntity<List<HandoverRequestResponse>> listPending(@CurrentUser User user) {
        return ResponseEntity.ok(handoverService.listPendingRequests(user));
    }

    /**
     * User approves a handover request.
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<HandoverRequestResponse> approve(
            @CurrentUser User user,
            @PathVariable UUID id) {
        return ResponseEntity.ok(handoverService.approveRequest(user, id));
    }

    /**
     * User denies a handover request.
     */
    @PostMapping("/{id}/deny")
    public ResponseEntity<HandoverRequestResponse> deny(
            @CurrentUser User user,
            @PathVariable UUID id) {
        return ResponseEntity.ok(handoverService.denyRequest(user, id));
    }
}
