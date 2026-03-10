package ee.parandiplaan.auth.eid;

import ee.parandiplaan.audit.AuditService;
import ee.parandiplaan.auth.dto.AuthResponse;
import ee.parandiplaan.auth.eid.dto.EIdPollResponse;
import ee.parandiplaan.auth.eid.dto.EIdStartResponse;
import ee.parandiplaan.common.security.JwtService;
import ee.parandiplaan.progress.UserProgress;
import ee.parandiplaan.progress.UserProgressRepository;
import ee.parandiplaan.session.SessionService;
import ee.parandiplaan.session.UserSession;
import ee.parandiplaan.subscription.Subscription;
import ee.parandiplaan.subscription.SubscriptionRepository;
import ee.parandiplaan.user.User;
import ee.parandiplaan.user.UserRepository;
import ee.parandiplaan.vault.EncryptionService;
import ee.sk.mid.MidAuthenticationHashToSign;
import ee.sk.mid.MidClient;
import ee.sk.mid.MidDisplayTextFormat;
import ee.sk.mid.MidLanguage;
import ee.sk.mid.rest.dao.request.MidAuthenticationRequest;
import ee.sk.mid.rest.dao.response.MidAuthenticationResponse;
import ee.sk.smartid.AuthenticationHash;
import ee.sk.smartid.SmartIdAuthenticationResponse;
import ee.sk.smartid.SmartIdClient;
import ee.sk.smartid.rest.dao.Interaction;
import ee.sk.smartid.rest.dao.SemanticsIdentifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.net.ssl.SSLContext;

import javax.security.auth.x500.X500Principal;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class EIdAuthService {

    private final SmartIdClient smartIdClient;
    private final MidClient midClient;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserProgressRepository userProgressRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final SessionService sessionService;

    // In-memory pending async auth sessions
    private final ConcurrentHashMap<String, PendingSession> pendingSessions = new ConcurrentHashMap<>();

    public EIdAuthService(EIdProperties props,
                          UserRepository userRepository,
                          SubscriptionRepository subscriptionRepository,
                          UserProgressRepository userProgressRepository,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService,
                          AuditService auditService,
                          SessionService sessionService) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.userProgressRepository = userProgressRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.sessionService = sessionService;

        // Initialize Smart-ID client
        this.smartIdClient = new SmartIdClient();
        smartIdClient.setRelyingPartyUUID(props.getSmartId().getRelyingPartyUuid());
        smartIdClient.setRelyingPartyName(props.getSmartId().getRelyingPartyName());
        smartIdClient.setHostUrl(props.getSmartId().getHostUrl());

        // Initialize Mobile-ID client
        try {
            this.midClient = MidClient.newBuilder()
                    .withRelyingPartyUUID(props.getMobileId().getRelyingPartyUuid())
                    .withRelyingPartyName(props.getMobileId().getRelyingPartyName())
                    .withHostUrl(props.getMobileId().getHostUrl())
                    .withTrustSslContext(SSLContext.getDefault())
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Mobile-ID client", e);
        }
    }

    // ---- Smart-ID ----

    public EIdStartResponse startSmartIdAuth(String personalCode, String country) {
        AuthenticationHash authHash = AuthenticationHash.generateRandomHash();
        String verificationCode = authHash.calculateVerificationCode();

        String internalSessionId = UUID.randomUUID().toString();

        SemanticsIdentifier semanticsIdentifier = new SemanticsIdentifier(
                SemanticsIdentifier.IdentityType.PNO, country, personalCode);

        // Run blocking authenticate() in a virtual thread
        CompletableFuture<AuthResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                SmartIdAuthenticationResponse response = smartIdClient
                        .createAuthentication()
                        .withSemanticsIdentifier(semanticsIdentifier)
                        .withAuthenticationHash(authHash)
                        .withCertificateLevel("QUALIFIED")
                        .withAllowedInteractionsOrder(List.of(
                                Interaction.displayTextAndPIN("Pärandiplaani sisselogimine")
                        ))
                        .authenticate();

                // Extract name from certificate
                X509Certificate cert = response.getCertificate();
                String fullName = extractFullName(cert);

                return new AuthResult(true, personalCode, fullName, null);

            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                if (msg.contains("USER_REFUSED") || msg.toLowerCase().contains("refused")) {
                    return new AuthResult(false, personalCode, null, "Kasutaja keeldus autentimisest");
                }
                if (msg.contains("TIMEOUT") || msg.toLowerCase().contains("timeout")) {
                    return new AuthResult(false, personalCode, null, "Smart-ID aegumine. Proovige uuesti.");
                }
                log.error("Smart-ID auth failed: {}", e.getMessage());
                return new AuthResult(false, personalCode, null, "Smart-ID viga: " + e.getMessage());
            }
        });

        pendingSessions.put(internalSessionId, new PendingSession(future, Instant.now()));

        log.info("Smart-ID auth started for {}:{}", country, personalCode);
        return new EIdStartResponse(internalSessionId, verificationCode);
    }

    public EIdPollResponse pollAuth(String sessionId, String ip, String userAgent) {
        PendingSession pending = pendingSessions.get(sessionId);
        if (pending == null) {
            return EIdPollResponse.error("Sessioon aegunud või tundmatu");
        }

        if (!pending.future().isDone()) {
            return EIdPollResponse.running();
        }

        pendingSessions.remove(sessionId);

        try {
            AuthResult result = pending.future().get();
            if (!result.success()) {
                return EIdPollResponse.error(result.error());
            }

            AuthResponse authResponse = loginOrRegisterByPersonalCode(
                    result.personalCode(), result.fullName(), ip, userAgent);
            return EIdPollResponse.complete(authResponse);

        } catch (Exception e) {
            log.error("Poll failed for session {}: {}", sessionId, e.getMessage());
            return EIdPollResponse.error("Autentimine ebaõnnestus");
        }
    }

    // ---- Mobile-ID ----

    public EIdStartResponse startMobileIdAuth(String personalCode, String phoneNumber) {
        MidAuthenticationHashToSign authHash = MidAuthenticationHashToSign.generateRandomHashOfDefaultType();
        String verificationCode = authHash.calculateVerificationCode();

        String internalSessionId = UUID.randomUUID().toString();

        MidAuthenticationRequest request = MidAuthenticationRequest.newBuilder()
                .withPhoneNumber(phoneNumber)
                .withNationalIdentityNumber(personalCode)
                .withHashToSign(authHash)
                .withLanguage(MidLanguage.EST)
                .withDisplayText("Pärandiplaani login")
                .withDisplayTextFormat(MidDisplayTextFormat.GSM7)
                .build();

        MidAuthenticationResponse midResponse = midClient.getMobileIdConnector().authenticate(request);
        String midSessionId = midResponse.getSessionID();

        // Run blocking poll in a virtual thread
        CompletableFuture<AuthResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                var sessionStatus = midClient.getSessionStatusPoller()
                        .fetchFinalSessionStatus(midSessionId, "/authentication/session/{sessionId}");

                if (!"OK".equalsIgnoreCase(sessionStatus.getResult())) {
                    return new AuthResult(false, personalCode, null,
                            mapMobileIdError(sessionStatus.getResult()));
                }

                return new AuthResult(true, personalCode, null, null);

            } catch (ee.sk.mid.exception.MidUserCancellationException e) {
                return new AuthResult(false, personalCode, null, "Kasutaja katkestas autentimise");
            } catch (ee.sk.mid.exception.MidSessionTimeoutException e) {
                return new AuthResult(false, personalCode, null, "Mobiil-ID aegumine. Proovige uuesti.");
            } catch (ee.sk.mid.exception.MidPhoneNotAvailableException e) {
                return new AuthResult(false, personalCode, null, "Telefon pole kättesaadav");
            } catch (ee.sk.mid.exception.MidDeliveryException e) {
                return new AuthResult(false, personalCode, null, "SMS saatmine ebaõnnestus");
            } catch (Exception e) {
                log.error("Mobile-ID auth failed: {}", e.getMessage());
                return new AuthResult(false, personalCode, null, "Mobiil-ID viga: " + e.getMessage());
            }
        });

        pendingSessions.put(internalSessionId, new PendingSession(future, Instant.now()));

        log.info("Mobile-ID auth started for {}", personalCode);
        return new EIdStartResponse(internalSessionId, verificationCode);
    }

    // ---- Shared ----

    @Transactional
    public AuthResponse loginOrRegisterByPersonalCode(String personalCode, String fullName, String ip, String userAgent) {
        User user = userRepository.findByPersonalCode(personalCode).orElse(null);

        if (user == null) {
            user = new User();
            user.setPersonalCode(personalCode);
            user.setFullName(fullName != null ? fullName : "Kasutaja");
            user.setEmail(personalCode + "@eid.parandiplaan.ee");
            user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
            user.setEmailVerified(true);
            user.setEncryptionSalt(EncryptionService.generateSalt());
            user = userRepository.save(user);

            Subscription sub = new Subscription();
            sub.setUser(user);
            Instant now = Instant.now();
            sub.setCurrentPeriodStart(now);
            sub.setCurrentPeriodEnd(now.plus(Subscription.TRIAL_DURATION_DAYS, ChronoUnit.DAYS));
            subscriptionRepository.save(sub);

            UserProgress progress = new UserProgress();
            progress.setUser(user);
            userProgressRepository.save(progress);

            log.info("New eID user registered: {} ({})", personalCode, fullName);
        } else if (fullName != null && !fullName.isBlank()) {
            user.setFullName(fullName);
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        auditService.log(user, "LOGIN_EID", "eID sisselogimine", ip);

        String refreshToken = jwtService.generateRefreshToken(user.getId());
        UserSession session = sessionService.createSession(user, refreshToken, ip, userAgent);
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), session.getId());

        return new AuthResponse(
                user.getId(), user.getEmail(), user.getFullName(),
                accessToken, refreshToken, user.isEmailVerified()
        );
    }

    private String extractFullName(X509Certificate cert) {
        try {
            String dn = cert.getSubjectX500Principal().getName(X500Principal.RFC2253);
            String givenName = extractDnField(dn, "GIVENNAME");
            String surname = extractDnField(dn, "SURNAME");
            if (givenName == null) givenName = extractDnField(dn, "G");
            if (surname == null) surname = extractDnField(dn, "SN");
            if (givenName != null && surname != null) {
                return givenName + " " + surname;
            }
            // Fallback: use CN
            String cn = extractDnField(dn, "CN");
            return cn != null ? cn : "Kasutaja";
        } catch (Exception e) {
            return "Kasutaja";
        }
    }

    private String extractDnField(String dn, String field) {
        String prefix = field + "=";
        for (String part : dn.split(",")) {
            String trimmed = part.trim();
            if (trimmed.toUpperCase().startsWith(prefix.toUpperCase())) {
                return trimmed.substring(prefix.length()).trim();
            }
        }
        return null;
    }

    private String mapMobileIdError(String result) {
        if (result == null) return "Tundmatu viga";
        return switch (result) {
            case "TIMEOUT" -> "Mobiil-ID aegumine. Proovige uuesti.";
            case "NOT_MID_CLIENT" -> "See number ei ole Mobiil-ID number";
            case "USER_CANCELLED" -> "Kasutaja katkestas autentimise";
            case "SIGNATURE_HASH_MISMATCH" -> "Turvaprobleem. Proovige uuesti.";
            case "PHONE_ABSENT" -> "Telefon pole kättesaadav";
            case "DELIVERY_ERROR" -> "SMS saatmine ebaõnnestus";
            case "SIM_ERROR" -> "SIM-kaardi viga";
            default -> "Mobiil-ID autentimine ebaõnnestus: " + result;
        };
    }

    @Scheduled(fixedRate = 300_000)
    public void cleanupExpiredSessions() {
        Instant cutoff = Instant.now().minus(5, ChronoUnit.MINUTES);
        pendingSessions.entrySet().removeIf(e -> e.getValue().createdAt().isBefore(cutoff));
    }

    private record PendingSession(CompletableFuture<AuthResult> future, Instant createdAt) {}
    private record AuthResult(boolean success, String personalCode, String fullName, String error) {}
}
