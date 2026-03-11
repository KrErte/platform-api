package ee.parandiplaan.vault;

import ee.parandiplaan.common.security.CurrentUser;
import ee.parandiplaan.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/vault/export")
@RequiredArgsConstructor
public class VaultExportController {

    private final VaultExportService exportService;
    private final InheritancePlanPdfService inheritancePlanPdfService;

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @CurrentUser User user,
            @RequestHeader("X-Encryption-Key") String encryptionKey) {
        byte[] pdf = exportService.exportToPdf(user, encryptionKey);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=parandiplaan.pdf")
                .body(pdf);
    }

    @GetMapping("/plan")
    public ResponseEntity<byte[]> exportInheritancePlan(
            @CurrentUser User user,
            @RequestHeader("X-Encryption-Key") String encryptionKey) {
        byte[] pdf = inheritancePlanPdfService.generateInheritancePlan(user, encryptionKey);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=parandiplaan-dokument.pdf")
                .body(pdf);
    }

    @GetMapping("/json")
    public ResponseEntity<byte[]> exportJson(
            @CurrentUser User user,
            @RequestHeader("X-Encryption-Key") String encryptionKey) {
        byte[] json = exportService.exportToJson(user, encryptionKey);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=parandiplaan.json")
                .body(json);
    }
}
