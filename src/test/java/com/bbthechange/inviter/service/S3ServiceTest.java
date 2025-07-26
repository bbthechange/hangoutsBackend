package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.PredefinedImageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URL;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for S3Service
 * 
 * Test Coverage:
 * - getPredefinedImages - Retrieve predefined images from S3 bucket
 * - generatePresignedUploadUrl - Generate presigned URLs for S3 uploads
 * - Error handling scenarios
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("S3Service Tests")
class S3ServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    @InjectMocks
    private S3Service s3Service;

    private String testBucketName = "test-bucket";

    @BeforeEach
    void setUp() throws Exception {
        // Use reflection to set the bucket name since it's a @Value field
        java.lang.reflect.Field bucketField = S3Service.class.getDeclaredField("bucketName");
        bucketField.setAccessible(true);
        bucketField.set(s3Service, testBucketName);
    }

    @Nested
    @DisplayName("getPredefinedImages - Predefined Images Retrieval Tests")
    class GetPredefinedImagesTests {

        @Test
        @DisplayName("Should return predefined images successfully")
        void getPredefinedImages_Success() {
            // Arrange
            S3Object s3Object1 = S3Object.builder()
                    .key("predefined/birthday.jpg")
                    .lastModified(Instant.now())
                    .build();
            
            S3Object s3Object2 = S3Object.builder()
                    .key("predefined/wedding.jpg")
                    .lastModified(Instant.now())
                    .build();

            ListObjectsV2Response response = ListObjectsV2Response.builder()
                    .contents(Arrays.asList(s3Object1, s3Object2))
                    .build();

            when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(response);

            // Act
            List<PredefinedImageResponse> result = s3Service.getPredefinedImages();

            // Assert
            assertNotNull(result);
            assertEquals(2, result.size());
            
            PredefinedImageResponse image1 = result.get(0);
            assertEquals("birthday", image1.getKey());
            assertEquals("predefined/birthday.jpg", image1.getPath());
            assertEquals("Birthday Party", image1.getDisplayName());
            
            PredefinedImageResponse image2 = result.get(1);
            assertEquals("wedding", image2.getKey());
            assertEquals("predefined/wedding.jpg", image2.getPath());
            assertEquals("Wedding", image2.getDisplayName());

            verify(s3Client).listObjectsV2(argThat((ListObjectsV2Request request) ->
                testBucketName.equals(request.bucket()) &&
                "predefined/".equals(request.prefix())
            ));
        }

        @Test
        @DisplayName("Should filter out prefix folder from results")
        void getPredefinedImages_FiltersPrefixFolder() {
            // Arrange
            S3Object prefixFolder = S3Object.builder()
                    .key("predefined/")
                    .lastModified(Instant.now())
                    .build();
            
            S3Object actualFile = S3Object.builder()
                    .key("predefined/party-balloons.jpg")
                    .lastModified(Instant.now())
                    .build();

            ListObjectsV2Response response = ListObjectsV2Response.builder()
                    .contents(Arrays.asList(prefixFolder, actualFile))
                    .build();

            when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(response);

            // Act
            List<PredefinedImageResponse> result = s3Service.getPredefinedImages();

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("party-balloons", result.get(0).getKey());
            assertEquals("Party", result.get(0).getDisplayName());
        }

        @Test
        @DisplayName("Should return empty list when no images found")
        void getPredefinedImages_EmptyResult() {
            // Arrange
            ListObjectsV2Response response = ListObjectsV2Response.builder()
                    .contents(Arrays.asList())
                    .build();

            when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(response);

            // Act
            List<PredefinedImageResponse> result = s3Service.getPredefinedImages();

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should throw RuntimeException when S3 call fails")
        void getPredefinedImages_S3Exception() {
            // Arrange
            when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                    .thenThrow(new RuntimeException("S3 connection failed"));

            // Act & Assert
            RuntimeException exception = assertThrows(RuntimeException.class, () ->
                s3Service.getPredefinedImages()
            );
            
            assertEquals("Failed to fetch predefined images", exception.getMessage());
            assertNotNull(exception.getCause());
            assertEquals("S3 connection failed", exception.getCause().getMessage());
        }
    }

    @Nested
    @DisplayName("generatePresignedUploadUrl - Presigned URL Generation Tests")
    class GeneratePresignedUploadUrlTests {

        @Test
        @DisplayName("Should generate presigned upload URL successfully")
        void generatePresignedUploadUrl_Success() throws Exception {
            // Arrange
            String testKey = "events/user123/12345_abc_image.jpg";
            String testContentType = "image/jpeg";
            String expectedUrl = "https://test-bucket.s3.amazonaws.com/events/user123/12345_abc_image.jpg?X-Amz-Credential=test";

            PresignedPutObjectRequest presignedRequest = mock(PresignedPutObjectRequest.class);
            when(presignedRequest.url()).thenReturn(new URL(expectedUrl));
            when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedRequest);

            // Act
            String result = s3Service.generatePresignedUploadUrl(testKey, testContentType);

            // Assert
            assertNotNull(result);
            assertEquals(expectedUrl, result);

            verify(s3Presigner).presignPutObject(argThat((PutObjectPresignRequest request) -> {
                var putObjectRequest = request.putObjectRequest();
                return testBucketName.equals(putObjectRequest.bucket()) &&
                       testKey.equals(putObjectRequest.key()) &&
                       testContentType.equals(putObjectRequest.contentType()); // contentType is now included in the request
            }));
        }

        @Test
        @DisplayName("Should generate URL with content type included in signature")
        void generatePresignedUploadUrl_WithContentTypeInSignature() throws Exception {
            // Arrange
            String testKey = "events/user123/image.png";
            String testContentType = "image/png";
            String expectedUrl = "https://test-bucket.s3.amazonaws.com/events/user123/image.png?signed";

            PresignedPutObjectRequest presignedRequest = mock(PresignedPutObjectRequest.class);
            when(presignedRequest.url()).thenReturn(new URL(expectedUrl));
            when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedRequest);

            // Act
            String result = s3Service.generatePresignedUploadUrl(testKey, testContentType);

            // Assert
            assertEquals(expectedUrl, result);
            verify(s3Presigner).presignPutObject(argThat((PutObjectPresignRequest request) ->
                testContentType.equals(request.putObjectRequest().contentType()) // contentType is now included in the request
            ));
        }

        @Test
        @DisplayName("Should throw RuntimeException when S3Presigner fails")
        void generatePresignedUploadUrl_S3PresignerException() {
            // Arrange
            String testKey = "events/user123/image.jpg";
            String testContentType = "image/jpeg";

            when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                    .thenThrow(new RuntimeException("Presigner failed"));

            // Act & Assert
            RuntimeException exception = assertThrows(RuntimeException.class, () ->
                s3Service.generatePresignedUploadUrl(testKey, testContentType)
            );
            
            assertEquals("Failed to generate presigned upload URL", exception.getMessage());
            assertNotNull(exception.getCause());
            assertEquals("Presigner failed", exception.getCause().getMessage());
        }

        @Test
        @DisplayName("Should handle null key")
        void generatePresignedUploadUrl_NullKey() {
            // Act & Assert
            assertThrows(RuntimeException.class, () ->
                s3Service.generatePresignedUploadUrl(null, "image/jpeg")
            );
        }

        @Test
        @DisplayName("Should handle null content type")
        void generatePresignedUploadUrl_NullContentType() {
            // Act & Assert
            assertThrows(RuntimeException.class, () ->
                s3Service.generatePresignedUploadUrl("events/user123/image.jpg", null)
            );
        }
    }
}