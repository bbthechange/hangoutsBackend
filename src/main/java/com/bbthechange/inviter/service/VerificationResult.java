package com.bbthechange.inviter.service;

public class VerificationResult {
    public enum Status {
        SUCCESS,
        CODE_EXPIRED,
        INVALID_CODE
    }
    
    private final Status status;
    private final String message;
    
    private VerificationResult(Status status, String message) {
        this.status = status;
        this.message = message;
    }
    
    public static VerificationResult success() {
        return new VerificationResult(Status.SUCCESS, "Verification successful");
    }
    
    public static VerificationResult codeExpired() {
        return new VerificationResult(Status.CODE_EXPIRED, "The verification code has expired. Please request a new one.");
    }
    
    public static VerificationResult invalidCode() {
        return new VerificationResult(Status.INVALID_CODE, "The verification code is incorrect.");
    }
    
    public Status getStatus() {
        return status;
    }
    
    public String getMessage() {
        return message;
    }
    
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
}