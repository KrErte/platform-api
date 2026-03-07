package ee.parandiplaan.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/webhooks/lemonsqueezy")
@RequiredArgsConstructor
@Slf4j
public class LemonSqueezyWebhookController {

    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;

    @Value("${lemonsqueezy.webhook-secret:}")
    private String webhookSecret;

    @PostMapping
    public ResponseEntity<String> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Signature", required = false) String signature) {

        // Verify signature if webhook secret is configured
        if (webhookSecret != null && !webhookSecret.isBlank()) {
            if (signature == null || !verifySignature(rawBody, signature)) {
                log.warn("Invalid webhook signature");
                return ResponseEntity.status(401).body("Invalid signature");
            }
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(rawBody, Map.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> meta = (Map<String, Object>) payload.get("meta");
            String eventName = meta != null ? (String) meta.get("event_name") : null;

            if (eventName == null) {
                log.warn("No event_name in webhook");
                return ResponseEntity.badRequest().body("Missing event_name");
            }

            log.info("LemonSqueezy webhook received: {}", eventName);
            subscriptionService.handleWebhookEvent(eventName, payload);

            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            log.error("Webhook processing failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Processing failed");
        }
    }

    private boolean verifySignature(String payload, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computed = HexFormat.of().formatHex(hash);
            return computed.equalsIgnoreCase(signature);
        } catch (Exception e) {
            log.error("Signature verification failed: {}", e.getMessage());
            return false;
        }
    }
}
