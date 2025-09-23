package com.bbthechange.inviter.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.Instant;
import java.util.Objects;

@DynamoDbBean
public class VerificationCode {

    private String phoneNumber;
    private String hashedCode;
    private Integer failedAttempts;
    private Long expiresAt;

    public VerificationCode() {
        this.failedAttempts = 0;
    }

    public VerificationCode(String phoneNumber, String hashedCode, Long expiresAt) {
        this.phoneNumber = phoneNumber;
        this.hashedCode = hashedCode;
        this.failedAttempts = 0;
        this.expiresAt = expiresAt;
    }

    @DynamoDbPartitionKey
    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getHashedCode() {
        return hashedCode;
    }

    public void setHashedCode(String hashedCode) {
        this.hashedCode = hashedCode;
    }

    public Integer getFailedAttempts() {
        return failedAttempts;
    }

    public void setFailedAttempts(Integer failedAttempts) {
        this.failedAttempts = failedAttempts;
    }

    public Long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Long expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() {
        return Instant.now().getEpochSecond() > this.expiresAt;
    }

    public void incrementFailedAttempts() {
        this.failedAttempts = (this.failedAttempts == null ? 0 : this.failedAttempts) + 1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VerificationCode that = (VerificationCode) o;
        return Objects.equals(phoneNumber, that.phoneNumber) &&
                Objects.equals(hashedCode, that.hashedCode) &&
                Objects.equals(failedAttempts, that.failedAttempts) &&
                Objects.equals(expiresAt, that.expiresAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(phoneNumber, hashedCode, failedAttempts, expiresAt);
    }

    @Override
    public String toString() {
        return "VerificationCode{" +
                "phoneNumber='" + phoneNumber + '\'' +
                ", hashedCode='[REDACTED]'" +
                ", failedAttempts=" + failedAttempts +
                ", expiresAt=" + expiresAt +
                '}';
    }
}