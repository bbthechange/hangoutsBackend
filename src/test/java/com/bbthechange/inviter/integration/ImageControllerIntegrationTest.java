package com.bbthechange.inviter.integration;

import com.bbthechange.inviter.config.BaseIntegrationTest;
import com.bbthechange.inviter.dto.UploadUrlRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for ImageController endpoints
 * Tests complete HTTP request/response cycles with real DynamoDB via TestContainers
 */
@DisplayName("ImageController Integration Tests")
public class ImageControllerIntegrationTest extends BaseIntegrationTest {

    @Nested
    @DisplayName("GET /images/predefined - Predefined Images Integration Tests")
    class PredefinedImagesIntegrationTests {

        @Test
        @DisplayName("Should return predefined images without authentication")
        void getPredefinedImages_Success_NoAuth() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/images/predefined"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("Should return predefined images with authentication")
        void getPredefinedImages_Success_WithAuth() throws Exception {
            // Arrange
            String token = createDefaultUserAndGetToken();

            // Act & Assert
            mockMvc.perform(get("/images/predefined")
                    .header("Authorization", authHeader(token)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray());
        }
    }

    @Nested
    @DisplayName("POST /images/upload-url - Upload URL Generation Integration Tests")
    class UploadUrlGenerationIntegrationTests {

        @Test
        @DisplayName("Should generate upload URL with valid request and authentication")
        void getUploadUrl_Success_ValidRequest() throws Exception {
            // Arrange
            String token = createDefaultUserAndGetToken();
            
            UploadUrlRequest request = new UploadUrlRequest();
            request.setKey("events/user123/12345_abc_test.jpg");
            request.setContentType("image/jpeg");

            // Act & Assert
            mockMvc.perform(post("/images/upload-url")
                    .header("Authorization", authHeader(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.uploadUrl").exists())
                    .andExpect(jsonPath("$.key").value("events/user123/12345_abc_test.jpg"));
        }

        @Test
        @DisplayName("Should generate upload URL for PNG image")
        void getUploadUrl_Success_PngImage() throws Exception {
            // Arrange
            String token = createDefaultUserAndGetToken();
            
            UploadUrlRequest request = new UploadUrlRequest();
            request.setKey("events/user456/67890_def_test.png");
            request.setContentType("image/png");

            // Act & Assert
            mockMvc.perform(post("/images/upload-url")
                    .header("Authorization", authHeader(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.uploadUrl").exists())
                    .andExpect(jsonPath("$.key").value("events/user456/67890_def_test.png"));
        }

        @Test
        @DisplayName("Should reject upload URL generation without authentication")
        void getUploadUrl_Unauthorized_NoAuth() throws Exception {
            // Arrange
            UploadUrlRequest request = new UploadUrlRequest();
            request.setKey("events/user123/test.jpg");
            request.setContentType("image/jpeg");

            // Act & Assert
            mockMvc.perform(post("/images/upload-url")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should reject upload URL generation with invalid token")
        void getUploadUrl_Unauthorized_InvalidToken() throws Exception {
            // Arrange
            UploadUrlRequest request = new UploadUrlRequest();
            request.setKey("events/user123/test.jpg");
            request.setContentType("image/jpeg");

            // Act & Assert
            mockMvc.perform(post("/images/upload-url")
                    .header("Authorization", "Bearer invalid-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should handle missing key in request")
        void getUploadUrl_BadRequest_MissingKey() throws Exception {
            // Arrange
            String token = createDefaultUserAndGetToken();
            
            Map<String, String> incompleteRequest = Map.of("contentType", "image/jpeg");

            // Act & Assert
            mockMvc.perform(post("/images/upload-url")
                    .header("Authorization", authHeader(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(incompleteRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should handle missing content type in request")
        void getUploadUrl_BadRequest_MissingContentType() throws Exception {
            // Arrange
            String token = createDefaultUserAndGetToken();
            
            Map<String, String> incompleteRequest = Map.of("key", "events/user123/test.jpg");

            // Act & Assert
            mockMvc.perform(post("/images/upload-url")
                    .header("Authorization", authHeader(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(incompleteRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should handle empty key in request")
        void getUploadUrl_BadRequest_EmptyKey() throws Exception {
            // Arrange
            String token = createDefaultUserAndGetToken();
            
            UploadUrlRequest request = new UploadUrlRequest();
            request.setKey("");
            request.setContentType("image/jpeg");

            // Act & Assert
            mockMvc.perform(post("/images/upload-url")
                    .header("Authorization", authHeader(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should handle invalid content type")
        void getUploadUrl_BadRequest_InvalidContentType() throws Exception {
            // Arrange
            String token = createDefaultUserAndGetToken();
            
            UploadUrlRequest request = new UploadUrlRequest();
            request.setKey("events/user123/test.txt");
            request.setContentType("text/plain");

            // Act & Assert - assuming the service validates content types
            mockMvc.perform(post("/images/upload-url")
                    .header("Authorization", authHeader(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should handle malformed JSON request")
        void getUploadUrl_BadRequest_MalformedJson() throws Exception {
            // Arrange
            String token = createDefaultUserAndGetToken();

            // Act & Assert
            mockMvc.perform(post("/images/upload-url")
                    .header("Authorization", authHeader(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{invalid json}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Image Management Flow Integration Tests")
    class ImageManagementFlowTests {

        @Test
        @DisplayName("Should complete full image workflow")
        void completeImageWorkflow() throws Exception {
            // Step 1: Get predefined images (no auth required)
            mockMvc.perform(get("/images/predefined"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());

            // Step 2: Authenticate user
            String token = createDefaultUserAndGetToken();

            // Step 3: Generate upload URL for custom image
            UploadUrlRequest uploadRequest = new UploadUrlRequest();
            uploadRequest.setKey("events/testuser/workflow_test.jpg");
            uploadRequest.setContentType("image/jpeg");

            mockMvc.perform(post("/images/upload-url")
                    .header("Authorization", authHeader(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(uploadRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uploadUrl").exists())
                    .andExpect(jsonPath("$.key").value("events/testuser/workflow_test.jpg"));

            // Step 4: Verify we can still access predefined images after getting upload URL
            mockMvc.perform(get("/images/predefined")
                    .header("Authorization", authHeader(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("Should handle multiple upload URL requests")
        void multipleUploadUrlRequests() throws Exception {
            // Arrange
            String token = createDefaultUserAndGetToken();

            // Act & Assert - Generate multiple upload URLs
            for (int i = 1; i <= 3; i++) {
                UploadUrlRequest request = new UploadUrlRequest();
                request.setKey("events/testuser/multiple_" + i + ".jpg");
                request.setContentType("image/jpeg");

                mockMvc.perform(post("/images/upload-url")
                        .header("Authorization", authHeader(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.uploadUrl").exists())
                        .andExpect(jsonPath("$.key").value("events/testuser/multiple_" + i + ".jpg"));
            }
        }
    }
}