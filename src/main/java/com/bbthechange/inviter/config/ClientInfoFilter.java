package com.bbthechange.inviter.config;

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

/**
 * Filter that extracts client information from request headers and makes it
 * available via request attributes and MDC for logging.
 *
 * Runs early in the filter chain to ensure client info is available to all
 * subsequent filters and handlers.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ClientInfoFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(ClientInfoFilter.class);

    // MDC keys for structured logging
    private static final String MDC_APP_VERSION = "appVersion";
    private static final String MDC_BUILD_NUMBER = "buildNumber";
    private static final String MDC_CLIENT_TYPE = "clientType";
    private static final String MDC_PLATFORM = "platform";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        ClientInfo clientInfo = ClientInfo.fromRequest(request);

        // Store in request attribute for controllers/services
        request.setAttribute(ClientInfo.REQUEST_ATTRIBUTE, clientInfo);

        // Add to MDC for structured logging
        try {
            if (clientInfo.appVersion() != null) {
                MDC.put(MDC_APP_VERSION, clientInfo.appVersion());
            }
            if (clientInfo.buildNumber() != null) {
                MDC.put(MDC_BUILD_NUMBER, clientInfo.buildNumber());
            }
            if (clientInfo.clientType() != null) {
                MDC.put(MDC_CLIENT_TYPE, clientInfo.clientType());
            }
            if (clientInfo.platform() != null) {
                MDC.put(MDC_PLATFORM, clientInfo.platform());
            }

            // Log at debug level for all requests with version info
            if (clientInfo.appVersion() != null && logger.isDebugEnabled()) {
                logger.debug("Request from client: {}", clientInfo.toLogString());
            }

            filterChain.doFilter(request, response);

        } finally {
            // Clean up MDC to prevent leaking to other requests
            MDC.remove(MDC_APP_VERSION);
            MDC.remove(MDC_BUILD_NUMBER);
            MDC.remove(MDC_CLIENT_TYPE);
            MDC.remove(MDC_PLATFORM);
        }
    }
}
