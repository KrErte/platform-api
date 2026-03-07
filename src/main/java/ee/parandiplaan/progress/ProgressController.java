package ee.parandiplaan.progress;

import ee.parandiplaan.common.security.CurrentUser;
import ee.parandiplaan.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/progress")
@RequiredArgsConstructor
public class ProgressController {

    private final ProgressService progressService;

    @GetMapping
    public ResponseEntity<ProgressService.ProgressResponse> getProgress(@CurrentUser User user) {
        return ResponseEntity.ok(progressService.getProgress(user));
    }

    @GetMapping("/recalculate")
    public ResponseEntity<ProgressService.ProgressResponse> recalculate(@CurrentUser User user) {
        progressService.recalculate(user);
        return ResponseEntity.ok(progressService.getProgress(user));
    }
}
