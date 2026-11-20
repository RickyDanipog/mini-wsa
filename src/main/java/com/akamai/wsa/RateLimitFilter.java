package com.akamai.wsa;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class RateLimitFilter extends OncePerRequestFilter {
    private final Clock clock;
    private final int limit;
    private final Map<String, Integer> counts = new HashMap<>();
    private long currentMinute = Long.MIN_VALUE;

    RateLimitFilter(Clock clock, @Value("${wsa.rate-limit.requests-per-minute:100}") int limit) {
        this.clock = clock;
        this.limit = limit;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !("GET".equals(request.getMethod())
                && (path.equals("/v1/events/samples") || path.startsWith("/v1/stats/")));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long second = clock.instant().getEpochSecond();
        if (allow(request.getRemoteAddr(), Math.floorDiv(second, 60))) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(60 - Math.floorMod(second, 60)));
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":{\"code\":\"RATE_LIMIT_EXCEEDED\"}}");
    }

    private synchronized boolean allow(String address, long minute) {
        if (minute != currentMinute) {
            counts.clear();
            currentMinute = minute;
        }
        int count = counts.getOrDefault(address, 0);
        if (count >= limit) {
            return false;
        }
        counts.put(address, count + 1);
        return true;
    }
}
