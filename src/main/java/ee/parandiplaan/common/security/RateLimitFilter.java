package ee.parandiplaan.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000; // 1 minute
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");

    private final List<RateLimitRule> rules;
    private final ConcurrentHashMap<String, Deque<Long>> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RateLimitFilter(
            @Value("${app.rate-limit.auth-requests-per-minute:10}") int authLimit,
            @Value("${app.rate-limit.shared-vault-requests-per-minute:20}") int sharedVaultLimit,
            @Value("${app.rate-limit.vault-requests-per-minute:30}") int vaultLimit,
            @Value("${app.rate-limit.admin-requests-per-minute:30}") int adminLimit,
            @Value("${app.rate-limit.users-requests-per-minute:20}") int usersLimit,
            @Value("${app.rate-limit.contacts-requests-per-minute:15}") int contactsLimit,
            @Value("${app.rate-limit.handover-requests-per-minute:10}") int handoverLimit) {
        // Order matters: more specific prefixes first
        this.rules = List.of(
                new RateLimitRule("/api/v1/auth/", authLimit, false),
                new RateLimitRule("/api/v1/shared-vault/", sharedVaultLimit, false),
                new RateLimitRule("/api/v1/vault/", vaultLimit, true),
                new RateLimitRule("/api/v1/admin/", adminLimit, false),
                new RateLimitRule("/api/v1/users/", usersLimit, false),
                new RateLimitRule("/api/v1/trusted-contacts/", contactsLimit, true),
                new RateLimitRule("/api/v1/handover-requests/", handoverLimit, false)
        );
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        RateLimitRule matched = null;
        for (RateLimitRule rule : rules) {
            if (path.startsWith(rule.prefix)) {
                if (!rule.writesOnly || WRITE_METHODS.contains(method)) {
                    matched = rule;
                }
                break;
            }
        }

        if (matched == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = extractClientIp(request);
        String bucketKey = matched.prefix + ":" + ip;
        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW_MS;

        Deque<Long> timestamps = buckets.computeIfAbsent(bucketKey, k -> new ConcurrentLinkedDeque<>());

        // Remove expired entries
        while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= matched.limit) {
            long oldestInWindow = timestamps.peekFirst();
            long retryAfterSeconds = Math.max(1, (oldestInWindow + WINDOW_MS - now) / 1000 + 1);

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));

            objectMapper.writeValue(response.getWriter(),
                    Map.of("error", "Liiga palju päringuid. Proovige hiljem uuesti.",
                           "retryAfterSeconds", retryAfterSeconds));
            return;
        }

        timestamps.addLast(now);
        filterChain.doFilter(request, response);
    }

    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @Scheduled(fixedRate = 300_000) // every 5 minutes
    public void cleanup() {
        long windowStart = System.currentTimeMillis() - WINDOW_MS;
        buckets.entrySet().removeIf(entry -> {
            Deque<Long> timestamps = entry.getValue();
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            return timestamps.isEmpty();
        });
    }

    private record RateLimitRule(String prefix, int limit, boolean writesOnly) {}
}
