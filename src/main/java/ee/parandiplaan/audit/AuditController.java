package ee.parandiplaan.audit;

import ee.parandiplaan.common.security.CurrentUser;
import ee.parandiplaan.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogs(
            @CurrentUser User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(auditService.getAuditLogs(user, page, size));
    }

    @GetMapping("/recent")
    public ResponseEntity<List<AuditLogResponse>> getRecentLogs(@CurrentUser User user) {
        return ResponseEntity.ok(auditService.getRecentLogs(user));
    }
}
