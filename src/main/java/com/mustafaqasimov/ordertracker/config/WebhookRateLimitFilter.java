package com.mustafaqasimov.ordertracker.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
public class WebhookRateLimitFilter extends OncePerRequestFilter {

    private static final int CAPACITY = 30;              // max sorğu
    private static final Duration REFILL_PERIOD = Duration.ofMinutes(1);

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/webhooks/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String clientIp = resolveClientIp(request);
        Bucket bucket = buckets.computeIfAbsent(clientIp, ip -> newBucket());

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for IP {} on {}", clientIp, request.getRequestURI());
            response.setStatus(429); // HttpStatus.TOO_MANY_REQUESTS
            response.setContentType("application/json");
            response.getWriter().write("""
                    {"error": "Too many requests, please try again later."}""");
        }
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(CAPACITY,
                io.github.bucket4j.Refill.greedy(CAPACITY, REFILL_PERIOD));
        return Bucket.builder().addLimit(limit).build();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
