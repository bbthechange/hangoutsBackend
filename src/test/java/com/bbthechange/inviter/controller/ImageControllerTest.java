package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.PredefinedImageResponse;
import com.bbthechange.inviter.dto.UploadUrlRequest;
import com.bbthechange.inviter.dto.UploadUrlResponse;
import com.bbthechange.inviter.service.S3Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ImageController
 * 
 * Test Coverage:
 * - GET /images/predefined - Retrieve predefined images
 * - POST /images/upload-url - Generate presigned upload URLs
 * - Error handling scenarios
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ImageController Tests")
class ImageControllerTest {

    @Mock
    private S3Service s3Service;

    @InjectMocks
    private ImageController imageController;

    private List<PredefinedImageResponse> mockPredefinedImages;
    private UploadUrlRequest uploadUrlRequest;

    @BeforeEach
    void setUp() {
        mockPredefinedImages = Arrays.asList(
            new PredefinedImageResponse("birthday", "predefined/birthday.jpg", "Birthday Party"),
            new PredefinedImageResponse("wedding", "predefined/wedding.jpg", "Wedding")
        );

        uploadUrlRequest = new UploadUrlRequest();
        uploadUrlRequest.setKey("events/user123/12345_abc_image.jpg");
        uploadUrlRequest.setContentType("image/jpeg");
    }

    @Nested
    @DisplayName("GET /images/predefined - Predefined Images Tests")
    class GetPredefinedImagesTests {

        @Test
        @DisplayName("Should return predefined images successfully")
        void getPredefinedImages_Success() {
            // Arrange
            when(s3Service.getPredefinedImages()).thenReturn(mockPredefinedImages);

            // Act
            ResponseEntity<List<PredefinedImageResponse>> response = imageController.getPredefinedImages();

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(2, response.getBody().size());
            
            List<PredefinedImageResponse> responseBody = response.getBody();
            assertEquals("birthday", responseBody.get(0).getKey());
            assertEquals("Birthday Party", responseBody.get(0).getDisplayName());
            assertEquals("wedding", responseBody.get(1).getKey());
            assertEquals("Wedding", responseBody.get(1).getDisplayName());

            verify(s3Service).getPredefinedImages();
        }

        @Test
        @DisplayName("Should return empty list when no predefined images")
        void getPredefinedImages_EmptyList() {
            // Arrange
            when(s3Service.getPredefinedImages()).thenReturn(Arrays.asList());

            // Act
            ResponseEntity<List<PredefinedImageResponse>> response = imageController.getPredefinedImages();

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().isEmpty());

            verify(s3Service).getPredefinedImages();
        }

        @Test
        @DisplayName("Should propagate service exceptions")
        void getPredefinedImages_ServiceException() {
            // Arrange
            when(s3Service.getPredefinedImages()).thenThrow(new RuntimeException("S3 service error"));

            // Act & Assert
            RuntimeException exception = assertThrows(RuntimeException.class, () ->
                imageController.getPredefinedImages()
            );
            
            assertEquals("S3 service error", exception.getMessage());
            verify(s3Service).getPredefinedImages();
        }
    }

    @Nested
    @DisplayName("POST /images/upload-url - Upload URL Generation Tests")
    class GetUploadUrlTests {

        @Test
        @DisplayName("Should generate upload URL successfully")
        void getUploadUrl_Success() {
            // Arrange
            String expectedUrl = "https://test-bucket.s3.amazonaws.com/events/user123/12345_abc_image.jpg?X-Amz-Credential=test";
            when(s3Service.generatePresignedUploadUrl("events/user123/12345_abc_image.jpg", "image/jpeg"))
                    .thenReturn(expectedUrl);

            // Act
            ResponseEntity<UploadUrlResponse> response = imageController.getUploadUrl(uploadUrlRequest);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            
            UploadUrlResponse responseBody = response.getBody();
            assertEquals(expectedUrl, responseBody.getUploadUrl());
            assertEquals("events/user123/12345_abc_image.jpg", responseBody.getKey());

            verify(s3Service).generatePresignedUploadUrl("events/user123/12345_abc_image.jpg", "image/jpeg");
        }

        @Test
        @DisplayName("Should handle PNG content type")
        void getUploadUrl_PngContentType() {
            // Arrange
            uploadUrlRequest.setContentType("image/png");
            String expectedUrl = "https://test-bucket.s3.amazonaws.com/events/user123/12345_abc_image.jpg?signed";
            when(s3Service.generatePresignedUploadUrl("events/user123/12345_abc_image.jpg", "image/png"))
                    .thenReturn(expectedUrl);

            // Act
            ResponseEntity<UploadUrlResponse> response = imageController.getUploadUrl(uploadUrlRequest);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(expectedUrl, response.getBody().getUploadUrl());

            verify(s3Service).generatePresignedUploadUrl("events/user123/12345_abc_image.jpg", "image/png");
        }

        @Test
        @DisplayName("Should handle different key formats")
        void getUploadUrl_DifferentKeyFormats() {
            // Arrange
            uploadUrlRequest.setKey("events/user456/67890_xyz_photo.png");
            String expectedUrl = "https://test-bucket.s3.amazonaws.com/events/user456/67890_xyz_photo.png?signed";
            when(s3Service.generatePresignedUploadUrl("events/user456/67890_xyz_photo.png", "image/jpeg"))
                    .thenReturn(expectedUrl);

            // Act
            ResponseEntity<UploadUrlResponse> response = imageController.getUploadUrl(uploadUrlRequest);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("events/user456/67890_xyz_photo.png", response.getBody().getKey());

            verify(s3Service).generatePresignedUploadUrl("events/user456/67890_xyz_photo.png", "image/jpeg");
        }

        @Test
        @DisplayName("Should propagate service exceptions")
        void getUploadUrl_ServiceException() {
            // Arrange
            when(s3Service.generatePresignedUploadUrl(anyString(), anyString()))
                    .thenThrow(new RuntimeException("Failed to generate presigned URL"));

            // Act & Assert
            RuntimeException exception = assertThrows(RuntimeException.class, () ->
                imageController.getUploadUrl(uploadUrlRequest)
            );
            
            assertEquals("Failed to generate presigned URL", exception.getMessage());
            verify(s3Service).generatePresignedUploadUrl("events/user123/12345_abc_image.jpg", "image/jpeg");
        }

        @Test
        @DisplayName("Should handle null request fields")
        void getUploadUrl_NullFields() {
            // Arrange
            UploadUrlRequest nullRequest = new UploadUrlRequest();
            nullRequest.setKey(null);
            nullRequest.setContentType(null);

            when(s3Service.generatePresignedUploadUrl(null, null))
                    .thenThrow(new RuntimeException("Invalid parameters"));

            // Act & Assert
            RuntimeException exception = assertThrows(RuntimeException.class, () ->
                imageController.getUploadUrl(nullRequest)
            );
            
            assertEquals("Invalid parameters", exception.getMessage());
            verify(s3Service).generatePresignedUploadUrl(null, null);
        }

        @Test
        @DisplayName("Should handle empty request fields")
        void getUploadUrl_EmptyFields() {
            // Arrange
            UploadUrlRequest emptyRequest = new UploadUrlRequest();
            emptyRequest.setKey("");
            emptyRequest.setContentType("");

            String expectedUrl = "https://test-bucket.s3.amazonaws.com/?signed";
            when(s3Service.generatePresignedUploadUrl("", ""))
                    .thenReturn(expectedUrl);

            // Act
            ResponseEntity<UploadUrlResponse> response = imageController.getUploadUrl(emptyRequest);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("", response.getBody().getKey());
            assertEquals(expectedUrl, response.getBody().getUploadUrl());

            verify(s3Service).generatePresignedUploadUrl("", "");
        }
    }
}