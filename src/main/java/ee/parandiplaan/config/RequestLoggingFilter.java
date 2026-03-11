package ee.parandiplaan.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // Skip noisy endpoints
        if (path.startsWith("/actuator/") || path.startsWith("/assets/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String requestId = UUID.randomUUID().toString().substring(0, 8);
        String method = request.getMethod();

        MDC.put("requestId", requestId);
        MDC.put("method", method);
        MDC.put("path", path);

        response.setHeader("X-Request-Id", requestId);

        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - start;
            log.info("HTTP {} {} {} {}ms", method, path, response.getStatus(), duration);
            MDC.clear();
        }
    }
}
