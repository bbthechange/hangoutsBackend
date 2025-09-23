package com.bbthechange.inviter.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RateLimitingService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingService.class);

    // For /auth/resend-code: 1 request per 60 seconds
    private final Cache<String, Instant> resendCodePerMinuteCache;
    
    // For /auth/resend-code: 5 requests per 1 hour
    private final Cache<String, AtomicInteger> resendCodePerHourCache;
    
    // For /auth/verify: 20 requests per 1 hour
    private final Cache<String, AtomicInteger> verifyPerHourCache;

    public RateLimitingService() {
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
     * Publish CloudWatch metric when rate limit is exceeded.
     * TODO: Implement actual CloudWatch integration
     */
    private void publishRateLimitMetric(String endpoint) {
        // For now, just log the metric
        // In production, this would publish to CloudWatch with:
        // Metric Name: RateLimitExceeded
        // Dimensions: endpoint={endpoint}
        logger.warn("CloudWatch Metric: RateLimitExceeded, Dimension: endpoint={}", endpoint);
    }
}