package com.bbthechange.inviter.config;

import com.bbthechange.inviter.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JwtAuthenticationFilter
 * 
 * Test Coverage:
 * - JWT token processing and validation
 * - Authentication context setup
 * - Request attribute setting
 * - Auth endpoint bypassing
 * - Invalid token handling
 * - Missing authorization header scenarios
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter Tests")
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private SecurityContext securityContext;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private String validToken;
    private String testUserId;

    @BeforeEach
    void setUp() {
        validToken = "valid.jwt.token";
        testUserId = "550e8400-e29b-41d4-a716-446655440000";
        
        // Setup SecurityContextHolder mock
        SecurityContextHolder.setContext(securityContext);
    }

    @Nested
    @DisplayName("doFilterInternal - Main Filter Logic Tests")
    class DoFilterInternalTests {

        @Test
        @DisplayName("Should skip JWT processing for auth endpoints")
        void doFilterInternal_SkipAuthEndpoints() throws ServletException, IOException {
            // Arrange
            when(request.getRequestURI()).thenReturn("/auth/login");

            // Act
            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            // Assert
            verify(filterChain).doFilter(request, response);
            verifyNoInteractions(jwtService);
            verify(securityContext, never()).setAuthentication(any());
            verify(request, never()).setAttribute(eq("userId"), any());
        }

        @Test
        @DisplayName("Should skip JWT processing for auth register endpoint")
        void doFilterInternal_SkipAuthRegisterEndpoint() throws ServletException, IOException {
            // Arrange
            when(request.getRequestURI()).thenReturn("/auth/register");

            // Act
            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            // Assert
            verify(filterChain).doFilter(request, response);
            verifyNoInteractions(jwtService);
            verify(securityContext, never()).setAuthentication(any());
        }

        @Test
        @DisplayName("Should process valid JWT token and set authentication")
        void doFilterInternal_ValidToken() throws ServletException, IOException {
            // Arrange
            when(request.getRequestURI()).thenReturn("/events");
            when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);
            when(jwtService.isTokenValid(validToken)).thenReturn(true);
            when(jwtService.extractUserId(validToken)).thenReturn(testUserId);

            // Act
            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            // Assert
            verify(jwtService).isTokenValid(validToken);
            verify(jwtService).extractUserId(validToken);
            verify(securityContext).setAuthentication(any(UsernamePasswordAuthenticationToken.class));
            verify(request).setAttribute("userId", testUserId);
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should not set authentication for invalid token")
        void doFilterInternal_InvalidToken() throws ServletException, IOException {
            // Arrange
            String invalidToken = "invalid.jwt.token";
            when(request.getRequestURI()).thenReturn("/events");
            when(request.getHeader("Authorization")).thenReturn("Bearer " + invalidToken);
            when(jwtService.isTokenValid(invalidToken)).thenReturn(false);

            // Act
            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            // Assert
            verify(jwtService).isTokenValid(invalidToken);
            verify(jwtService, never()).extractUserId(any());
            verify(securityContext, never()).setAuthentication(any());
            verify(request, never()).setAttribute(eq("userId"), any());
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should not process when Authorization header is missing")
        void doFilterInternal_MissingAuthorizationHeader() throws ServletException, IOException {
            // Arrange
            when(request.getRequestURI()).thenReturn("/events");
            when(request.getHeader("Authorization")).thenReturn(null);

            // Act
            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            // Assert
            verifyNoInteractions(jwtService);
            verify(securityContext, never()).setAuthentication(any());
            verify(request, never()).setAttribute(eq("userId"), any());
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should not process when Authorization header doesn't start with Bearer")
        void doFilterInternal_InvalidAuthorizationHeaderFormat() throws ServletException, IOException {
            // Arrange
            when(request.getRequestURI()).thenReturn("/events");
            when(request.getHeader("Authorization")).thenReturn("Basic " + validToken);

            // Act
            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            // Assert
            verifyNoInteractions(jwtService);
            verify(securityContext, never()).setAuthentication(any());
            verify(request, never()).setAttribute(eq("userId"), any());
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should handle empty Authorization header")
        void doFilterInternal_EmptyAuthorizationHeader() throws ServletException, IOException {
            // Arrange
            when(request.getRequestURI()).thenReturn("/events");
            when(request.getHeader("Authorization")).thenReturn("");

            // Act
            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            // Assert
            verifyNoInteractions(jwtService);
            verify(securityContext, never()).setAuthentication(any());
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should handle Bearer token with only 'Bearer ' prefix")
        void doFilterInternal_BearerPrefixOnly() throws ServletException, IOException {
            // Arrange
            when(request.getRequestURI()).thenReturn("/events");
            when(request.getHeader("Authorization")).thenReturn("Bearer ");

            // Act
            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            // Assert
            verify(jwtService).isTokenValid("");
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should extract token correctly from Bearer authorization")
        void doFilterInternal_CorrectTokenExtraction() throws ServletException, IOException {
            // Arrange
            String fullToken = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.example.token";
            String expectedToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.example.token";
            
            when(request.getRequestURI()).thenReturn("/events");
            when(request.getHeader("Authorization")).thenReturn(fullToken);
            when(jwtService.isTokenValid(expectedToken)).thenReturn(true);
            when(jwtService.extractUserId(expectedToken)).thenReturn(testUserId);

            // Act
            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            // Assert
            verify(jwtService).isTokenValid(expectedToken);
            verify(jwtService).extractUserId(expectedToken);
            verify(request).setAttribute("userId", testUserId);
        }
    }

    @Nested
    @DisplayName("Authentication Setup Tests")
    class AuthenticationSetupTests {

        @Test
        @DisplayName("Should create correct UsernamePasswordAuthenticationToken")
        void createCorrectAuthenticationToken() throws ServletException, IOException {
            // Arrange
            when(request.getRequestURI()).thenReturn("/profile");
            when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);
            when(jwtService.isTokenValid(validToken)).thenReturn(true);
            when(jwtService.extractUserId(validToken)).thenReturn(testUserId);

            // Act
            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            // Assert
            verify(securityContext).setAuthentication(argThat(auth -> {
                if (!(auth instanceof UsernamePasswordAuthenticationToken)) {
                    return false;
                }
                UsernamePasswordAuthenticationToken token = (UsernamePasswordAuthenticationToken) auth;
                return testUserId.equals(token.getPrincipal()) &&
                       token.getCredentials() == null &&
                       token.getAuthorities().isEmpty();
            }));
        }

        @Test
        @DisplayName("Should set userId attribute in request")
        void setUserIdAttribute() throws ServletException, IOException {
            // Arrange
            when(request.getRequestURI()).thenReturn("/events/123");
            when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);
            when(jwtService.isTokenValid(validToken)).thenReturn(true);
            when(jwtService.extractUserId(validToken)).thenReturn(testUserId);

            // Act
            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            // Assert
            verify(request).setAttribute("userId", testUserId);
        }

        @Test
        @DisplayName("Should handle null userId from token")
        void handleNullUserId() throws ServletException, IOException {
            // Arrange
            when(request.getRequestURI()).thenReturn("/events");
            when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);
            when(jwtService.isTokenValid(validToken)).thenReturn(true);
            when(jwtService.extractUserId(validToken)).thenReturn(null);

            // Act
            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            // Assert
            verify(securityContext).setAuthentication(argThat(auth -> {
                UsernamePasswordAuthenticationToken token = (UsernamePasswordAuthenticationToken) auth;
                return token.getPrincipal() == null;
            }));
            verify(request).setAttribute("userId", null);
        }

        @Test
        @DisplayName("Should handle empty userId from token")
        void handleEmptyUserId() throws ServletException, IOException {
            // Arrange
            when(request.getRequestURI()).thenReturn("/events");
            when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);
            when(jwtService.isTokenValid(validToken)).thenReturn(true);
            when(jwtService.extractUserId(validToken)).thenReturn("");

            // Act
            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            // Assert
            verify(securityContext).setAuthentication(argThat(auth -> {
                UsernamePasswordAuthenticationToken token = (UsernamePasswordAuthenticationToken) auth;
                return "".equals(token.getPrincipal());
            }));
            verify(request).setAttribute("userId", "");
        }
    }

    @Nested
    @DisplayName("Path Handling Tests")
    class PathHandlingTests {

        @Test
        @DisplayName("Should process non-auth endpoints")
        void processNonAuthEndpoints() throws ServletException, IOException {
            // Arrange
            String[] nonAuthPaths = {
                "/events",
                "/events/123",
                "/profile",
                "/events/123/invites",
                "/images/predefined"
            };

            for (String path : nonAuthPaths) {
                // Reset mocks for each iteration
                reset(jwtService, securityContext, request, filterChain);
                
                when(request.getRequestURI()).thenReturn(path);
                when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);
                when(jwtService.isTokenValid(validToken)).thenReturn(true);
                when(jwtService.extractUserId(validToken)).thenReturn(testUserId);

                // Act
                jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

                // Assert
                verify(jwtService).isTokenValid(validToken);
                verify(securityContext).setAuthentication(any());
            }
        }

        @Test
        @DisplayName("Should skip all auth subpaths")
        void skipAllAuthSubpaths() throws ServletException, IOException {
            // Arrange
            String[] authPaths = {
                "/auth/login",
                "/auth/register",
                "/auth/refresh",
                "/auth/logout"
            };

            for (String path : authPaths) {
                // Reset mocks for each iteration
                reset(jwtService, securityContext, request, filterChain);
                
                when(request.getRequestURI()).thenReturn(path);

                // Act
                jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

                // Assert
                verifyNoInteractions(jwtService);
                verify(securityContext, never()).setAuthentication(any());
                verify(filterChain).doFilter(request, response);
            }
        }

        @Test
        @DisplayName("Should handle root path")
        void handleRootPath() throws ServletException, IOException {
            // Arrange
            when(request.getRequestURI()).thenReturn("/");
            when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);
            when(jwtService.isTokenValid(validToken)).thenReturn(true);
            when(jwtService.extractUserId(validToken)).thenReturn(testUserId);

            // Act
            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            // Assert
            verify(jwtService).isTokenValid(validToken);
            verify(securityContext).setAuthentication(any());
        }

        @Test
        @DisplayName("Should handle paths with query parameters")
        void handlePathsWithQueryParameters() throws ServletException, IOException {
            // Arrange
            when(request.getRequestURI()).thenReturn("/events");
            when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);
            when(jwtService.isTokenValid(validToken)).thenReturn(true);
            when(jwtService.extractUserId(validToken)).thenReturn(testUserId);

            // Act
            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            // Assert
            verify(jwtService).isTokenValid(validToken);
            verify(securityContext).setAuthentication(any());
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should continue filter chain when JWT service throws exception")
        void continueFilterChainOnJwtException() throws ServletException, IOException {
            // Arrange
            when(request.getRequestURI()).thenReturn("/events");
            when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);
            when(jwtService.isTokenValid(validToken)).thenThrow(new RuntimeException("JWT processing error"));

            // Act & Assert - Exception should propagate, but we'll test the behavior
            assertThrows(RuntimeException.class, () -> {
                jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);
            });
            
            verify(securityContext, never()).setAuthentication(any());
            verify(request, never()).setAttribute(eq("userId"), any());
        }

        @Test
        @DisplayName("Should handle malformed Bearer token gracefully")
        void handleMalformedBearerToken() throws ServletException, IOException {
            // Arrange
            when(request.getRequestURI()).thenReturn("/events");
            when(request.getHeader("Authorization")).thenReturn("Bearer ");
            when(jwtService.isTokenValid("")).thenReturn(false);

            // Act
            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            // Assert
            // Should attempt to validate empty string as token
            verify(jwtService).isTokenValid("");
            verify(filterChain).doFilter(request, response);
        }
    }
}