package com.bbthechange.inviter.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenCookieServiceTest {
    
    @Mock
    private HttpServletRequest request;
    
    private RefreshTokenCookieService cookieService;
    
    @BeforeEach
    void setUp() {
        cookieService = new RefreshTokenCookieService();
        ReflectionTestUtils.setField(cookieService, "cookieDomain", "");
        ReflectionTestUtils.setField(cookieService, "activeProfile", "prod");
    }
    
    @Test
    void createRefreshTokenCookie_InProduction_ShouldReturnSecureCookie() {
        // Given
        String refreshToken = "test-refresh-token";

        // When
        ResponseCookie cookie = cookieService.createRefreshTokenCookie(refreshToken);

        // Then
        assertThat(cookie.getName()).isEqualTo("refreshToken");
        assertThat(cookie.getValue()).isEqualTo(refreshToken);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/auth");
        assertThat(cookie.getSameSite()).isEqualTo("None"); // Cross-origin requires None
        assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(30 * 24 * 60 * 60); // 30 days
    }
    
    @Test
    void createRefreshTokenCookie_InDevelopment_ShouldReturnNonSecureCookie() {
        // Given
        ReflectionTestUtils.setField(cookieService, "activeProfile", "dev");
        String refreshToken = "test-refresh-token";
        
        // When
        ResponseCookie cookie = cookieService.createRefreshTokenCookie(refreshToken);
        
        // Then
        assertThat(cookie.getName()).isEqualTo("refreshToken");
        assertThat(cookie.getValue()).isEqualTo(refreshToken);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isFalse(); // Should be false in dev
        assertThat(cookie.getPath()).isEqualTo("/auth");
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
    }
    
    @Test
    void createRefreshTokenCookie_WithDomain_ShouldIncludeDomain() {
        // Given
        ReflectionTestUtils.setField(cookieService, "cookieDomain", "example.com");
        String refreshToken = "test-refresh-token";
        
        // When
        ResponseCookie cookie = cookieService.createRefreshTokenCookie(refreshToken);
        
        // Then
        assertThat(cookie.getDomain()).isEqualTo("example.com");
    }
    
    @Test
    void clearRefreshTokenCookie_ShouldReturnExpiredCookie() {
        // When
        ResponseCookie cookie = cookieService.clearRefreshTokenCookie();

        // Then
        assertThat(cookie.getName()).isEqualTo("refreshToken");
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/auth");
        assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(0); // Immediate expiration
        assertThat(cookie.getSameSite()).isEqualTo("None"); // Cross-origin requires None in prod
        assertThat(cookie.isSecure()).isTrue(); // Must be secure with SameSite=None
    }
    
    @Test
    void extractRefreshTokenFromCookies_WithRefreshTokenCookie_ShouldReturnToken() {
        // Given
        String expectedToken = "test-refresh-token";
        Cookie[] cookies = {
            new Cookie("otherCookie", "otherValue"),
            new Cookie("refreshToken", expectedToken),
            new Cookie("anotherCookie", "anotherValue")
        };
        
        when(request.getCookies()).thenReturn(cookies);
        
        // When
        String result = cookieService.extractRefreshTokenFromCookies(request);
        
        // Then
        assertThat(result).isEqualTo(expectedToken);
    }
    
    @Test
    void extractRefreshTokenFromCookies_WithoutRefreshTokenCookie_ShouldReturnNull() {
        // Given
        Cookie[] cookies = {
            new Cookie("otherCookie", "otherValue"),
            new Cookie("anotherCookie", "anotherValue")
        };
        
        when(request.getCookies()).thenReturn(cookies);
        
        // When
        String result = cookieService.extractRefreshTokenFromCookies(request);
        
        // Then
        assertThat(result).isNull();
    }
    
    @Test
    void extractRefreshTokenFromCookies_WithNoCookies_ShouldReturnNull() {
        // Given
        when(request.getCookies()).thenReturn(null);
        
        // When
        String result = cookieService.extractRefreshTokenFromCookies(request);
        
        // Then
        assertThat(result).isNull();
    }
    
    @Test
    void extractRefreshTokenFromCookies_WithEmptyCookieArray_ShouldReturnNull() {
        // Given
        Cookie[] cookies = {};
        when(request.getCookies()).thenReturn(cookies);
        
        // When
        String result = cookieService.extractRefreshTokenFromCookies(request);
        
        // Then
        assertThat(result).isNull();
    }
}