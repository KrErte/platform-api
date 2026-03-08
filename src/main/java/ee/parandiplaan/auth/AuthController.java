package ee.parandiplaan.auth;

import ee.parandiplaan.auth.dto.*;
import ee.parandiplaan.common.security.CurrentUser;
import ee.parandiplaan.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TotpService totpService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request.refreshToken()));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<MessageResponse> verifyEmail(@RequestParam UUID token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(new MessageResponse("E-post kinnitatud!"));
    }

    // --- 2FA endpoints ---

    @PostMapping("/2fa/setup")
    public ResponseEntity<TotpService.TotpSetupResponse> setup2fa(@CurrentUser User user) {
        return ResponseEntity.ok(totpService.setup(user));
    }

    @PostMapping("/2fa/enable")
    public ResponseEntity<MessageResponse> enable2fa(@CurrentUser User user, @RequestBody Map<String, String> body) {
        totpService.enable(user, body.get("code"));
        return ResponseEntity.ok(new MessageResponse("2FA aktiveeritud!"));
    }

    @PostMapping("/2fa/disable")
    public ResponseEntity<MessageResponse> disable2fa(@CurrentUser User user, @RequestBody Map<String, String> body) {
        totpService.disable(user, body.get("code"));
        return ResponseEntity.ok(new MessageResponse("2FA deaktiveeritud!"));
    }
}
