package com.bbthechange.inviter.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughputExceededException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    @ExceptionHandler(ConditionalCheckFailedException.class)
    public ResponseEntity<Map<String, Object>> handleConditionalCheckFailed(
            ConditionalCheckFailedException e) {
        logger.warn("Conditional check failed (requestId={}): {}", e.requestId(), e.getMessage());

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "CONFLICT");
        errorResponse.put("message", "Resource was modified concurrently. Please retry.");

        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(TransactionCanceledException.class)
    public ResponseEntity<Map<String, Object>> handleTransactionCanceled(
            TransactionCanceledException e) {
        logger.warn("Transaction canceled (requestId={}): reasons={}",
                e.requestId(), e.cancellationReasons());

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "TRANSACTION_CONFLICT");
        errorResponse.put("message", "Operation conflicted with another request. Please retry.");

        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(ProvisionedThroughputExceededException.class)
    public ResponseEntity<Map<String, Object>> handleThroughputExceeded(
            ProvisionedThroughputExceededException e) {
        logger.error("DynamoDB throughput exceeded after SDK retries (requestId={})", e.requestId());

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "SERVICE_UNAVAILABLE");
        errorResponse.put("message", "Database capacity temporarily exceeded. Please retry later.");

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "10")
                .body(errorResponse);
    }

    @ExceptionHandler(RepositoryException.class)
    public ResponseEntity<Map<String, Object>> handleRepositoryException(RepositoryException e) {
        logger.error("Repository operation failed: {}", e.getMessage(), e);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "INTERNAL_ERROR");
        errorResponse.put("message", "An internal error occurred.");

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(TransactionFailedException.class)
    public ResponseEntity<Map<String, Object>> handleTransactionFailed(TransactionFailedException e) {
        logger.error("Transaction failed: {}", e.getMessage(), e);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "TRANSACTION_FAILED");
        errorResponse.put("message", "Operation could not be completed. Please retry.");

        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(SdkClientException.class)
    public ResponseEntity<Map<String, Object>> handleSdkClientException(SdkClientException e) {
        logger.error("AWS SDK client error (network/config): {}", e.getMessage(), e);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "SERVICE_UNAVAILABLE");
        errorResponse.put("message", "Service temporarily unavailable.");

        return new ResponseEntity<>(errorResponse, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(DynamoDbException.class)
    public ResponseEntity<Map<String, Object>> handleDynamoDbException(DynamoDbException e) {
        if (e.isThrottlingException()) {
            logger.error("DynamoDB throttling after SDK retries (requestId={})", e.requestId());

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "THROTTLED");
            errorResponse.put("message", "Service is temporarily overloaded. Please retry later.");

            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", "5")
                    .body(errorResponse);
        }

        logger.error("Unhandled DynamoDB error (requestId={}, errorCode={}, statusCode={}): {}",
                e.requestId(),
                e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : "unknown",
                e.statusCode(),
                e.getMessage(), e);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Database error occurred");
        errorResponse.put("message", "Please try again later");

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    @ExceptionHandler(software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleDynamoDbResourceNotFoundException(
            software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException e) {
        logger.error("DynamoDB resource not found: {}", e.getMessage(), e);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Resource not found");
        errorResponse.put("message", "The requested resource does not exist");

        // Return 404 Not Found for missing resources
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(com.bbthechange.inviter.exception.ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFoundException(
            com.bbthechange.inviter.exception.ResourceNotFoundException e) {
        logger.warn("Resource not found: {}", e.getMessage());

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Resource not found");
        errorResponse.put("message", e.getMessage());

        // Return 404 Not Found for missing resources
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleEventNotFoundException(EventNotFoundException e) {
        logger.warn("Event not found: {}", e.getMessage());

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "EVENT_NOT_FOUND");
        errorResponse.put("message", e.getMessage());
        errorResponse.put("timestamp", System.currentTimeMillis());

        // Return 404 Not Found for missing events
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(TvMazeException.class)
    public ResponseEntity<Map<String, Object>> handleTvMazeException(TvMazeException e) {
        logger.warn("TVMaze error ({}): {}", e.getErrorType(), e.getMessage());

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "TVMAZE_" + e.getErrorType().name());
        errorResponse.put("message", e.getMessage());
        errorResponse.put("timestamp", System.currentTimeMillis());
        if (e.getSeasonId() != null) {
            errorResponse.put("seasonId", e.getSeasonId());
        }

        return new ResponseEntity<>(errorResponse, e.getHttpStatus());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception e) {
        logger.error("Unexpected error: {}", e.getMessage(), e);
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Internal server error");
        errorResponse.put("message", "An unexpected error occurred");
        
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}