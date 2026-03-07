package ee.parandiplaan.user;

import ee.parandiplaan.common.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@CurrentUser User user) {
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "fullName", user.getFullName(),
                "phone", user.getPhone() != null ? user.getPhone() : "",
                "country", user.getCountry(),
                "language", user.getLanguage(),
                "emailVerified", user.isEmailVerified(),
                "totpEnabled", user.isTotpEnabled(),
                "createdAt", user.getCreatedAt().toString()
        ));
    }
}
