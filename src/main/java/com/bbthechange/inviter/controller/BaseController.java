package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Base controller with common functionality and comprehensive error handling.
 * All controllers should extend this for consistent error handling and user extraction.
 */
@RestController
public abstract class BaseController {
    
    private static final Logger logger = LoggerFactory.getLogger(BaseController.class);
    
    /**
     * Extract authenticated user ID from JWT token (set by authentication filter).
     */
    protected String extractUserId(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null || userId.trim().isEmpty()) {
            throw new UnauthorizedException("No authenticated user");
        }
        return userId;
    }
    
    /**
     * Error response DTO for consistent error formatting.
     */
    public static class ErrorResponse {
        private final String error;
        private final String message;
        private final long timestamp;
        
        public ErrorResponse(String error, String message) {
            this.error = error;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getError() { return error; }
        public String getMessage() { return message; }
        public long getTimestamp() { return timestamp; }
    }
    
    // Common exception handlers
    
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException e) {
        logger.warn("Unauthorized access attempt: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse("UNAUTHORIZED", e.getMessage()));
    }
    
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException e) {
        logger.warn("Validation error: {}", e.getMessage());
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("VALIDATION_ERROR", e.getMessage()));
    }
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException e) {
        logger.debug("Resource not found: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("NOT_FOUND", e.getMessage()));
    }
    
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException e) {
        logger.debug("User not found: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("USER_NOT_FOUND", e.getMessage()));
    }
    
    @ExceptionHandler(TransactionFailedException.class)
    public ResponseEntity<ErrorResponse> handleTransactionFailed(TransactionFailedException e) {
        logger.error("Transaction failed: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("TRANSACTION_FAILED", "Operation could not be completed"));
    }
    
    @ExceptionHandler(RepositoryException.class)
    public ResponseEntity<ErrorResponse> handleRepository(RepositoryException e) {
        logger.error("Repository error: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("REPOSITORY_ERROR", "Internal server error"));
    }
    
    @ExceptionHandler(DynamoDbException.class)
    public ResponseEntity<ErrorResponse> handleDynamoDbError(DynamoDbException e) {
        logger.error("DynamoDB error: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("DATABASE_ERROR", "Internal server error"));
    }
    
    @ExceptionHandler(InvalidKeyException.class)
    public ResponseEntity<ErrorResponse> handleInvalidKey(InvalidKeyException e) {
        logger.warn("Invalid key format: {}", e.getMessage());
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("INVALID_KEY", e.getMessage()));
    }
    
    @ExceptionHandler(javax.validation.ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(javax.validation.ConstraintViolationException e) {
        logger.warn("Validation constraint violation: {}", e.getMessage());
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("VALIDATION_ERROR", "Invalid input parameters"));
    }
    
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(org.springframework.web.bind.MethodArgumentNotValidException e) {
        logger.warn("Method argument validation error: {}", e.getMessage());
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .findFirst()
            .orElse("Invalid input");
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("VALIDATION_ERROR", message));
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception e) {
        logger.error("Unexpected error: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}