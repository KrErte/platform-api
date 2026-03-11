package ee.parandiplaan.trust;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.parandiplaan.TestDataSeeder;
import ee.parandiplaan.config.TestMinioConfig;
import ee.parandiplaan.subscription.Subscription;
import ee.parandiplaan.subscription.SubscriptionRepository;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestMinioConfig.class, TestDataSeeder.class})
class HandoverRequestEdgeCaseTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private TrustedContactRepository contactRepository;
    @Autowired private HandoverRequestRepository handoverRepository;

    private User userA;
    private String tokenA;
    private User userB;
    private String tokenB;
    private TrustedContact contactForA;

    @BeforeEach
    void setUp() throws Exception {
        // Register user A (use lowercase — AuthService lowercases emails)
        String emailA = "usera-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        MvcResult resultA = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", emailA,
                                "password", "PasswordA123",
                                "fullName", "User A"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        tokenA = objectMapper.readTree(resultA.getResponse().getContentAsString())
                .get("accessToken").asText();
        userA = userRepository.findByEmail(emailA).orElseThrow();

        // Register user B (use lowercase — AuthService lowercases emails)
        String emailB = "userb-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        MvcResult resultB = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", emailB,
                                "password", "PasswordB123",
                                "fullName", "User B"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        tokenB = objectMapper.readTree(resultB.getResponse().getContentAsString())
                .get("accessToken").asText();
        userB = userRepository.findByEmail(emailB).orElseThrow();

        // Upgrade user A to PLUS (so they can have trusted contacts)
        Subscription subA = subscriptionRepository.findByUserId(userA.getId()).orElseThrow();
        subA.setPlan("PLUS");
        subA.setCurrentPeriodEnd(Instant.now().plus(335, ChronoUnit.DAYS));
        subscriptionRepository.save(subA);

        // Create a trusted contact for user A
        contactForA = new TrustedContact();
        contactForA.setUser(userA);
        contactForA.setFullName("Trusted For A");
        contactForA.setEmail("trusted-for-a@example.com");
        contactForA.setRelationship("Friend");
        contactForA.setAccessLevel("FULL");
        contactForA.setActivationMode("MANUAL");
        contactForA.setInviteAccepted(true);
        contactForA.setInviteAcceptedAt(Instant.now());
        contactForA = contactRepository.save(contactForA);
    }

    // --- Duplicate handover requests ---

    @Test
    void createHandover_duplicatePending_returnsError() throws Exception {
        // Create first handover request
        mockMvc.perform(post("/api/v1/handover-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "trustedContactId", contactForA.getId().toString(),
                                "inviteToken", contactForA.getInviteToken().toString(),
                                "reason", "First request"
                        ))))
                .andExpect(status().isCreated());

        // Try to create a second request for the same contact (duplicate)
        mockMvc.perform(post("/api/v1/handover-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "trustedContactId", contactForA.getId().toString(),
                                "inviteToken", contactForA.getInviteToken().toString(),
                                "reason", "Second request"
                        ))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void createHandover_afterDenied_succeedsAsNewRequest() throws Exception {
        // Create and deny first request
        MvcResult createResult = mockMvc.perform(post("/api/v1/handover-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "trustedContactId", contactForA.getId().toString(),
                                "inviteToken", contactForA.getInviteToken().toString(),
                                "reason", "First request"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        UUID requestId = UUID.fromString(objectMapper.readTree(
                createResult.getResponse().getContentAsString()).get("id").asText());

        // User A denies the request
        mockMvc.perform(post("/api/v1/handover-requests/" + requestId + "/deny")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        // Now the contact should be able to create a new request
        mockMvc.perform(post("/api/v1/handover-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "trustedContactId", contactForA.getId().toString(),
                                "inviteToken", contactForA.getInviteToken().toString(),
                                "reason", "New request after denial"
                        ))))
                .andExpect(status().isCreated());
    }

    // --- Expired / invalid invites ---

    @Test
    void createHandover_invalidInviteToken_returnsError() throws Exception {
        mockMvc.perform(post("/api/v1/handover-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "trustedContactId", contactForA.getId().toString(),
                                "inviteToken", UUID.randomUUID().toString(),
                                "reason", "Wrong token"
                        ))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void createHandover_uninvitedContact_returnsError() throws Exception {
        // Create a contact that has NOT accepted the invite
        TrustedContact unaccepted = new TrustedContact();
        unaccepted.setUser(userA);
        unaccepted.setFullName("Unaccepted Contact");
        unaccepted.setEmail("unaccepted@example.com");
        unaccepted.setAccessLevel("FULL");
        unaccepted.setActivationMode("MANUAL");
        unaccepted.setInviteAccepted(false); // Not accepted
        unaccepted = contactRepository.save(unaccepted);

        mockMvc.perform(post("/api/v1/handover-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "trustedContactId", unaccepted.getId().toString(),
                                "inviteToken", unaccepted.getInviteToken().toString(),
                                "reason", "Should fail"
                        ))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void createHandover_nonExistentContact_returnsError() throws Exception {
        mockMvc.perform(post("/api/v1/handover-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "trustedContactId", UUID.randomUUID().toString(),
                                "inviteToken", UUID.randomUUID().toString(),
                                "reason", "Unknown contact"
                        ))))
                .andExpect(status().is4xxClientError());
    }

    // --- Cross-user access attempts ---

    @Test
    void approveHandover_userBCannotApproveUserAsRequests() throws Exception {
        // Create a handover request for user A
        MvcResult createResult = mockMvc.perform(post("/api/v1/handover-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "trustedContactId", contactForA.getId().toString(),
                                "inviteToken", contactForA.getInviteToken().toString(),
                                "reason", "Test cross-user"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        UUID requestId = UUID.fromString(objectMapper.readTree(
                createResult.getResponse().getContentAsString()).get("id").asText());

        // User B tries to approve User A's request
        mockMvc.perform(post("/api/v1/handover-requests/" + requestId + "/approve")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void denyHandover_userBCannotDenyUserAsRequests() throws Exception {
        // Create a handover request for user A
        MvcResult createResult = mockMvc.perform(post("/api/v1/handover-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "trustedContactId", contactForA.getId().toString(),
                                "inviteToken", contactForA.getInviteToken().toString(),
                                "reason", "Test cross-user deny"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        UUID requestId = UUID.fromString(objectMapper.readTree(
                createResult.getResponse().getContentAsString()).get("id").asText());

        // User B tries to deny User A's request
        mockMvc.perform(post("/api/v1/handover-requests/" + requestId + "/deny")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void listHandovers_userBCannotSeeUserAsRequests() throws Exception {
        // Create a handover request for user A
        mockMvc.perform(post("/api/v1/handover-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "trustedContactId", contactForA.getId().toString(),
                                "inviteToken", contactForA.getInviteToken().toString(),
                                "reason", "Visible only to A"
                        ))))
                .andExpect(status().isCreated());

        // User B lists their handover requests — should not see User A's
        mockMvc.perform(get("/api/v1/handover-requests")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listPendingHandovers_userBCannotSeeUserAsRequests() throws Exception {
        // Create a handover request for user A
        mockMvc.perform(post("/api/v1/handover-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "trustedContactId", contactForA.getId().toString(),
                                "inviteToken", contactForA.getInviteToken().toString(),
                                "reason", "Pending only for A"
                        ))))
                .andExpect(status().isCreated());

        // User B lists pending requests — should not see User A's
        mockMvc.perform(get("/api/v1/handover-requests/pending")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // --- Double approve/deny ---

    @Test
    void approveHandover_alreadyApproved_returnsError() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/handover-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "trustedContactId", contactForA.getId().toString(),
                                "inviteToken", contactForA.getInviteToken().toString(),
                                "reason", "Approve twice"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        UUID requestId = UUID.fromString(objectMapper.readTree(
                createResult.getResponse().getContentAsString()).get("id").asText());

        // First approve
        mockMvc.perform(post("/api/v1/handover-requests/" + requestId + "/approve")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        // Second approve should fail (status is no longer PENDING)
        mockMvc.perform(post("/api/v1/handover-requests/" + requestId + "/approve")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void denyHandover_alreadyDenied_returnsError() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/handover-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "trustedContactId", contactForA.getId().toString(),
                                "inviteToken", contactForA.getInviteToken().toString(),
                                "reason", "Deny twice"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        UUID requestId = UUID.fromString(objectMapper.readTree(
                createResult.getResponse().getContentAsString()).get("id").asText());

        // First deny
        mockMvc.perform(post("/api/v1/handover-requests/" + requestId + "/deny")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        // Second deny should fail
        mockMvc.perform(post("/api/v1/handover-requests/" + requestId + "/deny")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().is4xxClientError());
    }

    // --- Unauthenticated access ---

    @Test
    void listHandovers_unauthenticated_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/handover-requests"))
                .andExpect(status().isForbidden());
    }

    @Test
    void approveHandover_unauthenticated_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/handover-requests/" + UUID.randomUUID() + "/approve"))
                .andExpect(status().isForbidden());
    }
}
