package com.bbthechange.inviter.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RateLimitingService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingService.class);

    private final MeterRegistry meterRegistry;

    // For /auth/resend-code: 1 request per 60 seconds
    private final Cache<String, Instant> resendCodePerMinuteCache;
    
    // For /auth/resend-code: 5 requests per 1 hour
    private final Cache<String, AtomicInteger> resendCodePerHourCache;
    
    // For /auth/verify: 20 requests per 1 hour
    private final Cache<String, AtomicInteger> verifyPerHourCache;

    // For /groups/invite/{code} preview: 60 requests per hour per IP
    private final Cache<String, AtomicInteger> invitePreviewPerIpCache;

    // For /groups/invite/{code} preview: 100 requests per hour per code
    private final Cache<String, AtomicInteger> invitePreviewPerCodeCache;

    // For /auth/request-password-reset: 1 request per hour per phone
    private final Cache<String, Instant> passwordResetRequestCache;

    // For /auth/verify-reset-code: 10 requests per hour per phone
    private final Cache<String, AtomicInteger> passwordResetVerifyCache;

    // For /auth/refresh: 10 requests per minute per user
    private final Cache<String, AtomicInteger> refreshRateLimitCache;

    public RateLimitingService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Cache for 1 minute rate limit - stores last request time
        this.resendCodePerMinuteCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(60))
                .maximumSize(10000)
                .build();
        
        // Cache for 1 hour rate limit counters - stores request count
        this.resendCodePerHourCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(1))
                .maximumSize(10000)
                .build();
        
        this.verifyPerHourCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(1))
                .maximumSize(10000)
                .build();

        this.invitePreviewPerIpCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(1))
                .maximumSize(10000)
                .build();

        this.invitePreviewPerCodeCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(1))
                .maximumSize(10000)
                .build();

        this.passwordResetRequestCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(1))
                .maximumSize(10000)
                .build();

        this.passwordResetVerifyCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(1))
                .maximumSize(10000)
                .build();

        this.refreshRateLimitCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(1))
                .maximumSize(10000)
                .build();
    }

    /**
     * Check if resend-code request is allowed for the given phone number.
     * Enforces: 1 request per 60 seconds AND 5 requests per 1 hour.
     */
    public boolean isResendCodeAllowed(String phoneNumber) {
        String key = "resend_" + phoneNumber;
        Instant now = Instant.now();
        
        // Check 1 request per 60 seconds limit
        Instant lastRequest = resendCodePerMinuteCache.getIfPresent(key);
        if (lastRequest != null) {
            logger.info("Rate limit exceeded for resend-code (60s limit): {}", phoneNumber);
            publishRateLimitMetric("/auth/resend-code");
            return false;
        }
        
        // Check 5 requests per 1 hour limit
        AtomicInteger hourlyCount = resendCodePerHourCache.getIfPresent(key);
        if (hourlyCount == null) {
            hourlyCount = new AtomicInteger(0);
            resendCodePerHourCache.put(key, hourlyCount);
        }
        
        if (hourlyCount.get() >= 5) {
            logger.info("Rate limit exceeded for resend-code (5/hour limit): {}", phoneNumber);
            publishRateLimitMetric("/auth/resend-code");
            return false;
        }
        
        // Update both caches
        resendCodePerMinuteCache.put(key, now);
        hourlyCount.incrementAndGet();
        
        return true;
    }

    /**
     * Check if verify request is allowed for the given phone number.
     * Enforces: 20 requests per 1 hour.
     */
    public boolean isVerifyAllowed(String phoneNumber) {
        String key = "verify_" + phoneNumber;
        
        AtomicInteger hourlyCount = verifyPerHourCache.getIfPresent(key);
        if (hourlyCount == null) {
            hourlyCount = new AtomicInteger(0);
            verifyPerHourCache.put(key, hourlyCount);
        }
        
        if (hourlyCount.get() >= 20) {
            logger.info("Rate limit exceeded for verify (20/hour limit): {}", phoneNumber);
            publishRateLimitMetric("/auth/verify");
            return false;
        }
        
        hourlyCount.incrementAndGet();
        return true;
    }

    /**
     * Check if invite preview request is allowed.
     * Enforces: 60 requests per hour per IP AND 100 requests per hour per code.
     *
     * @param ipAddress Client IP address
     * @param inviteCode The invite code being previewed
     * @return true if allowed, false if rate limit exceeded
     */
    public boolean isInvitePreviewAllowed(String ipAddress, String inviteCode) {
        // Check IP limit: 60/hour
        String ipKey = "preview_ip_" + ipAddress;
        AtomicInteger ipCount = invitePreviewPerIpCache.getIfPresent(ipKey);
        if (ipCount == null) {
            ipCount = new AtomicInteger(0);
            invitePreviewPerIpCache.put(ipKey, ipCount);
        }

        if (ipCount.get() >= 60) {
            logger.info("Rate limit exceeded for invite preview (IP): {}", ipAddress);
            publishRateLimitMetric("/groups/invite/preview");
            return false;
        }

        // Check code limit: 100/hour
        String codeKey = "preview_code_" + inviteCode;
        AtomicInteger codeCount = invitePreviewPerCodeCache.getIfPresent(codeKey);
        if (codeCount == null) {
            codeCount = new AtomicInteger(0);
            invitePreviewPerCodeCache.put(codeKey, codeCount);
        }

        if (codeCount.get() >= 100) {
            logger.info("Rate limit exceeded for invite preview (code): {}", inviteCode);
            publishRateLimitMetric("/groups/invite/preview");
            return false;
        }

        // Increment both counters
        ipCount.incrementAndGet();
        codeCount.incrementAndGet();

        return true;
    }

    /**
     * Check if password reset request is allowed for the given phone number.
     * Enforces: 1 request per hour per phone number.
     */
    public boolean isPasswordResetRequestAllowed(String phoneNumber) {
        String key = "password_reset_request_" + phoneNumber;
        Instant now = Instant.now();

        Instant lastRequest = passwordResetRequestCache.getIfPresent(key);
        if (lastRequest != null) {
            logger.info("Rate limit exceeded for password-reset-request (1/hour): {}", phoneNumber);
            publishRateLimitMetric("/auth/request-password-reset");
            return false;
        }

        passwordResetRequestCache.put(key, now);
        return true;
    }

    /**
     * Check if password reset code verification is allowed for the given phone number.
     * Enforces: 10 requests per hour per phone number.
     */
    public boolean isPasswordResetVerifyAllowed(String phoneNumber) {
        String key = "password_reset_verify_" + phoneNumber;

        AtomicInteger hourlyCount = passwordResetVerifyCache.getIfPresent(key);
        if (hourlyCount == null) {
            hourlyCount = new AtomicInteger(0);
            passwordResetVerifyCache.put(key, hourlyCount);
        }

        if (hourlyCount.get() >= 10) {
            logger.info("Rate limit exceeded for password-reset-verify (10/hour): {}", phoneNumber);
            publishRateLimitMetric("/auth/verify-reset-code");
            return false;
        }

        hourlyCount.incrementAndGet();
        return true;
    }

    /**
     * Check if refresh token request is allowed for the given user.
     * Enforces: 10 requests per minute per user.
     *
     * @param userId The user ID attempting to refresh
     * @return true if allowed, false if rate limit exceeded
     */
    public boolean isRefreshAllowed(String userId) {
        AtomicInteger count = refreshRateLimitCache.get(userId, k -> new AtomicInteger(0));
        if (count.incrementAndGet() > 10) {
            logger.warn("Refresh rate limit exceeded: user={}", userId);
            publishRateLimitMetric("/auth/refresh");
            return false;
        }
        return true;
    }

    /**
     * Publish metric when rate limit is exceeded.
     */
    private void publishRateLimitMetric(String endpoint) {
        meterRegistry.counter("rate_limit_exceeded_total", "endpoint", endpoint).increment();
        logger.warn("Rate limit exceeded for endpoint: {}", endpoint);
    }
}