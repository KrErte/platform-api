package ee.parandiplaan.auth;

import ee.parandiplaan.auth.dto.*;
import ee.parandiplaan.common.security.CurrentUser;
import ee.parandiplaan.common.util.IpUtils;
import ee.parandiplaan.user.User;
import jakarta.servlet.http.HttpServletRequest;
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
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String ip = IpUtils.getClientIp(httpRequest);
        String ua = httpRequest.getHeader("User-Agent");
        return ResponseEntity.ok(authService.login(request, ip, ua));
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

    // --- Password Reset endpoints ---

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return ResponseEntity.ok(new MessageResponse("Kui see e-posti aadress on registreeritud, saadame lähtestamislingi."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<AuthResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(authService.resetPassword(request.token(), request.newPassword()));
    }

    @PostMapping("/change-password")
    public ResponseEntity<AuthResponse> changePassword(
            @CurrentUser User user,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest) {
        String ip = IpUtils.getClientIp(httpRequest);
        String ua = httpRequest.getHeader("User-Agent");
        return ResponseEntity.ok(authService.changePassword(user, request.currentPassword(), request.newPassword(), ip, ua));
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

    @PostMapping("/2fa/backup-codes")
    public ResponseEntity<TotpService.BackupCodesResponse> generateBackupCodes(@CurrentUser User user) {
        var codes = totpService.generateBackupCodes(user);
        return ResponseEntity.ok(new TotpService.BackupCodesResponse(codes));
    }
}
