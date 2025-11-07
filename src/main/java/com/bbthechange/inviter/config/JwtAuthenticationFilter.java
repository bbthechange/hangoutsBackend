package com.bbthechange.inviter.config;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.Subsegment;
import com.bbthechange.inviter.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Autowired
    private JwtService jwtService;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        // Skip JWT processing for auth endpoints
        if (requestPath.startsWith("/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Only trace if X-Ray segment is available (prevents errors when filter runs before X-Ray servlet filter)
        boolean xrayAvailable = AWSXRay.getCurrentSegmentOptional().isPresent();
        Subsegment jwtSubsegment = xrayAvailable ? AWSXRay.beginSubsegment("JWT Authentication") : null;
        try {
            String authHeader = request.getHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);

                Subsegment validationSubsegment = xrayAvailable ? AWSXRay.beginSubsegment("JWT Validation") : null;
                try {
                    if (jwtService.isTokenValid(token)) {
                        if (validationSubsegment != null) {
                            validationSubsegment.putAnnotation("valid", true);
                        }

                        String userId = jwtService.extractUserId(token);

                        UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userId, null, new ArrayList<>());
                        SecurityContextHolder.getContext().setAuthentication(authentication);

                        request.setAttribute("userId", userId);
                    } else {
                        if (validationSubsegment != null) {
                            validationSubsegment.putAnnotation("valid", false);
                        }
                        logger.warn("Invalid or expired JWT token for request: {} {}", request.getMethod(), requestPath);
                    }
                } finally {
                    if (validationSubsegment != null) {
                        AWSXRay.endSubsegment();
                    }
                }
                // If token is invalid, don't set authentication - let AuthenticationEntryPoint handle it
            } else {
                if (jwtSubsegment != null) {
                    jwtSubsegment.putAnnotation("has_bearer_token", false);
                }
            }
        } finally {
            if (jwtSubsegment != null) {
                AWSXRay.endSubsegment();
            }
        }

        filterChain.doFilter(request, response);
    }
}