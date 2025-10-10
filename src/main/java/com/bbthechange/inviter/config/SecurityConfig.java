package com.bbthechange.inviter.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    
    @Autowired
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(jwtAuthenticationEntryPoint))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/health").permitAll() // Allow health check endpoints
                .requestMatchers("/auth/register", "/auth/login", "/auth/refresh", "/auth/logout", "/auth/resend-code", "/auth/verify").permitAll()
                .requestMatchers("/images/predefined").permitAll()
                .requestMatchers("/hangouts/time-options").permitAll()
                .requestMatchers("/hiking/**").permitAll() // Allow public hiking trail search
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(
            "http://localhost:3000", // Local development (AngularJS)
            "http://localhost:4200", // Local development (Angular 19)
            "http://localhost:8080", // Swagger UI
            "https://d3lm7si4v7xvcj.cloudfront.net", // Production CloudFront domain (legacy)
            "https://d1713f2ygzp5es.cloudfront.net", // Production CloudFront domain (current)
            "https://d3e93y6prxzuq0.cloudfront.net", // Staging CloudFront domain
            "https://api.inviter.app", // Production API Gateway domain
            "https://v7ihwy6uv9.execute-api.us-west-2.amazonaws.com", // Staging API Gateway domain
            "http://inviter-webapp-staging-575960429871.s3-website.us-west-2.amazonaws.com" // Staging webapp S3 website
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList(
            "Content-Type", 
            "Authorization", 
            "X-Requested-With",
            "X-Client-Type"           // Add client type header
        ));
        configuration.setAllowCredentials(true);  // Required for HttpOnly cookies
        configuration.setMaxAge(3600L);           // Cache preflight for 1 hour
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}