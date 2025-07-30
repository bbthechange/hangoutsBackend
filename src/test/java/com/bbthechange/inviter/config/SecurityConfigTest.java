package com.bbthechange.inviter.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.*;

class SecurityConfigTest {

    @Test
    void corsConfigurationSource_ShouldConfigureAllowedOrigins() {
        SecurityConfig securityConfig = new SecurityConfig();
        CorsConfigurationSource corsConfigurationSource = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest();
        var configuration = corsConfigurationSource.getCorsConfiguration(request);
        
        assertNotNull(configuration);
        assertTrue(configuration.getAllowedOrigins().contains("http://localhost:3000"));
        assertTrue(configuration.getAllowedOrigins().contains("https://d3lm7si4v7xvcj.cloudfront.net"));
        assertTrue(configuration.getAllowedOrigins().contains("https://api.inviter.app"));
    }

    @Test
    void corsConfigurationSource_ShouldConfigureAllowedMethods() {
        SecurityConfig securityConfig = new SecurityConfig();
        CorsConfigurationSource corsConfigurationSource = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest();
        var configuration = corsConfigurationSource.getCorsConfiguration(request);
        
        assertNotNull(configuration);
        assertTrue(configuration.getAllowedMethods().contains("GET"));
        assertTrue(configuration.getAllowedMethods().contains("POST"));
        assertTrue(configuration.getAllowedMethods().contains("PUT"));
        assertTrue(configuration.getAllowedMethods().contains("DELETE"));
        assertTrue(configuration.getAllowedMethods().contains("OPTIONS"));
    }

    @Test
    void corsConfigurationSource_ShouldConfigureAllowedHeaders() {
        SecurityConfig securityConfig = new SecurityConfig();
        CorsConfigurationSource corsConfigurationSource = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest();
        var configuration = corsConfigurationSource.getCorsConfiguration(request);
        
        assertNotNull(configuration);
        assertTrue(configuration.getAllowedHeaders().contains("Content-Type"));
        assertTrue(configuration.getAllowedHeaders().contains("Authorization"));
        assertTrue(configuration.getAllowedHeaders().contains("X-Requested-With"));
    }

    @Test
    void corsConfigurationSource_ShouldAllowCredentials() {
        SecurityConfig securityConfig = new SecurityConfig();
        CorsConfigurationSource corsConfigurationSource = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest();
        var configuration = corsConfigurationSource.getCorsConfiguration(request);
        
        assertNotNull(configuration);
        assertTrue(configuration.getAllowCredentials());
    }
}