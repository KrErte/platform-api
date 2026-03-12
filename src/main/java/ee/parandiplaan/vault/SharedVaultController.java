package ee.parandiplaan.vault;

import ee.parandiplaan.capsule.TimeCapsuleService;
import ee.parandiplaan.capsule.dto.TimeCapsuleResponse;
import ee.parandiplaan.common.security.CurrentUser;
import ee.parandiplaan.trust.SharedVaultToken;
import ee.parandiplaan.trust.TrustedContact;
import ee.parandiplaan.user.User;
import ee.parandiplaan.vault.dto.AttachmentResponse;
import ee.parandiplaan.vault.dto.VaultEntryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shared-vault")
@RequiredArgsConstructor
public class SharedVaultController {

    private final SharedVaultService sharedVaultService;
    private final TimeCapsuleService timeCapsuleService;

    // ===== Public endpoints (token-based auth) =====

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getInfo(@RequestParam String token) {
        SharedVaultToken svt = sharedVaultService.validateAndTouch(token);
        TrustedContact contact = svt.getTrustedContact();
        User owner = svt.getUser();

        List<VaultEntryResponse> entries = sharedVaultService.listSharedEntries(svt);

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("ownerName", owner.getFullName());
        info.put("contactName", contact.getFullName());
        info.put("expiresAt", svt.getExpiresAt().toString());
        info.put("entryCount", entries.size());
        info.put("accessLevel", contact.getAccessLevel());
        return ResponseEntity.ok(info);
    }

    @GetMapping("/entries")
    public ResponseEntity<List<VaultEntryResponse>> getEntries(@RequestParam String token) {
        SharedVaultToken svt = sharedVaultService.validateAndTouch(token);
        List<VaultEntryResponse> entries = sharedVaultService.listSharedEntries(svt);
        return ResponseEntity.ok(entries);
    }

    @GetMapping("/entries/{entryId}/attachments")
    public ResponseEntity<List<AttachmentResponse>> getEntryAttachments(
            @RequestParam String token,
            @PathVariable UUID entryId) {
        SharedVaultToken svt = sharedVaultService.validateAndTouch(token);
        List<AttachmentResponse> attachments = sharedVaultService.listSharedAttachments(svt, entryId);
        return ResponseEntity.ok(attachments);
    }

    @GetMapping("/attachments/{attachmentId}/download")
    public ResponseEntity<byte[]> downloadAttachment(
            @RequestParam String token,
            @PathVariable UUID attachmentId) {
        SharedVaultToken svt = sharedVaultService.validateAndTouch(token);
        VaultAttachmentService.DownloadResult result = sharedVaultService.downloadSharedAttachment(svt, attachmentId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.fileName() + "\"")
                .contentType(MediaType.parseMediaType(result.mimeType()))
                .body(result.data());
    }

    @GetMapping("/capsules")
    public ResponseEntity<List<TimeCapsuleResponse>> getCapsules(@RequestParam String token) {
        SharedVaultToken svt = sharedVaultService.validateAndTouch(token);
        UUID contactId = svt.getTrustedContact().getId();
        return ResponseEntity.ok(timeCapsuleService.getDeliveredForContact(contactId));
    }

    // ===== Authenticated endpoints (owner) =====

    @GetMapping("/tokens")
    public ResponseEntity<List<Map<String, Object>>> listActiveTokens(@CurrentUser User user) {
        List<SharedVaultToken> tokens = sharedVaultService.listActiveTokens(user);
        List<Map<String, Object>> response = tokens.stream().map(t -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", t.getId());
            map.put("contactName", t.getTrustedContact().getFullName());
            map.put("contactEmail", t.getTrustedContact().getEmail());
            map.put("expiresAt", t.getExpiresAt().toString());
            map.put("lastAccessedAt", t.getLastAccessedAt() != null ? t.getLastAccessedAt().toString() : null);
            map.put("accessCount", t.getAccessCount());
            map.put("createdAt", t.getCreatedAt().toString());
            return map;
        }).toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/tokens/{tokenId}/revoke")
    public ResponseEntity<Map<String, String>> revokeToken(
            @CurrentUser User user,
            @PathVariable UUID tokenId) {
        sharedVaultService.revokeToken(user, tokenId);
        return ResponseEntity.ok(Map.of("message", "Jagamislink tühistatud"));
    }
}
