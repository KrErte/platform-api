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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final int maxRequests;
    private static final long WINDOW_MS = 60_000; // 1 minute

    private final ConcurrentHashMap<String, Deque<Long>> requestCounts = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RateLimitFilter(@Value("${app.rate-limit.auth-requests-per-minute:10}") int maxRequests) {
        this.maxRequests = maxRequests;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/v1/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = extractClientIp(request);
        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW_MS;

        Deque<Long> timestamps = requestCounts.computeIfAbsent(ip, k -> new ConcurrentLinkedDeque<>());

        // Remove expired entries
        while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= maxRequests) {
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
        requestCounts.entrySet().removeIf(entry -> {
            Deque<Long> timestamps = entry.getValue();
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            return timestamps.isEmpty();
        });
    }
}
