package ee.parandiplaan.subscription;

import ee.parandiplaan.common.security.CurrentUser;
import ee.parandiplaan.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping
    public ResponseEntity<SubscriptionService.SubscriptionResponse> getSubscription(
            @CurrentUser User user) {
        return ResponseEntity.ok(subscriptionService.getSubscription(user));
    }

    @PostMapping("/checkout")
    public ResponseEntity<SubscriptionService.CheckoutResponse> createCheckout(
            @CurrentUser User user,
            @RequestBody Map<String, String> body) {
        String plan = body.get("plan");
        if (plan == null || plan.isBlank()) {
            throw new IllegalArgumentException("Plaan on kohustuslik");
        }
        return ResponseEntity.ok(subscriptionService.createCheckout(user, plan));
    }
}
