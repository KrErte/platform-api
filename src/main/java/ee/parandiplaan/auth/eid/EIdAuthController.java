package ee.parandiplaan.auth.eid;

import ee.parandiplaan.auth.eid.dto.*;
import ee.parandiplaan.common.util.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/eid")
@RequiredArgsConstructor
public class EIdAuthController {

    private final EIdAuthService eIdAuthService;

    @PostMapping("/smart-id/start")
    public ResponseEntity<EIdStartResponse> startSmartId(@Valid @RequestBody EIdStartRequest request) {
        return ResponseEntity.ok(eIdAuthService.startSmartIdAuth(request.personalCode(), request.country()));
    }

    @PostMapping("/mobile-id/start")
    public ResponseEntity<EIdStartResponse> startMobileId(@Valid @RequestBody MIdStartRequest request) {
        return ResponseEntity.ok(eIdAuthService.startMobileIdAuth(request.personalCode(), request.phoneNumber()));
    }

    @GetMapping("/poll/{sessionId}")
    public ResponseEntity<EIdPollResponse> poll(
            @PathVariable String sessionId, HttpServletRequest httpRequest) {
        String ip = IpUtils.getClientIp(httpRequest);
        String ua = httpRequest.getHeader("User-Agent");
        return ResponseEntity.ok(eIdAuthService.pollAuth(sessionId, ip, ua));
    }
}
