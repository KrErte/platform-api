package ee.parandiplaan.session;

import ee.parandiplaan.common.security.CurrentUser;
import ee.parandiplaan.session.dto.SessionResponse;
import ee.parandiplaan.user.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @GetMapping
    public ResponseEntity<List<SessionResponse>> getActiveSessions(
            @CurrentUser User user, HttpServletRequest request) {
        UUID sessionId = getSessionId(request);
        return ResponseEntity.ok(sessionService.getActiveSessions(user.getId(), sessionId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> revokeSession(
            @CurrentUser User user, @PathVariable UUID id) {
        sessionService.revokeSession(user.getId(), id);
        return ResponseEntity.ok(Map.of("message", "Sessioon tühistatud"));
    }

    @DeleteMapping
    public ResponseEntity<Map<String, String>> revokeAllOtherSessions(
            @CurrentUser User user, HttpServletRequest request) {
        UUID sessionId = getSessionId(request);
        if (sessionId == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Sessiooni ID puudub"));
        }
        sessionService.revokeAllExceptCurrent(user.getId(), sessionId);
        return ResponseEntity.ok(Map.of("message", "Kõik teised sessioonid tühistatud"));
    }

    private UUID getSessionId(HttpServletRequest request) {
        Object sid = request.getAttribute("sessionId");
        if (sid instanceof UUID uuid) {
            return uuid;
        }
        if (sid instanceof String s) {
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }
}
