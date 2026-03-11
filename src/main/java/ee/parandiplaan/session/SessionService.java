package ee.parandiplaan.session;

import ee.parandiplaan.common.util.DeviceUtils;
import ee.parandiplaan.notification.EmailService;
import ee.parandiplaan.session.dto.SessionResponse;
import ee.parandiplaan.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private final UserSessionRepository sessionRepository;
    private final EmailService emailService;

    @Transactional
    public UserSession createSession(User user, String refreshToken, String ip, String userAgent) {
        boolean knownIp = ip != null && sessionRepository.existsByUserIdAndIpAddressAndRevokedAtIsNull(user.getId(), ip);

        UserSession session = new UserSession();
        session.setUser(user);
        session.setRefreshTokenHash(hashToken(refreshToken));
        session.setIpAddress(ip);
        session.setUserAgent(userAgent);
        session.setDeviceLabel(DeviceUtils.parseDeviceLabel(userAgent));
        session = sessionRepository.save(session);

        // Send security alert for new IP
        if (!knownIp && user.isNotifySecurityAlerts() && ip != null) {
            sendNewIpAlert(user, ip, session.getDeviceLabel());
        }

        return session;
    }

    @Transactional
    public UserSession validateAndTouchSession(String refreshToken) {
        String hash = hashToken(refreshToken);
        UserSession session = sessionRepository.findByRefreshTokenHash(hash)
                .orElseThrow(() -> new IllegalArgumentException("Sessioon ei leitud"));

        if (!session.isActive()) {
            throw new IllegalArgumentException("Sessioon on tühistatud");
        }

        session.setLastUsedAt(Instant.now());
        return sessionRepository.save(session);
    }

    @Transactional
    public void updateRefreshTokenHash(UUID sessionId, String newRefreshToken) {
        UserSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Sessioon ei leitud"));
        session.setRefreshTokenHash(hashToken(newRefreshToken));
        session.setLastUsedAt(Instant.now());
        sessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> getActiveSessions(UUID userId, UUID currentSessionId) {
        return sessionRepository.findByUserIdAndRevokedAtIsNullOrderByLastUsedAtDesc(userId)
                .stream()
                .map(s -> new SessionResponse(
                        s.getId(),
                        s.getDeviceLabel(),
                        s.getIpAddress(),
                        s.getCreatedAt(),
                        s.getLastUsedAt(),
                        s.getId().equals(currentSessionId)
                ))
                .toList();
    }

    @Transactional
    public void revokeSession(UUID userId, UUID sessionId) {
        UserSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Sessioon ei leitud"));

        if (!session.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Sessioon ei leitud");
        }

        session.setRevokedAt(Instant.now());
        sessionRepository.save(session);
    }

    @Transactional
    public void revokeAllExceptCurrent(UUID userId, UUID currentSessionId) {
        sessionRepository.revokeAllExcept(userId, currentSessionId, Instant.now());
    }

    @Transactional
    public void revokeAllSessions(UUID userId) {
        sessionRepository.revokeAll(userId, Instant.now());
    }

    private void sendNewIpAlert(User user, String ip, String deviceLabel) {
        String subject = "Uus sisselogimine tundmatust seadmest — Pärandiplaan";
        String html = """
                <div style="font-family: sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #1B4332;">Uus sisselogimine</h2>
                    <p>Tere, %s!</p>
                    <p>Sinu kontole logiti sisse uuest seadmest:</p>
                    <ul style="line-height:1.8;">
                        <li><strong>Seade:</strong> %s</li>
                        <li><strong>IP-aadress:</strong> %s</li>
                    </ul>
                    <p>Kui see olid sina, siis pole midagi vaja teha.</p>
                    <p style="color: #DC2626; font-weight: bold;">Kui see ei olnud sina, muuda kohe oma parool ja tühista kõik sessioonid Seadete lehel.</p>
                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    <p style="color: #999; font-size: 12px;">Pärandiplaan — Sinu digitaalne pärand, turvaliselt korraldatud</p>
                </div>
                """.formatted(user.getFullName(), deviceLabel, ip);

        emailService.sendEmail(user.getEmail(), subject, html);
    }

    static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
