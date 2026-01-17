package com.bbthechange.inviter.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
    @DisplayName("handleDynamoDbException")
    class HandleDynamoDbExceptionTests {

        @Test
        @DisplayName("Should return 500 Internal Server Error for DynamoDB exceptions")
        void handleDynamoDbException_Returns500() {
            // Arrange
            DynamoDbException exception = (DynamoDbException) DynamoDbException.builder()
                    .message("Connection failed")
                    .build();

            // Act
            ResponseEntity<Map<String, Object>> response = handler.handleDynamoDbException(exception);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("error")).isEqualTo("Database error occurred");
            assertThat(response.getBody().get("message")).isEqualTo("Please try again later");
        }

        @Test
        @DisplayName("Should not expose internal DynamoDB error details to client")
        void handleDynamoDbException_HidesInternalDetails() {
            // Arrange
            DynamoDbException exception = (DynamoDbException) DynamoDbException.builder()
                    .message("ValidationException: The provided key element does not match the schema")
                    .build();

            // Act
            ResponseEntity<Map<String, Object>> response = handler.handleDynamoDbException(exception);

            // Assert
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("message").toString())
                    .doesNotContain("ValidationException")
                    .doesNotContain("schema");
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
