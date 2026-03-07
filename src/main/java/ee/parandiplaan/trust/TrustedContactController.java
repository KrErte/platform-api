package ee.parandiplaan.trust;

import ee.parandiplaan.auth.dto.MessageResponse;
import ee.parandiplaan.common.security.CurrentUser;
import ee.parandiplaan.trust.dto.CreateTrustedContactRequest;
import ee.parandiplaan.trust.dto.TrustedContactResponse;
import ee.parandiplaan.trust.dto.UpdateTrustedContactRequest;
import ee.parandiplaan.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trusted-contacts")
@RequiredArgsConstructor
public class TrustedContactController {

    private final TrustedContactService contactService;

    @GetMapping
    public ResponseEntity<List<TrustedContactResponse>> list(@CurrentUser User user) {
        return ResponseEntity.ok(contactService.listContacts(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TrustedContactResponse> get(
            @CurrentUser User user,
            @PathVariable UUID id) {
        return ResponseEntity.ok(contactService.getContact(user, id));
    }

    @PostMapping
    public ResponseEntity<TrustedContactResponse> create(
            @CurrentUser User user,
            @Valid @RequestBody CreateTrustedContactRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(contactService.createContact(user, request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TrustedContactResponse> update(
            @CurrentUser User user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTrustedContactRequest request) {
        return ResponseEntity.ok(contactService.updateContact(user, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> delete(
            @CurrentUser User user,
            @PathVariable UUID id) {
        contactService.deleteContact(user, id);
        return ResponseEntity.ok(new MessageResponse("Usalduskontakt kustutatud"));
    }

    @PostMapping("/{id}/resend-invite")
    public ResponseEntity<TrustedContactResponse> resendInvite(
            @CurrentUser User user,
            @PathVariable UUID id) {
        return ResponseEntity.ok(contactService.resendInvite(user, id));
    }

    @PostMapping("/accept-invite/{token}")
    public ResponseEntity<TrustedContactResponse> acceptInvite(@PathVariable UUID token) {
        return ResponseEntity.ok(contactService.acceptInvite(token));
    }
}
