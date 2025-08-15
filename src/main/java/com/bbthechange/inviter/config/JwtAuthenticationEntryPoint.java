package com.bbthechange.inviter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        // Check if this is a JWT-related authentication failure
        String authHeader = request.getHeader("Authorization");
        boolean hasJwtToken = authHeader != null && authHeader.startsWith("Bearer ");

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");

        Map<String, String> errorResponse = new HashMap<>();
        
        if (hasJwtToken) {
            // This indicates an expired or invalid JWT token
            errorResponse.put("error", "Token expired or invalid");
            errorResponse.put("code", "TOKEN_EXPIRED");
        } else {
            // This indicates missing authentication
            errorResponse.put("error", "Authentication required");
            errorResponse.put("code", "AUTHENTICATION_REQUIRED");
        }

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}