package ee.parandiplaan.user;

import ee.parandiplaan.common.security.CurrentUser;
import ee.parandiplaan.user.dto.UpdateProfileRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@CurrentUser User user) {
        return ResponseEntity.ok(buildUserResponse(user));
    }

    @PutMapping("/me")
    public ResponseEntity<Map<String, Object>> updateProfile(
            @CurrentUser User user,
            @Valid @RequestBody UpdateProfileRequest request) {
        user.setFullName(request.fullName().trim());
        user.setPhone(request.phone());
        user.setDateOfBirth(request.dateOfBirth());
        if (request.country() != null) {
            user.setCountry(request.country());
        }
        if (request.language() != null) {
            user.setLanguage(request.language());
        }
        user = userRepository.save(user);
        return ResponseEntity.ok(buildUserResponse(user));
    }

    private Map<String, Object> buildUserResponse(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("email", user.getEmail());
        map.put("fullName", user.getFullName());
        map.put("phone", user.getPhone() != null ? user.getPhone() : "");
        map.put("dateOfBirth", user.getDateOfBirth() != null ? user.getDateOfBirth().toString() : null);
        map.put("country", user.getCountry());
        map.put("language", user.getLanguage());
        map.put("emailVerified", user.isEmailVerified());
        map.put("totpEnabled", user.isTotpEnabled());
        map.put("createdAt", user.getCreatedAt().toString());
        return map;
    }
}
