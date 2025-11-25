package com.bbthechange.inviter.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Spring Cache configuration using Caffeine as the cache provider.
 *
 * This config enables declarative caching via @Cacheable and @CacheEvict annotations
 * on service methods. The primary use case is caching user profile summary data
 * to reduce DynamoDB read operations.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Configures the Caffeine-based cache manager with the "friendlyNames" cache.
     *
     * @return CacheManager configured with Caffeine cache specifications
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("friendlyNames");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.MINUTES)
                .maximumSize(10000) // 10,000 entries (~5MB memory footprint)
                .recordStats());
        return cacheManager;
    }
}
