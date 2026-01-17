package com.bbthechange.inviter.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    @ExceptionHandler(DynamoDbException.class)
    public ResponseEntity<Map<String, Object>> handleDynamoDbException(DynamoDbException e) {
        logger.error("DynamoDB error: {}", e.getMessage(), e);
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Database error occurred");
        errorResponse.put("message", "Please try again later");
        
        // Return 500 Internal Server Error for database issues
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