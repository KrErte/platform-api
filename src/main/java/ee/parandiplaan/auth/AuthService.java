package ee.parandiplaan.auth;

import ee.parandiplaan.auth.dto.AuthResponse;
import ee.parandiplaan.auth.dto.LoginRequest;
import ee.parandiplaan.auth.dto.RegisterRequest;
import ee.parandiplaan.common.security.JwtService;
import ee.parandiplaan.notification.EmailService;
import ee.parandiplaan.progress.UserProgress;
import ee.parandiplaan.progress.UserProgressRepository;
import ee.parandiplaan.subscription.Subscription;
import ee.parandiplaan.subscription.SubscriptionRepository;
import ee.parandiplaan.user.User;
import ee.parandiplaan.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserProgressRepository userProgressRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email().toLowerCase())) {
            throw new IllegalArgumentException("See e-posti aadress on juba kasutusel");
        }

        User user = new User();
        user.setEmail(request.email().toLowerCase().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setEmailVerificationToken(UUID.randomUUID());
        user = userRepository.save(user);

        // Create FREE subscription
        Subscription sub = new Subscription();
        sub.setUser(user);
        subscriptionRepository.save(sub);

        // Create empty progress
        UserProgress progress = new UserProgress();
        progress.setUser(user);
        userProgressRepository.save(progress);

        log.info("New user registered: {}", user.getEmail());

        // Send verification email
        emailService.sendVerificationEmail(
                user.getEmail(),
                user.getFullName(),
                user.getEmailVerificationToken().toString()
        );

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().toLowerCase().trim())
                .orElseThrow(() -> new IllegalArgumentException("Vale e-post või parool"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Vale e-post või parool");
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        log.info("User logged in: {}", user.getEmail());

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtService.isTokenValid(refreshToken)) {
            throw new IllegalArgumentException("Kehtetu refresh token");
        }

        if (!"refresh".equals(jwtService.getTokenType(refreshToken))) {
            throw new IllegalArgumentException("Vale tokeni tüüp");
        }

        UUID userId = jwtService.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Kasutajat ei leitud"));

        return buildAuthResponse(user);
    }

    @Transactional
    public void verifyEmail(UUID token) {
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Kehtetu kinnitustoken"));

        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        userRepository.save(user);

        log.info("Email verified: {}", user.getEmail());
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        return new AuthResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                accessToken,
                refreshToken,
                user.isEmailVerified()
        );
    }
}
