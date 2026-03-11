package ee.parandiplaan.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.parandiplaan.TestDataSeeder;
import ee.parandiplaan.config.TestMinioConfig;
import ee.parandiplaan.user.User;
import ee.parandiplaan.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestMinioConfig.class, TestDataSeeder.class})
class LemonSqueezyWebhookTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;

    @Value("${lemonsqueezy.webhook-secret}")
    private String webhookSecret;

    private User user;
    private String lsSubId; // Unique per test to avoid cross-test collisions

    @BeforeEach
    void setUp() throws Exception {
        String email = "webhook-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        lsSubId = "ls-sub-" + UUID.randomUUID().toString().substring(0, 8);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "TestPassword123",
                                "fullName", "Webhook Test User"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        user = userRepository.findByEmail(email).orElseThrow();
    }

    // --- Signature validation ---

    @Test
    void webhook_validSignature_returnsOk() throws Exception {
        String body = buildWebhookPayload("subscription_created", user.getId().toString(), "active");
        String signature = computeSignature(body);

        mockMvc.perform(post("/api/v1/webhooks/lemonsqueezy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", signature)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    @Test
    void webhook_invalidSignature_returnsUnauthorized() throws Exception {
        String body = buildWebhookPayload("subscription_created", user.getId().toString(), "active");

        mockMvc.perform(post("/api/v1/webhooks/lemonsqueezy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", "0000000000000000000000000000000000000000000000000000000000000000")
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void webhook_missingSignature_returnsUnauthorized() throws Exception {
        String body = buildWebhookPayload("subscription_created", user.getId().toString(), "active");

        mockMvc.perform(post("/api/v1/webhooks/lemonsqueezy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    // --- subscription_created ---

    @Test
    void webhook_subscriptionCreated_upgradesSubscription() throws Exception {
        String body = buildWebhookPayload("subscription_created", user.getId().toString(), "active");
        String signature = computeSignature(body);

        mockMvc.perform(post("/api/v1/webhooks/lemonsqueezy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", signature)
                        .content(body))
                .andExpect(status().isOk());

        Subscription sub = subscriptionRepository.findByUserId(user.getId()).orElseThrow();
        // Both plus-variant-id and family-variant-id are "test" in test config,
        // so resolvePlan checks family first and returns FAMILY.
        // We use "unknown-variant" which falls through to default PLUS.
        assertThat(sub.getPlan()).isEqualTo("PLUS");
        assertThat(sub.getStatus()).isEqualTo("ACTIVE");
        assertThat(sub.getLemonsqueezySubscriptionId()).isEqualTo(lsSubId);
        assertThat(sub.getLemonsqueezyCustomerId()).isEqualTo("ls-cust-456");
    }

    // --- subscription_updated ---

    @Test
    void webhook_subscriptionUpdated_changesStatus() throws Exception {
        // First create the subscription via webhook
        sendWebhook("subscription_created", user.getId().toString(), "active");

        // Now update it with past_due status
        String updateBody = buildWebhookPayload("subscription_updated", user.getId().toString(), "past_due");
        mockMvc.perform(post("/api/v1/webhooks/lemonsqueezy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", computeSignature(updateBody))
                        .content(updateBody))
                .andExpect(status().isOk());

        Subscription sub = subscriptionRepository.findByLemonsqueezySubscriptionId(lsSubId).orElseThrow();
        assertThat(sub.getStatus()).isEqualTo("PAST_DUE");
    }

    // --- subscription_cancelled ---

    @Test
    void webhook_subscriptionCancelled_setsStatusCancelled() throws Exception {
        sendWebhook("subscription_created", user.getId().toString(), "active");

        String cancelBody = buildWebhookPayload("subscription_cancelled", user.getId().toString(), "cancelled");
        mockMvc.perform(post("/api/v1/webhooks/lemonsqueezy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", computeSignature(cancelBody))
                        .content(cancelBody))
                .andExpect(status().isOk());

        Subscription sub = subscriptionRepository.findByLemonsqueezySubscriptionId(lsSubId).orElseThrow();
        assertThat(sub.getStatus()).isEqualTo("CANCELLED");
        assertThat(sub.getPlan()).isEqualTo("NONE");
    }

    // --- subscription_resumed ---

    @Test
    void webhook_subscriptionResumed_setsStatusActive() throws Exception {
        sendWebhook("subscription_created", user.getId().toString(), "active");

        // Set to cancelled manually
        Subscription sub = subscriptionRepository.findByLemonsqueezySubscriptionId(lsSubId).orElseThrow();
        sub.setStatus("CANCELLED");
        subscriptionRepository.save(sub);

        // Resume
        String resumeBody = buildWebhookPayload("subscription_resumed", user.getId().toString(), "active");
        mockMvc.perform(post("/api/v1/webhooks/lemonsqueezy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", computeSignature(resumeBody))
                        .content(resumeBody))
                .andExpect(status().isOk());

        sub = subscriptionRepository.findByLemonsqueezySubscriptionId(lsSubId).orElseThrow();
        assertThat(sub.getStatus()).isEqualTo("ACTIVE");
    }

    // --- subscription_payment_failed ---

    @Test
    void webhook_paymentFailed_setsStatusPastDue() throws Exception {
        sendWebhook("subscription_created", user.getId().toString(), "active");

        String failBody = buildWebhookPayload("subscription_payment_failed", user.getId().toString(), "past_due");
        mockMvc.perform(post("/api/v1/webhooks/lemonsqueezy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", computeSignature(failBody))
                        .content(failBody))
                .andExpect(status().isOk());

        Subscription sub = subscriptionRepository.findByLemonsqueezySubscriptionId(lsSubId).orElseThrow();
        assertThat(sub.getStatus()).isEqualTo("PAST_DUE");
    }

    // --- subscription_payment_success ---

    @Test
    void webhook_paymentSuccess_returnsOk() throws Exception {
        sendWebhook("subscription_created", user.getId().toString(), "active");

        String successBody = buildWebhookPayload("subscription_payment_success", user.getId().toString(), "active");
        mockMvc.perform(post("/api/v1/webhooks/lemonsqueezy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", computeSignature(successBody))
                        .content(successBody))
                .andExpect(status().isOk());
    }

    // --- Edge cases ---

    @Test
    void webhook_missingEventName_returnsBadRequest() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "data", Map.of(
                        "id", lsSubId,
                        "attributes", Map.of("status", "active")
                )
        ));
        String signature = computeSignature(body);

        mockMvc.perform(post("/api/v1/webhooks/lemonsqueezy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", signature)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void webhook_unknownEvent_returnsOk() throws Exception {
        String body = buildWebhookPayload("some_unknown_event", user.getId().toString(), "active");
        String signature = computeSignature(body);

        mockMvc.perform(post("/api/v1/webhooks/lemonsqueezy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", signature)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void webhook_invalidJson_returnsServerError() throws Exception {
        String body = "not-valid-json{{{";
        String signature = computeSignature(body);

        mockMvc.perform(post("/api/v1/webhooks/lemonsqueezy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", signature)
                        .content(body))
                .andExpect(status().is5xxServerError());
    }

    // --- Helpers ---

    private void sendWebhook(String eventName, String userId, String status) throws Exception {
        String body = buildWebhookPayload(eventName, userId, status);
        mockMvc.perform(post("/api/v1/webhooks/lemonsqueezy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", computeSignature(body))
                        .content(body))
                .andExpect(status().isOk());
    }

    private String buildWebhookPayload(String eventName, String userId, String status) throws Exception {
        // Use "unknown-variant" so resolvePlan falls through to default "PLUS"
        // (In test config, both plus-variant-id and family-variant-id are "test",
        //  so using "test" would always resolve to FAMILY since it's checked first.)
        Map<String, Object> payload = Map.of(
                "meta", Map.of(
                        "event_name", eventName,
                        "custom_data", Map.of("user_id", userId)
                ),
                "data", Map.of(
                        "id", lsSubId,
                        "attributes", Map.of(
                                "status", status,
                                "variant_id", "unknown-variant",
                                "customer_id", "ls-cust-456"
                        )
                )
        );
        return objectMapper.writeValueAsString(payload);
    }

    private String computeSignature(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
