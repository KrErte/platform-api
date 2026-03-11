package ee.parandiplaan.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.mockito.Mockito.*;

class RateLimitFilterTest {

    private RateLimitFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(5, 20, 30, 30, 20, 15, 10);
        filterChain = mock(FilterChain.class);
    }

    @Test
    void requestsWithinLimitPassThrough() throws Exception {
        HttpServletRequest request = createMockRequest("/api/v1/auth/login", "192.168.1.1");
        HttpServletResponse response = mock(HttpServletResponse.class);

        for (int i = 0; i < 5; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }

        verify(filterChain, times(5)).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    void requestsExceedingLimitGet429() throws Exception {
        HttpServletRequest request = createMockRequest("/api/v1/auth/login", "192.168.1.2");

        // First 5 should pass
        for (int i = 0; i < 5; i++) {
            HttpServletResponse response = mock(HttpServletResponse.class);
            filter.doFilterInternal(request, response, filterChain);
        }

        // 6th should be blocked
        HttpServletResponse blockedResponse = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(blockedResponse.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilterInternal(request, blockedResponse, filterChain);

        verify(blockedResponse).setStatus(429);
        verify(filterChain, times(5)).doFilter(any(), any());
    }

    @Test
    void differentIpsHaveSeparateCounters() throws Exception {
        HttpServletRequest request1 = createMockRequest("/api/v1/auth/login", "10.0.0.1");
        HttpServletRequest request2 = createMockRequest("/api/v1/auth/login", "10.0.0.2");

        // Fill up IP1's limit
        for (int i = 0; i < 5; i++) {
            HttpServletResponse response = mock(HttpServletResponse.class);
            filter.doFilterInternal(request1, response, filterChain);
        }

        // IP2 should still work
        HttpServletResponse response2 = mock(HttpServletResponse.class);
        filter.doFilterInternal(request2, response2, filterChain);

        verify(response2, never()).setStatus(429);
    }

    @Test
    void nonAuthEndpointsAreNotRateLimited() throws Exception {
        HttpServletRequest request = createMockRequest("/api/v1/vault/entries", "192.168.1.3");
        HttpServletResponse response = mock(HttpServletResponse.class);

        for (int i = 0; i < 20; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }

        verify(response, never()).setStatus(429);
        verify(filterChain, times(20)).doFilter(request, response);
    }

    @Test
    void xForwardedForHeaderIsUsed() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/auth/register");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1, 10.0.0.1");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        // Fill up the X-Forwarded-For IP's limit
        for (int i = 0; i < 5; i++) {
            HttpServletResponse response = mock(HttpServletResponse.class);
            filter.doFilterInternal(request, response, filterChain);
        }

        // 6th request from same forwarded IP should be blocked
        HttpServletResponse blockedResponse = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(blockedResponse.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilterInternal(request, blockedResponse, filterChain);
        verify(blockedResponse).setStatus(429);

        // But a request from a different IP (no X-Forwarded-For) should work
        HttpServletRequest directRequest = createMockRequest("/api/v1/auth/register", "127.0.0.1");
        HttpServletResponse directResponse = mock(HttpServletResponse.class);
        filter.doFilterInternal(directRequest, directResponse, filterChain);
        verify(directResponse, never()).setStatus(429);
    }

    @Test
    void sharedVaultWithinLimitPassesThrough() throws Exception {
        HttpServletRequest request = createMockRequest("/api/v1/shared-vault/entries", "172.16.0.1");
        HttpServletResponse response = mock(HttpServletResponse.class);

        for (int i = 0; i < 20; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }

        verify(filterChain, times(20)).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    void sharedVaultExceedingLimitGet429() throws Exception {
        HttpServletRequest request = createMockRequest("/api/v1/shared-vault/info", "172.16.0.2");

        // First 20 should pass
        for (int i = 0; i < 20; i++) {
            HttpServletResponse response = mock(HttpServletResponse.class);
            filter.doFilterInternal(request, response, filterChain);
        }

        // 21st should be blocked
        HttpServletResponse blockedResponse = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(blockedResponse.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilterInternal(request, blockedResponse, filterChain);
        verify(blockedResponse).setStatus(429);
    }

    @Test
    void sharedVaultAndAuthHaveSeparateCounters() throws Exception {
        String ip = "172.16.0.3";
        HttpServletRequest authRequest = createMockRequest("/api/v1/auth/login", ip);
        HttpServletRequest sharedRequest = createMockRequest("/api/v1/shared-vault/entries", ip);

        // Fill up auth limit (5)
        for (int i = 0; i < 5; i++) {
            HttpServletResponse response = mock(HttpServletResponse.class);
            filter.doFilterInternal(authRequest, response, filterChain);
        }

        // Shared-vault should still work from same IP
        HttpServletResponse sharedResponse = mock(HttpServletResponse.class);
        filter.doFilterInternal(sharedRequest, sharedResponse, filterChain);
        verify(sharedResponse, never()).setStatus(429);
    }

    private HttpServletRequest createMockRequest(String uri, String remoteAddr) {
        return createMockRequest(uri, remoteAddr, "GET");
    }

    private HttpServletRequest createMockRequest(String uri, String remoteAddr, String method) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getMethod()).thenReturn(method);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn(remoteAddr);
        return request;
    }
}
