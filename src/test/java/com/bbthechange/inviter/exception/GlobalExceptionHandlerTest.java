package com.bbthechange.inviter.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughputExceededException;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GlobalExceptionHandler
 *
 * Tests the exception handling logic for various exception types,
 * verifying correct HTTP status codes and error response structures.
 */
@DisplayName("GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Nested
    @DisplayName("handleConditionalCheckFailed")
    class HandleConditionalCheckFailedTests {

        @Test
        @DisplayName("Should return 409 Conflict for conditional check failures")
        void handleConditionalCheckFailed_Returns409() {
            ConditionalCheckFailedException exception =
                    (ConditionalCheckFailedException) ConditionalCheckFailedException.builder()
                            .message("The conditional request failed")
                            .build();

            ResponseEntity<Map<String, Object>> response = handler.handleConditionalCheckFailed(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("error")).isEqualTo("CONFLICT");
            assertThat(response.getBody().get("message")).isEqualTo("Resource was modified concurrently. Please retry.");
        }

        @Test
        @DisplayName("Should not expose internal condition expression details")
        void handleConditionalCheckFailed_HidesInternalDetails() {
            ConditionalCheckFailedException exception =
                    (ConditionalCheckFailedException) ConditionalCheckFailedException.builder()
                            .message("ConditionalCheckFailedException: attribute_not_exists(version)")
                            .build();

            ResponseEntity<Map<String, Object>> response = handler.handleConditionalCheckFailed(exception);

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("message").toString())
                    .doesNotContain("attribute_not_exists")
                    .doesNotContain("version");
        }
    }

    @Nested
    @DisplayName("handleTransactionCanceled")
    class HandleTransactionCanceledTests {

        @Test
        @DisplayName("Should return 409 Conflict for canceled transactions")
        void handleTransactionCanceled_Returns409() {
            TransactionCanceledException exception =
                    (TransactionCanceledException) TransactionCanceledException.builder()
                            .message("Transaction cancelled")
                            .build();

            ResponseEntity<Map<String, Object>> response = handler.handleTransactionCanceled(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("error")).isEqualTo("TRANSACTION_CONFLICT");
            assertThat(response.getBody().get("message")).isEqualTo("Operation conflicted with another request. Please retry.");
        }
    }

    @Nested
    @DisplayName("handleThroughputExceeded")
    class HandleThroughputExceededTests {

        @Test
        @DisplayName("Should return 503 Service Unavailable for throughput exceeded")
        void handleThroughputExceeded_Returns503() {
            ProvisionedThroughputExceededException exception =
                    (ProvisionedThroughputExceededException) ProvisionedThroughputExceededException.builder()
                            .message("Throughput exceeds the current capacity")
                            .build();

            ResponseEntity<Map<String, Object>> response = handler.handleThroughputExceeded(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("error")).isEqualTo("SERVICE_UNAVAILABLE");
            assertThat(response.getBody().get("message")).isEqualTo("Database capacity temporarily exceeded. Please retry later.");
        }

        @Test
        @DisplayName("Should include Retry-After header")
        void handleThroughputExceeded_IncludesRetryAfterHeader() {
            ProvisionedThroughputExceededException exception =
                    (ProvisionedThroughputExceededException) ProvisionedThroughputExceededException.builder()
                            .message("Throughput exceeds the current capacity")
                            .build();

            ResponseEntity<Map<String, Object>> response = handler.handleThroughputExceeded(exception);

            assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("10");
        }
    }

    @Nested
    @DisplayName("handleRepositoryException")
    class HandleRepositoryExceptionTests {

        @Test
        @DisplayName("Should return 500 Internal Server Error for repository exceptions")
        void handleRepositoryException_Returns500() {
            RepositoryException exception = new RepositoryException("Failed to save group",
                    new RuntimeException("DynamoDB connection timeout"));

            ResponseEntity<Map<String, Object>> response = handler.handleRepositoryException(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("error")).isEqualTo("INTERNAL_ERROR");
            assertThat(response.getBody().get("message")).isEqualTo("An internal error occurred.");
        }

        @Test
        @DisplayName("Should not expose cause details to client")
        void handleRepositoryException_HidesCauseDetails() {
            RepositoryException exception = new RepositoryException("Failed to save group",
                    new RuntimeException("Connection to dynamodb-local:8000 refused"));

            ResponseEntity<Map<String, Object>> response = handler.handleRepositoryException(exception);

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("message").toString())
                    .doesNotContain("dynamodb-local")
                    .doesNotContain("Connection");
        }
    }

    @Nested
    @DisplayName("handleTransactionFailed")
    class HandleTransactionFailedTests {

        @Test
        @DisplayName("Should return 409 Conflict for transaction failures")
        void handleTransactionFailed_Returns409() {
            TransactionFailedException exception = new TransactionFailedException("Transaction failed",
                    new RuntimeException("Version conflict"));

            ResponseEntity<Map<String, Object>> response = handler.handleTransactionFailed(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("error")).isEqualTo("TRANSACTION_FAILED");
            assertThat(response.getBody().get("message")).isEqualTo("Operation could not be completed. Please retry.");
        }
    }

    @Nested
    @DisplayName("handleSdkClientException")
    class HandleSdkClientExceptionTests {

        @Test
        @DisplayName("Should return 503 Service Unavailable for SDK client errors")
        void handleSdkClientException_Returns503() {
            SdkClientException exception = SdkClientException.builder()
                    .message("Unable to execute HTTP request: Connection refused")
                    .build();

            ResponseEntity<Map<String, Object>> response = handler.handleSdkClientException(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("error")).isEqualTo("SERVICE_UNAVAILABLE");
            assertThat(response.getBody().get("message")).isEqualTo("Service temporarily unavailable.");
        }

        @Test
        @DisplayName("Should not expose internal network details to client")
        void handleSdkClientException_HidesInternalDetails() {
            SdkClientException exception = SdkClientException.builder()
                    .message("Unable to execute HTTP request: dynamodb.us-west-2.amazonaws.com")
                    .build();

            ResponseEntity<Map<String, Object>> response = handler.handleSdkClientException(exception);

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("message").toString())
                    .doesNotContain("dynamodb")
                    .doesNotContain("amazonaws");
        }
    }

    @Nested
    @DisplayName("handleDynamoDbException (fallback)")
    class HandleDynamoDbExceptionTests {

        @Test
        @DisplayName("Should return 500 Internal Server Error for unmapped DynamoDB exceptions")
        void handleDynamoDbException_Returns500() {
            DynamoDbException exception = (DynamoDbException) DynamoDbException.builder()
                    .message("Connection failed")
                    .build();

            ResponseEntity<Map<String, Object>> response = handler.handleDynamoDbException(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("error")).isEqualTo("Database error occurred");
            assertThat(response.getBody().get("message")).isEqualTo("Please try again later");
        }

        @Test
        @DisplayName("Should not expose internal DynamoDB error details to client")
        void handleDynamoDbException_HidesInternalDetails() {
            DynamoDbException exception = (DynamoDbException) DynamoDbException.builder()
                    .message("ValidationException: The provided key element does not match the schema")
                    .build();

            ResponseEntity<Map<String, Object>> response = handler.handleDynamoDbException(exception);

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("message").toString())
                    .doesNotContain("ValidationException")
                    .doesNotContain("schema");
        }

        @Test
        @DisplayName("Should return 429 Too Many Requests for throttling exceptions")
        void handleDynamoDbException_Throttling_Returns429() {
            // Use a mock to simulate a DynamoDbException where isThrottlingException() is true
            // This covers throttling variants that don't have their own dedicated handler
            DynamoDbException exception = mock(DynamoDbException.class);
            when(exception.isThrottlingException()).thenReturn(true);
            when(exception.requestId()).thenReturn("test-request-id");

            ResponseEntity<Map<String, Object>> response = handler.handleDynamoDbException(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("error")).isEqualTo("THROTTLED");
            assertThat(response.getBody().get("message")).isEqualTo("Service is temporarily overloaded. Please retry later.");
            assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("5");
        }
    }

    @Nested
    @DisplayName("handleDynamoDbResourceNotFoundException")
    class HandleDynamoDbResourceNotFoundExceptionTests {

        @Test
        @DisplayName("Should return 404 Not Found for DynamoDB ResourceNotFoundException")
        void handleDynamoDbResourceNotFoundException_Returns404() {
            // Arrange
            software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException exception =
                    (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException)
                            software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException.builder()
                                    .message("Requested resource not found")
                                    .build();

            // Act
            ResponseEntity<Map<String, Object>> response = handler.handleDynamoDbResourceNotFoundException(exception);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("error")).isEqualTo("Resource not found");
            assertThat(response.getBody().get("message")).isEqualTo("The requested resource does not exist");
        }
    }

    @Nested
    @DisplayName("handleResourceNotFoundException")
    class HandleResourceNotFoundExceptionTests {

        @Test
        @DisplayName("Should return 404 Not Found with custom message")
        void handleResourceNotFoundException_Returns404WithMessage() {
            // Arrange
            ResourceNotFoundException exception = new ResourceNotFoundException("User with ID 123 not found");

            // Act
            ResponseEntity<Map<String, Object>> response = handler.handleResourceNotFoundException(exception);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("error")).isEqualTo("Resource not found");
            assertThat(response.getBody().get("message")).isEqualTo("User with ID 123 not found");
        }

        @Test
        @DisplayName("Should handle null message gracefully")
        void handleResourceNotFoundException_NullMessage() {
            // Arrange
            ResourceNotFoundException exception = new ResourceNotFoundException(null);

            // Act
            ResponseEntity<Map<String, Object>> response = handler.handleResourceNotFoundException(exception);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("error")).isEqualTo("Resource not found");
        }
    }

    @Nested
    @DisplayName("handleEventNotFoundException")
    class HandleEventNotFoundExceptionTests {

        @Test
        @DisplayName("Should return 404 with EVENT_NOT_FOUND error code")
        void handleEventNotFoundException_Returns404WithErrorCode() {
            // Arrange
            EventNotFoundException exception = new EventNotFoundException("Event with ID abc123 not found");

            // Act
            ResponseEntity<Map<String, Object>> response = handler.handleEventNotFoundException(exception);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("error")).isEqualTo("EVENT_NOT_FOUND");
            assertThat(response.getBody().get("message")).isEqualTo("Event with ID abc123 not found");
        }

        @Test
        @DisplayName("Should include timestamp in response")
        void handleEventNotFoundException_IncludesTimestamp() {
            // Arrange
            long beforeCall = System.currentTimeMillis();
            EventNotFoundException exception = new EventNotFoundException("Event not found");

            // Act
            ResponseEntity<Map<String, Object>> response = handler.handleEventNotFoundException(exception);

            // Assert
            long afterCall = System.currentTimeMillis();
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("timestamp")).isNotNull();
            long timestamp = (Long) response.getBody().get("timestamp");
            assertThat(timestamp).isBetween(beforeCall, afterCall);
        }
    }

    @Nested
    @DisplayName("handleGenericException")
    class HandleGenericExceptionTests {

        @Test
        @DisplayName("Should return 500 for unexpected exceptions")
        void handleGenericException_Returns500() {
            // Arrange
            Exception exception = new RuntimeException("Something unexpected happened");

            // Act
            ResponseEntity<Map<String, Object>> response = handler.handleGenericException(exception);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("error")).isEqualTo("Internal server error");
            assertThat(response.getBody().get("message")).isEqualTo("An unexpected error occurred");
        }

        @Test
        @DisplayName("Should not expose internal exception details to client")
        void handleGenericException_HidesInternalDetails() {
            // Arrange
            Exception exception = new NullPointerException("Sensitive internal details: user.password was null");

            // Act
            ResponseEntity<Map<String, Object>> response = handler.handleGenericException(exception);

            // Assert
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("message").toString())
                    .doesNotContain("Sensitive")
                    .doesNotContain("password")
                    .doesNotContain("NullPointerException");
        }

        @Test
        @DisplayName("Should handle null exception message")
        void handleGenericException_NullMessage() {
            // Arrange
            Exception exception = new RuntimeException((String) null);

            // Act
            ResponseEntity<Map<String, Object>> response = handler.handleGenericException(exception);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("error")).isEqualTo("Internal server error");
        }

        @Test
        @DisplayName("Should handle exception with cause")
        void handleGenericException_WithCause() {
            // Arrange
            Exception cause = new IllegalStateException("Root cause");
            Exception exception = new RuntimeException("Wrapper exception", cause);

            // Act
            ResponseEntity<Map<String, Object>> response = handler.handleGenericException(exception);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody().get("message").toString())
                    .doesNotContain("Root cause")
                    .doesNotContain("IllegalStateException");
        }
    }

    @Nested
    @DisplayName("handleTvMazeException")
    class TvMazeExceptionTests {

        @Test
        @DisplayName("Should return 404 Not Found for SEASON_NOT_FOUND error type")
        void handleTvMazeException_SeasonNotFound_Returns404() {
            // Given
            TvMazeException exception = TvMazeException.seasonNotFound(999);

            // When
            ResponseEntity<Map<String, Object>> response = handler.handleTvMazeException(exception);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("error")).isEqualTo("TVMAZE_SEASON_NOT_FOUND");
            assertThat(response.getBody().get("seasonId")).isEqualTo(999);
        }

        @Test
        @DisplayName("Should return 503 Service Unavailable for SERVICE_UNAVAILABLE error type")
        void handleTvMazeException_ServiceUnavailable_Returns503() {
            // Given
            TvMazeException exception = TvMazeException.serviceUnavailable(83, new RuntimeException("test"));

            // When
            ResponseEntity<Map<String, Object>> response = handler.handleTvMazeException(exception);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("error")).isEqualTo("TVMAZE_SERVICE_UNAVAILABLE");
        }

        @Test
        @DisplayName("Should return 400 Bad Request for NO_EPISODES error type")
        void handleTvMazeException_NoEpisodes_Returns400() {
            // Given
            TvMazeException exception = TvMazeException.noEpisodes(83);

            // When
            ResponseEntity<Map<String, Object>> response = handler.handleTvMazeException(exception);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("error")).isEqualTo("TVMAZE_NO_EPISODES");
        }
    }
}
