package ee.parandiplaan.vault;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ee.parandiplaan.TestDataSeeder;
import ee.parandiplaan.config.TestMinioConfig;
import ee.parandiplaan.subscription.Subscription;
import ee.parandiplaan.subscription.SubscriptionRepository;
import ee.parandiplaan.trust.*;
import ee.parandiplaan.user.User;
import ee.parandiplaan.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestMinioConfig.class, TestDataSeeder.class})
class SharedVaultIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private VaultCategoryRepository categoryRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private TrustedContactRepository contactRepository;
    @Autowired private HandoverRequestRepository handoverRepository;
    @Autowired private SharedVaultTokenRepository tokenRepository;

    private String accessToken;
    private String password;
    private User user;
    private UUID bankingCategoryId;
    private UUID insuranceCategoryId;
    private String rawToken;
    private SharedVaultToken sharedToken;

    @BeforeEach
    void setUp() throws Exception {
        String email = "shared-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        password = "TestPassword123";

        // Register user (also escrows vault key)
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", password,
                                "fullName", "Shared Vault Tester"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        accessToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();

        user = userRepository.findByEmail(email).orElseThrow();
        bankingCategoryId = categoryRepository.findBySlug("banking").orElseThrow().getId();
        insuranceCategoryId = categoryRepository.findBySlug("insurance").orElseThrow().getId();

        // Create vault entries in different categories
        createVaultEntry(bankingCategoryId, "My Bank Account", "{\"bank_name\":\"SEB\",\"account\":\"EE1234\"}");
        createVaultEntry(insuranceCategoryId, "Insurance Policy", "{\"provider\":\"ERGO\",\"policy_nr\":\"P-999\"}");

        // Upgrade existing TRIAL subscription to PLUS
        Subscription sub = subscriptionRepository.findByUserId(user.getId()).orElseThrow();
        sub.setPlan("PLUS");
        sub.setCurrentPeriodEnd(Instant.now().plus(335, ChronoUnit.DAYS));
        subscriptionRepository.save(sub);

        // Create trusted contact directly via repository
        TrustedContact contact = new TrustedContact();
        contact.setUser(user);
        contact.setFullName("Jaan Tamm");
        contact.setEmail("jaan@example.com");
        contact.setRelationship("Abikaasa");
        contact.setAccessLevel("FULL");
        contact.setActivationMode("MANUAL");
        contact.setInviteAccepted(true);
        contact.setInviteAcceptedAt(Instant.now());
        contact = contactRepository.save(contact);

        // Create handover request
        HandoverRequest handover = new HandoverRequest();
        handover.setTrustedContact(contact);
        handover.setUser(user);
        handover.setStatus("APPROVED");
        handover.setReason("Test handover");
        handover.setResponseDeadline(Instant.now().plus(72, ChronoUnit.HOURS));
        handover.setRespondedAt(Instant.now());
        handover.setRespondedBy("USER");
        handover = handoverRepository.save(handover);

        // Create shared vault token
        byte[] tokenBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(tokenBytes);
        rawToken = HexFormat.of().formatHex(tokenBytes);
        String tokenHash = sha256(rawToken);

        SharedVaultToken token = new SharedVaultToken();
        token.setHandoverRequest(handover);
        token.setTrustedContact(contact);
        token.setUser(user);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        sharedToken = tokenRepository.save(token);
    }

    @Test
    void getInfo_validToken_returnsMetadata() throws Exception {
        mockMvc.perform(get("/api/v1/shared-vault/info")
                        .param("token", rawToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerName").value("Shared Vault Tester"))
                .andExpect(jsonPath("$.contactName").value("Jaan Tamm"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.entryCount").value(2))
                .andExpect(jsonPath("$.accessLevel").value("FULL"));
    }

    @Test
    void getEntries_validToken_returnsDecryptedEntries() throws Exception {
        mockMvc.perform(get("/api/v1/shared-vault/entries")
                        .param("token", rawToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].title", containsInAnyOrder("My Bank Account", "Insurance Policy")))
                .andExpect(jsonPath("$[0].data").isNotEmpty())
                .andExpect(jsonPath("$[0].categorySlug").isNotEmpty());
    }

    @Test
    void getInfo_invalidToken_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/shared-vault/info")
                        .param("token", "0000000000000000000000000000000000000000000000000000000000000000"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getEntries_expiredToken_returnsBadRequest() throws Exception {
        // Create an expired token
        TrustedContact contact = sharedToken.getTrustedContact();
        HandoverRequest handover = sharedToken.getHandoverRequest();

        byte[] tokenBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(tokenBytes);
        String expiredRawToken = HexFormat.of().formatHex(tokenBytes);

        SharedVaultToken expired = new SharedVaultToken();
        expired.setHandoverRequest(handover);
        expired.setTrustedContact(contact);
        expired.setUser(user);
        expired.setTokenHash(sha256(expiredRawToken));
        expired.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        tokenRepository.save(expired);

        mockMvc.perform(get("/api/v1/shared-vault/entries")
                        .param("token", expiredRawToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getEntries_revokedToken_returnsBadRequest() throws Exception {
        // Create a revoked token
        TrustedContact contact = sharedToken.getTrustedContact();
        HandoverRequest handover = sharedToken.getHandoverRequest();

        byte[] tokenBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(tokenBytes);
        String revokedRawToken = HexFormat.of().formatHex(tokenBytes);

        SharedVaultToken revoked = new SharedVaultToken();
        revoked.setHandoverRequest(handover);
        revoked.setTrustedContact(contact);
        revoked.setUser(user);
        revoked.setTokenHash(sha256(revokedRawToken));
        revoked.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        revoked.setRevokedAt(Instant.now());
        tokenRepository.save(revoked);

        mockMvc.perform(get("/api/v1/shared-vault/entries")
                        .param("token", revokedRawToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listActiveTokens_authenticated_returnsTokens() throws Exception {
        mockMvc.perform(get("/api/v1/shared-vault/tokens")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].contactName").value("Jaan Tamm"))
                .andExpect(jsonPath("$[0].contactEmail").value("jaan@example.com"))
                .andExpect(jsonPath("$[0].accessCount").isNumber());
    }

    @Test
    void listActiveTokens_unauthenticated_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/shared-vault/tokens"))
                .andExpect(status().isForbidden());
    }

    @Test
    void revokeToken_authenticated_revokesSuccessfully() throws Exception {
        mockMvc.perform(post("/api/v1/shared-vault/tokens/" + sharedToken.getId() + "/revoke")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").isNotEmpty());

        // Token should no longer work
        mockMvc.perform(get("/api/v1/shared-vault/info")
                        .param("token", rawToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void revokeToken_wrongUser_returnsBadRequest() throws Exception {
        // Register another user
        String otherEmail = "other-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", otherEmail,
                                "password", "OtherPass123",
                                "fullName", "Other User"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String otherAccessToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();

        // Try to revoke another user's token
        mockMvc.perform(post("/api/v1/shared-vault/tokens/" + sharedToken.getId() + "/revoke")
                        .header("Authorization", "Bearer " + otherAccessToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void limitedAccess_onlyShowsAllowedCategories() throws Exception {
        // Create a contact with LIMITED access to banking only
        TrustedContact limitedContact = new TrustedContact();
        limitedContact.setUser(user);
        limitedContact.setFullName("Limited User");
        limitedContact.setEmail("limited@example.com");
        limitedContact.setAccessLevel("LIMITED");
        limitedContact.setAllowedCategories(new UUID[]{bankingCategoryId}); // Only banking
        limitedContact.setActivationMode("MANUAL");
        limitedContact.setInviteAccepted(true);
        limitedContact = contactRepository.save(limitedContact);

        HandoverRequest handover = new HandoverRequest();
        handover.setTrustedContact(limitedContact);
        handover.setUser(user);
        handover.setStatus("APPROVED");
        handover.setResponseDeadline(Instant.now().plus(72, ChronoUnit.HOURS));
        handover = handoverRepository.save(handover);

        byte[] tokenBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(tokenBytes);
        String limitedRawToken = HexFormat.of().formatHex(tokenBytes);

        SharedVaultToken limitedToken = new SharedVaultToken();
        limitedToken.setHandoverRequest(handover);
        limitedToken.setTrustedContact(limitedContact);
        limitedToken.setUser(user);
        limitedToken.setTokenHash(sha256(limitedRawToken));
        limitedToken.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        tokenRepository.save(limitedToken);

        // Should only show banking entries (1 out of 2)
        mockMvc.perform(get("/api/v1/shared-vault/entries")
                        .param("token", limitedRawToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].categorySlug").value("banking"));
    }

    @Test
    void escrowVaultKey_authenticated_returnsOk() throws Exception {
        mockMvc.perform(post("/api/v1/auth/escrow-vault-key")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "vaultKey", "SomeVaultKey"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void escrowVaultKey_emptyKey_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/escrow-vault-key")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "vaultKey", ""
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void userProfile_showsEscrowStatus() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vaultKeyEscrowed").value(true))
                .andExpect(jsonPath("$.vaultKeyEscrowedAt").isNotEmpty());
    }

    // --- Helpers ---

    private void createVaultEntry(UUID catId, String title, String data) throws Exception {
        mockMvc.perform(post("/api/v1/vault/entries")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Encryption-Key", password)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "categoryId", catId.toString(),
                                "title", title,
                                "data", data
                        ))))
                .andExpect(status().isCreated());
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
