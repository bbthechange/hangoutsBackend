package com.bbthechange.inviter.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;

@Service
public class RefreshTokenCookieService {

    @Value("${app.cookie.domain:}")
    private String cookieDomain;

    @Value("${spring.profiles.active:prod}")
    private String activeProfile;
    
    /**
     * Create HttpOnly refresh token cookie with appropriate security settings
     */
    public ResponseCookie createRefreshTokenCookie(String refreshToken) {
        ResponseCookie.ResponseCookieBuilder cookieBuilder = ResponseCookie.from("refreshToken", refreshToken)
            .httpOnly(true)                           // Prevent XSS access
            .path("/auth")                            // Limit scope to auth endpoints
            .maxAge(Duration.ofDays(30));             // 30 days

        // Environment-specific settings
        if ("dev".equals(activeProfile) || "test".equals(activeProfile)) {
            cookieBuilder.secure(false);              // Allow HTTP in development
            cookieBuilder.sameSite("Lax");            // Same-origin in dev, Lax is fine
        } else {
            cookieBuilder.secure(true);               // HTTPS only in production
            cookieBuilder.sameSite("None");           // Cross-origin requires None (CORS protects against CSRF)
        }

        if (!cookieDomain.isEmpty()) {
            cookieBuilder.domain(cookieDomain);       // Set domain for production
        }

        return cookieBuilder.build();
    }
    
    /**
     * Create cookie that clears the refresh token (for logout)
     */
    public ResponseCookie clearRefreshTokenCookie() {
        ResponseCookie.ResponseCookieBuilder cookieBuilder = ResponseCookie.from("refreshToken", "")
            .httpOnly(true)
            .path("/auth")
            .maxAge(0);                               // Immediate expiration

        if ("dev".equals(activeProfile) || "test".equals(activeProfile)) {
            cookieBuilder.secure(false);
            cookieBuilder.sameSite("Lax");
        } else {
            cookieBuilder.secure(true);
            cookieBuilder.sameSite("None");
        }

        return cookieBuilder.build();
    }
    
    /**
     * Extract refresh token from HttpOnly cookie
     */
    public String extractRefreshTokenFromCookies(HttpServletRequest request) {
        if (request.getCookies() != null) {
            return Arrays.stream(request.getCookies())
                .filter(cookie -> "refreshToken".equals(cookie.getName()))
                .findFirst()
                .map(Cookie::getValue)
                .orElse(null);
        }
        return null;
    }
}