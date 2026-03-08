package ee.parandiplaan.subscription;

import ee.parandiplaan.notification.EmailService;
import ee.parandiplaan.user.User;
import ee.parandiplaan.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${lemonsqueezy.api-key:}")
    private String apiKey;

    @Value("${lemonsqueezy.store-id:}")
    private String storeId;

    @Value("${lemonsqueezy.plus-variant-id:}")
    private String plusVariantId;

    @Value("${lemonsqueezy.family-variant-id:}")
    private String familyVariantId;

    @Transactional(readOnly = true)
    public SubscriptionResponse getSubscription(User user) {
        Subscription sub = subscriptionRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Tellimust ei leitud"));

        return toResponse(sub);
    }

    /**
     * Generate a LemonSqueezy checkout URL for a plan upgrade.
     */
    public CheckoutResponse createCheckout(User user, String plan) {
        String variantId = switch (plan.toUpperCase()) {
            case "PLUS" -> plusVariantId;
            case "FAMILY" -> familyVariantId;
            default -> throw new IllegalArgumentException("Tundmatu plaan: " + plan);
        };

        if (variantId == null || variantId.isBlank()) {
            throw new IllegalStateException("LemonSqueezy variant ID pole konfigureeritud plaanile: " + plan);
        }

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("LemonSqueezy API key pole konfigureeritud");
        }

        WebClient client = WebClient.builder()
                .baseUrl("https://api.lemonsqueezy.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Accept", "application/vnd.api+json")
                .defaultHeader("Content-Type", "application/vnd.api+json")
                .build();

        Map<String, Object> checkoutData = Map.of(
                "data", Map.of(
                        "type", "checkouts",
                        "attributes", Map.of(
                                "checkout_data", Map.of(
                                        "email", user.getEmail(),
                                        "name", user.getFullName(),
                                        "custom", Map.of("user_id", user.getId().toString())
                                )
                        ),
                        "relationships", Map.of(
                                "store", Map.of("data", Map.of("type", "stores", "id", storeId)),
                                "variant", Map.of("data", Map.of("type", "variants", "id", variantId))
                        )
                )
        );

        try {
            String response = client.post()
                    .uri("/checkouts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(checkoutData)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // Extract checkout URL from response
            // LemonSqueezy returns: { data: { attributes: { url: "..." } } }
            // Simple extraction without full JSON parsing
            String url = extractJsonValue(response, "url");
            log.info("Checkout created for user {} plan {}", user.getEmail(), plan);
            return new CheckoutResponse(url, plan);
        } catch (Exception e) {
            log.error("Failed to create checkout: {}", e.getMessage());
            throw new RuntimeException("Checkout loomine ebaõnnestus");
        }
    }

    /**
     * Handle LemonSqueezy webhook events.
     */
    @Transactional
    public void handleWebhookEvent(String eventName, Map<String, Object> data) {
        log.info("LemonSqueezy webhook: {}", eventName);

        Map<String, Object> attributes = getNestedMap(data, "data", "attributes");
        Map<String, Object> meta = getNestedMap(data, "meta");

        if (attributes == null) {
            log.warn("No attributes in webhook data");
            return;
        }

        String lsSubscriptionId = String.valueOf(getNestedValue(data, "data", "id"));
        String lsCustomerId = String.valueOf(attributes.get("customer_id"));
        String status = String.valueOf(attributes.get("status"));

        // Get user_id from custom_data
        String userId = null;
        Map<String, Object> customData = getNestedMap(meta, "custom_data");
        if (customData != null) {
            userId = (String) customData.get("user_id");
        }
        // Also try first_subscription_item for variant
        String variantId = String.valueOf(attributes.get("variant_id"));

        switch (eventName) {
            case "subscription_created" -> handleSubscriptionCreated(
                    userId, lsSubscriptionId, lsCustomerId, variantId, status, attributes);
            case "subscription_updated" -> handleSubscriptionUpdated(
                    lsSubscriptionId, variantId, status, attributes);
            case "subscription_cancelled" -> handleSubscriptionCancelled(lsSubscriptionId);
            case "subscription_resumed" -> handleSubscriptionResumed(lsSubscriptionId, attributes);
            case "subscription_payment_success" -> log.info("Payment success for sub {}", lsSubscriptionId);
            case "subscription_payment_failed" -> handlePaymentFailed(lsSubscriptionId);
            default -> log.info("Unhandled event: {}", eventName);
        }
    }

    private void handleSubscriptionCreated(String userId, String lsSubId, String lsCustomerId,
                                            String variantId, String status, Map<String, Object> attributes) {
        if (userId == null) {
            log.error("No user_id in subscription_created webhook");
            return;
        }

        UUID uid = UUID.fromString(userId);
        Subscription sub = subscriptionRepository.findByUserId(uid).orElse(null);
        if (sub == null) {
            log.error("No subscription found for user {}", userId);
            return;
        }

        String plan = resolvePlan(variantId);

        sub.setLemonsqueezySubscriptionId(lsSubId);
        sub.setLemonsqueezyCustomerId(lsCustomerId);
        sub.setPlan(plan);
        sub.setStatus(mapStatus(status));
        updatePeriod(sub, attributes);
        subscriptionRepository.save(sub);

        log.info("Subscription created: user={}, plan={}", userId, plan);

        // Notify user
        User user = userRepository.findById(uid).orElse(null);
        if (user != null) {
            emailService.sendEmail(user.getEmail(),
                    "Plaan aktiveeritud — Pärandiplaan",
                    planActivatedHtml(user.getFullName(), plan));
        }
    }

    private void handleSubscriptionUpdated(String lsSubId, String variantId,
                                            String status, Map<String, Object> attributes) {
        Subscription sub = subscriptionRepository.findByLemonsqueezySubscriptionId(lsSubId).orElse(null);
        if (sub == null) {
            log.warn("Subscription not found: {}", lsSubId);
            return;
        }

        String plan = resolvePlan(variantId);
        sub.setPlan(plan);
        sub.setStatus(mapStatus(status));
        updatePeriod(sub, attributes);
        subscriptionRepository.save(sub);

        log.info("Subscription updated: lsSubId={}, plan={}, status={}", lsSubId, plan, status);
    }

    private void handleSubscriptionCancelled(String lsSubId) {
        Subscription sub = subscriptionRepository.findByLemonsqueezySubscriptionId(lsSubId).orElse(null);
        if (sub == null) return;

        sub.setStatus("CANCELLED");
        sub.setPlan("NONE");
        subscriptionRepository.save(sub);

        log.info("Subscription cancelled: {}", lsSubId);
    }

    private void handleSubscriptionResumed(String lsSubId, Map<String, Object> attributes) {
        Subscription sub = subscriptionRepository.findByLemonsqueezySubscriptionId(lsSubId).orElse(null);
        if (sub == null) return;

        sub.setStatus("ACTIVE");
        updatePeriod(sub, attributes);
        subscriptionRepository.save(sub);

        log.info("Subscription resumed: {}", lsSubId);
    }

    private void handlePaymentFailed(String lsSubId) {
        Subscription sub = subscriptionRepository.findByLemonsqueezySubscriptionId(lsSubId).orElse(null);
        if (sub == null) return;

        sub.setStatus("PAST_DUE");
        subscriptionRepository.save(sub);

        log.info("Payment failed for sub: {}", lsSubId);
    }

    private String resolvePlan(String variantId) {
        if (variantId != null && variantId.equals(familyVariantId)) return "FAMILY";
        if (variantId != null && variantId.equals(plusVariantId)) return "PLUS";
        return "PLUS"; // default paid plan
    }

    private String mapStatus(String lsStatus) {
        return switch (lsStatus) {
            case "active" -> "ACTIVE";
            case "past_due" -> "PAST_DUE";
            case "cancelled" -> "CANCELLED";
            case "paused" -> "PAUSED";
            case "expired" -> "EXPIRED";
            default -> "ACTIVE";
        };
    }

    private void updatePeriod(Subscription sub, Map<String, Object> attributes) {
        try {
            String start = (String) attributes.get("renews_at");
            String end = (String) attributes.get("ends_at");
            if (start != null) sub.setCurrentPeriodStart(Instant.parse(start));
            if (end != null) sub.setCurrentPeriodEnd(Instant.parse(end));
        } catch (Exception e) {
            log.warn("Failed to parse period dates: {}", e.getMessage());
        }
    }

    private SubscriptionResponse toResponse(Subscription sub) {
        Integer trialDaysRemaining = null;
        if ("TRIAL".equals(sub.getPlan()) && sub.getCurrentPeriodEnd() != null) {
            long days = ChronoUnit.DAYS.between(Instant.now(), sub.getCurrentPeriodEnd());
            trialDaysRemaining = (int) Math.max(0, days);
        }

        return new SubscriptionResponse(
                sub.getPlan(),
                sub.getStatus(),
                sub.getCurrentPeriodStart(),
                sub.getCurrentPeriodEnd(),
                sub.getLemonsqueezyCustomerId() != null,
                sub.getCreatedAt(),
                trialDaysRemaining
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getNestedMap(Map<String, Object> map, String... keys) {
        Object current = map;
        for (String key : keys) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(key);
            } else {
                return null;
            }
        }
        return current instanceof Map ? (Map<String, Object>) current : null;
    }

    @SuppressWarnings("unchecked")
    private Object getNestedValue(Map<String, Object> map, String... keys) {
        Object current = map;
        for (String key : keys) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(key);
            } else {
                return null;
            }
        }
        return current;
    }

    private String extractJsonValue(String json, String key) {
        // Simple extraction for checkout URL
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        return end > start ? json.substring(start, end) : null;
    }

    private String planActivatedHtml(String name, String plan) {
        String planName = switch (plan) {
            case "PLUS" -> "Plus";
            case "FAMILY" -> "Perekond";
            default -> plan;
        };
        return """
                <div style="font-family: sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #1B4332;">%s plaan aktiveeritud!</h2>
                    <p>Tere, %s!</p>
                    <p>Sinu <strong>%s</strong> plaan on nüüd aktiivne. Aitäh toetamast!</p>
                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    <p style="color: #999; font-size: 12px;">Pärandiplaan — Sinu digitaalne pärand, turvaliselt korraldatud</p>
                </div>
                """.formatted(planName, name, planName);
    }

    public record SubscriptionResponse(
            String plan,
            String status,
            Instant currentPeriodStart,
            Instant currentPeriodEnd,
            boolean hasPaymentMethod,
            Instant createdAt,
            Integer trialDaysRemaining
    ) {}

    public record CheckoutResponse(
            String checkoutUrl,
            String plan
    ) {}
}
