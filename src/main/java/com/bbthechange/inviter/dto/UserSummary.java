package com.bbthechange.inviter.dto;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

/**
 * Lightweight user representation for lists in HangoutPointer.
 * Used in ParticipationSummaryDTO for efficient group feed rendering.
 */
@DynamoDbBean
public class UserSummary {

    private String userId;
    private String displayName;               // Denormalized from User
    private String mainImagePath;             // Denormalized from User

    // Default constructor for DynamoDB
    public UserSummary() {
    }

    /**
     * Constructor for creating UserSummary with denormalized user info.
     */
    public UserSummary(String userId, String displayName, String mainImagePath) {
        this.userId = userId;
        this.displayName = displayName;
        this.mainImagePath = mainImagePath;
    }

    // Getters and setters

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getMainImagePath() {
        return mainImagePath;
    }

    public void setMainImagePath(String mainImagePath) {
        this.mainImagePath = mainImagePath;
    }
}
