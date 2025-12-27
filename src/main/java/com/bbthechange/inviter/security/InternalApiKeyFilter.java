package com.bbthechange.inviter.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

import java.io.IOException;

/**
 * Filter for authenticating internal API requests from EventBridge Scheduler.
 * Validates X-Api-Key header against value stored in AWS Parameter Store.
 * Only applies to /internal/** endpoints.
 */
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(InternalApiKeyFilter.class);
    private static final String API_KEY_HEADER = "X-Api-Key";
    private static final String INTERNAL_PATH_PREFIX = "/internal/";

    @Value("${aws.region:us-west-2}")
    private String awsRegion;

    @Value("${internal.api-key-parameter-name:/inviter/scheduler/internal-api-key}")
    private String apiKeyParameterName;

    // Cached API key to avoid repeated SSM calls
    private volatile String cachedApiKey;
    private volatile long cacheExpiry = 0;
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only filter requests to /internal/** endpoints
        return !request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String providedApiKey = request.getHeader(API_KEY_HEADER);

        if (providedApiKey == null || providedApiKey.isBlank()) {
            logger.warn("Missing API key for internal endpoint: {}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Missing API key\"}");
            return;
        }

        String expectedApiKey = getApiKey();
        if (expectedApiKey == null) {
            logger.error("Failed to retrieve API key from Parameter Store");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Internal configuration error\"}");
            return;
        }

        if (!expectedApiKey.equals(providedApiKey)) {
            logger.warn("Invalid API key for internal endpoint: {}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Invalid API key\"}");
            return;
        }

        // API key valid, continue filter chain
        filterChain.doFilter(request, response);
    }

    /**
     * Get API key from cache or fetch from SSM Parameter Store.
     */
    private String getApiKey() {
        long now = System.currentTimeMillis();
        if (cachedApiKey != null && now < cacheExpiry) {
            return cachedApiKey;
        }

        synchronized (this) {
            // Double-check after acquiring lock
            if (cachedApiKey != null && System.currentTimeMillis() < cacheExpiry) {
                return cachedApiKey;
            }

            try (SsmClient ssmClient = SsmClient.builder()
                    .region(Region.of(awsRegion))
                    .build()) {

                GetParameterRequest parameterRequest = GetParameterRequest.builder()
                        .name(apiKeyParameterName)
                        .withDecryption(true)
                        .build();

                GetParameterResponse parameterResponse = ssmClient.getParameter(parameterRequest);
                cachedApiKey = parameterResponse.parameter().value();
                cacheExpiry = System.currentTimeMillis() + CACHE_TTL_MS;

                logger.debug("Refreshed internal API key from Parameter Store");
                return cachedApiKey;

            } catch (Exception e) {
                logger.error("Failed to retrieve API key from Parameter Store: {}", e.getMessage());
                return null;
            }
        }
    }
}
