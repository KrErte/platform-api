package ee.parandiplaan.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private static final String SECRET = "TestSecretKeyThatIsAtLeast256BitsLongForHmacSHA256Algorithm!!";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 900_000L, 7L);
    }

    @Test
    void generateAndValidateAccessToken() {
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        UUID sessionId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(userId, email, sessionId, "USER");

        assertNotNull(token);
        assertTrue(jwtService.isTokenValid(token));
        assertEquals(userId, jwtService.getUserIdFromToken(token));
        assertEquals("access", jwtService.getTokenType(token));
        assertEquals(sessionId, jwtService.getSessionIdFromToken(token));
    }

    @Test
    void generateAndValidateRefreshToken() {
        UUID userId = UUID.randomUUID();

        String token = jwtService.generateRefreshToken(userId);

        assertNotNull(token);
        assertTrue(jwtService.isTokenValid(token));
        assertEquals(userId, jwtService.getUserIdFromToken(token));
        assertEquals("refresh", jwtService.getTokenType(token));
    }

    @Test
    void accessTokenWithNullSessionId() {
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";

        String token = jwtService.generateAccessToken(userId, email, null, "USER");

        assertTrue(jwtService.isTokenValid(token));
        assertNull(jwtService.getSessionIdFromToken(token));
    }

    @Test
    void expiredTokenIsInvalid() {
        // Create JwtService with 0ms expiration
        JwtService shortLived = new JwtService(SECRET, 0L, 0L);

        UUID userId = UUID.randomUUID();
        String token = shortLived.generateAccessToken(userId, "test@example.com", null, "USER");

        // Token should be expired immediately
        assertFalse(shortLived.isTokenValid(token));
    }

    @Test
    void tamperedTokenIsInvalid() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateAccessToken(userId, "test@example.com", null, "USER");

        // Tamper with the token
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertFalse(jwtService.isTokenValid(tampered));
    }

    @Test
    void invalidTokenStringIsInvalid() {
        assertFalse(jwtService.isTokenValid("not.a.valid.token"));
        assertFalse(jwtService.isTokenValid(""));
        assertFalse(jwtService.isTokenValid(null));
    }

    @Test
    void differentSecretCannotValidateToken() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateAccessToken(userId, "test@example.com", null, "USER");

        JwtService otherService = new JwtService(
                "AnotherSecretKeyThatIsAtLeast256BitsLongForHmacSHA256AlgorithmXX", 900_000L, 7L);

        assertFalse(otherService.isTokenValid(token));
    }
}
