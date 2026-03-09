package ee.parandiplaan.vault;

import ee.parandiplaan.auth.dto.MessageResponse;
import ee.parandiplaan.common.security.CurrentUser;
import ee.parandiplaan.user.User;
import ee.parandiplaan.vault.dto.CreateEntryRequest;
import ee.parandiplaan.vault.dto.UpdateEntryRequest;
import ee.parandiplaan.vault.dto.VaultEntryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vault/entries")
@RequiredArgsConstructor
public class VaultEntryController {

    private final VaultEntryService entryService;

    @GetMapping
    public ResponseEntity<List<VaultEntryResponse>> list(
            @CurrentUser User user,
            @RequestParam(required = false) UUID category,
            @RequestHeader("X-Encryption-Key") String encryptionKey) {
        return ResponseEntity.ok(entryService.listEntries(user, category, encryptionKey));
    }

    @GetMapping("/search")
    public ResponseEntity<List<VaultEntryResponse>> search(
            @CurrentUser User user,
            @RequestParam("q") String query,
            @RequestHeader("X-Encryption-Key") String encryptionKey) {
        return ResponseEntity.ok(entryService.searchEntries(user, query, encryptionKey));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VaultEntryResponse> get(
            @CurrentUser User user,
            @PathVariable UUID id,
            @RequestHeader("X-Encryption-Key") String encryptionKey) {
        return ResponseEntity.ok(entryService.getEntry(user, id, encryptionKey));
    }

    @PostMapping
    public ResponseEntity<VaultEntryResponse> create(
            @CurrentUser User user,
            @Valid @RequestBody CreateEntryRequest request,
            @RequestHeader("X-Encryption-Key") String encryptionKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(entryService.createEntry(user, request, encryptionKey));
    }

    @PutMapping("/{id}")
    public ResponseEntity<VaultEntryResponse> update(
            @CurrentUser User user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEntryRequest request,
            @RequestHeader("X-Encryption-Key") String encryptionKey) {
        return ResponseEntity.ok(entryService.updateEntry(user, id, request, encryptionKey));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> delete(
            @CurrentUser User user,
            @PathVariable UUID id) {
        entryService.deleteEntry(user, id);
        return ResponseEntity.ok(new MessageResponse("Kirje kustutatud"));
    }

    @PatchMapping("/{id}/toggle-complete")
    public ResponseEntity<VaultEntryResponse> toggleComplete(
            @CurrentUser User user,
            @PathVariable UUID id) {
        return ResponseEntity.ok(entryService.toggleComplete(user, id));
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<VaultEntryResponse> duplicate(
            @CurrentUser User user,
            @PathVariable UUID id,
            @RequestHeader("X-Encryption-Key") String encryptionKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(entryService.duplicateEntry(user, id, encryptionKey));
    }

    @PutMapping("/{id}/review")
    public ResponseEntity<VaultEntryResponse> markReviewed(
            @CurrentUser User user,
            @PathVariable UUID id,
            @RequestHeader("X-Encryption-Key") String encryptionKey) {
        return ResponseEntity.ok(entryService.markReviewed(user, id, encryptionKey));
    }
}
