package com.bbthechange.inviter.config;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.Subsegment;
import com.bbthechange.inviter.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
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

        Subsegment jwtSubsegment = AWSXRay.beginSubsegment("JWT Authentication");
        try {
            String authHeader = request.getHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);

                Subsegment validationSubsegment = AWSXRay.beginSubsegment("JWT Validation");
                try {
                    if (jwtService.isTokenValid(token)) {
                        validationSubsegment.putAnnotation("valid", true);

                        String userId = jwtService.extractUserId(token);

                        UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userId, null, new ArrayList<>());
                        SecurityContextHolder.getContext().setAuthentication(authentication);

                        request.setAttribute("userId", userId);
                    } else {
                        validationSubsegment.putAnnotation("valid", false);
                    }
                } finally {
                    AWSXRay.endSubsegment();
                }
                // If token is invalid, don't set authentication - let AuthenticationEntryPoint handle it
            } else {
                jwtSubsegment.putAnnotation("has_bearer_token", false);
            }
        } finally {
            AWSXRay.endSubsegment();
        }

        filterChain.doFilter(request, response);
    }
}