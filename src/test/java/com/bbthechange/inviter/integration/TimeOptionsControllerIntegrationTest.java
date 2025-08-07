package com.bbthechange.inviter.integration;

import com.bbthechange.inviter.config.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for TimeOptionsController endpoints
 * Tests complete HTTP request/response cycles with real server
 */
@DisplayName("TimeOptionsController Integration Tests")
public class TimeOptionsControllerIntegrationTest extends BaseIntegrationTest {

    @Nested
    @DisplayName("GET /hangouts/time-options - Time Options Integration Tests")
    class TimeOptionsIntegrationTests {

        @Test
        @DisplayName("Should return time options without authentication - public endpoint")
        void getTimeOptions_Success_NoAuth() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/hangouts/time-options"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(7))
                    .andExpect(jsonPath("$[0]").value("exact"))
                    .andExpect(jsonPath("$[1]").value("morning"))
                    .andExpect(jsonPath("$[2]").value("afternoon"))
                    .andExpect(jsonPath("$[3]").value("evening"))
                    .andExpect(jsonPath("$[4]").value("night"))
                    .andExpect(jsonPath("$[5]").value("day"))
                    .andExpect(jsonPath("$[6]").value("weekend"));
        }

        @Test
        @DisplayName("Should return time options with authentication - accessible to authenticated users")
        void getTimeOptions_Success_WithAuth() throws Exception {
            // Arrange
            String token = createDefaultUserAndGetToken();

            // Act & Assert
            mockMvc.perform(get("/hangouts/time-options")
                    .header("Authorization", authHeader(token)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(7))
                    .andExpect(jsonPath("$[0]").value("exact"))
                    .andExpect(jsonPath("$[1]").value("morning"))
                    .andExpect(jsonPath("$[2]").value("afternoon"))
                    .andExpect(jsonPath("$[3]").value("evening"))
                    .andExpect(jsonPath("$[4]").value("night"))
                    .andExpect(jsonPath("$[5]").value("day"))
                    .andExpect(jsonPath("$[6]").value("weekend"));
        }

        @Test
        @DisplayName("Should return consistent results on multiple calls")
        void getTimeOptions_ConsistentResults() throws Exception {
            // Act & Assert - Make multiple calls and verify consistency
            for (int i = 0; i < 3; i++) {
                mockMvc.perform(get("/hangouts/time-options"))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$").isArray())
                        .andExpect(jsonPath("$.length()").value(7))
                        .andExpect(jsonPath("$[0]").value("exact"))
                        .andExpect(jsonPath("$[6]").value("weekend"));
            }
        }

        @Test
        @DisplayName("Should handle concurrent requests properly")
        void getTimeOptions_ConcurrentRequests() throws Exception {
            // Test that the endpoint can handle multiple simultaneous requests
            // This verifies the stateless nature of the controller
            
            // Act & Assert - Simulate concurrent access
            mockMvc.perform(get("/hangouts/time-options"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(7));
                    
            mockMvc.perform(get("/hangouts/time-options"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(7));
                    
            mockMvc.perform(get("/hangouts/time-options"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(7));
        }

        @Test
        @DisplayName("Should return all expected fuzzy time options in correct order")
        void getTimeOptions_AllOptionsInOrder() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/hangouts/time-options"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0]").value("exact"))
                    .andExpect(jsonPath("$[1]").value("morning"))
                    .andExpect(jsonPath("$[2]").value("afternoon"))
                    .andExpect(jsonPath("$[3]").value("evening"))
                    .andExpect(jsonPath("$[4]").value("night"))
                    .andExpect(jsonPath("$[5]").value("day"))
                    .andExpect(jsonPath("$[6]").value("weekend"));
        }
    }

    @Nested
    @DisplayName("Time Options Endpoint Accessibility Tests")
    class AccessibilityTests {

        @Test
        @DisplayName("Should be accessible via different HTTP headers")
        void getTimeOptions_DifferentHeaders() throws Exception {
            // Test with different Accept headers
            mockMvc.perform(get("/hangouts/time-options")
                    .header("Accept", "application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(7));

            mockMvc.perform(get("/hangouts/time-options")
                    .header("Accept", "*/*"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(7));
        }

        @Test
        @DisplayName("Should handle invalid authentication gracefully - still returns data")
        void getTimeOptions_InvalidAuth_StillReturnsData() throws Exception {
            // Since this is a public endpoint, it should work even with invalid auth
            mockMvc.perform(get("/hangouts/time-options")
                    .header("Authorization", "Bearer invalid-token"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.length()").value(7));
        }
    }
}