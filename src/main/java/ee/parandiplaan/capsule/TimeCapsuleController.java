package ee.parandiplaan.capsule;

import ee.parandiplaan.auth.dto.MessageResponse;
import ee.parandiplaan.capsule.dto.CreateTimeCapsuleRequest;
import ee.parandiplaan.capsule.dto.TimeCapsuleResponse;
import ee.parandiplaan.capsule.dto.UpdateTimeCapsuleRequest;
import ee.parandiplaan.common.security.CurrentUser;
import ee.parandiplaan.trust.SharedVaultToken;
import ee.parandiplaan.vault.SharedVaultService;
import ee.parandiplaan.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/capsules")
@RequiredArgsConstructor
public class TimeCapsuleController {

    private final TimeCapsuleService capsuleService;
    private final SharedVaultService sharedVaultService;

    @PostMapping
    public ResponseEntity<TimeCapsuleResponse> create(
            @CurrentUser User user,
            @Valid @RequestBody CreateTimeCapsuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(capsuleService.create(user, request));
    }

    @GetMapping
    public ResponseEntity<List<TimeCapsuleResponse>> list(@CurrentUser User user) {
        return ResponseEntity.ok(capsuleService.listByUser(user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TimeCapsuleResponse> update(
            @CurrentUser User user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTimeCapsuleRequest request) {
        return ResponseEntity.ok(capsuleService.update(user, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> cancel(
            @CurrentUser User user,
            @PathVariable UUID id) {
        capsuleService.cancel(user, id);
        return ResponseEntity.ok(new MessageResponse("Ajakapsel tühistatud"));
    }

    @GetMapping("/delivered")
    public ResponseEntity<List<TimeCapsuleResponse>> getDelivered(@RequestParam String token) {
        SharedVaultToken svt = sharedVaultService.validateAndTouch(token);
        UUID contactId = svt.getTrustedContact().getId();
        return ResponseEntity.ok(capsuleService.getDeliveredForContact(contactId));
    }
}
