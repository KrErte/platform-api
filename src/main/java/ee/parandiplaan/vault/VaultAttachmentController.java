package ee.parandiplaan.vault;

import ee.parandiplaan.auth.dto.MessageResponse;
import ee.parandiplaan.common.security.CurrentUser;
import ee.parandiplaan.user.User;
import ee.parandiplaan.vault.dto.AttachmentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class VaultAttachmentController {

    private final VaultAttachmentService attachmentService;

    @PostMapping("/api/v1/vault/entries/{entryId}/attachments")
    public ResponseEntity<AttachmentResponse> upload(
            @CurrentUser User user,
            @PathVariable UUID entryId,
            @RequestParam("file") MultipartFile file,
            @RequestHeader("X-Encryption-Key") String encryptionKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(attachmentService.upload(user, entryId, file, encryptionKey));
    }

    @GetMapping("/api/v1/vault/entries/{entryId}/attachments")
    public ResponseEntity<List<AttachmentResponse>> list(
            @CurrentUser User user,
            @PathVariable UUID entryId,
            @RequestHeader("X-Encryption-Key") String encryptionKey) {
        return ResponseEntity.ok(attachmentService.listAttachments(user, entryId, encryptionKey));
    }

    @GetMapping("/api/v1/vault/attachments/{id}/download")
    public ResponseEntity<byte[]> download(
            @CurrentUser User user,
            @PathVariable UUID id,
            @RequestHeader("X-Encryption-Key") String encryptionKey) {
        VaultAttachmentService.DownloadResult result = attachmentService.download(user, id, encryptionKey);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(result.mimeType()));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(result.fileName())
                .build());
        headers.setContentLength(result.data().length);

        return new ResponseEntity<>(result.data(), headers, HttpStatus.OK);
    }

    @DeleteMapping("/api/v1/vault/attachments/{id}")
    public ResponseEntity<MessageResponse> delete(
            @CurrentUser User user,
            @PathVariable UUID id) {
        attachmentService.delete(user, id);
        return ResponseEntity.ok(new MessageResponse("Manus kustutatud"));
    }
}
