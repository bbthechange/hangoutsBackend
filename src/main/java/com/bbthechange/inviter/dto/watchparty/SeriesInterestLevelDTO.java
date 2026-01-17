package com.bbthechange.inviter.dto.watchparty;

import com.bbthechange.inviter.model.InterestLevel;

/**
 * DTO for series-level interest in watch parties.
 * Uses "level" field (not "status") to match the request format.
 */
public class SeriesInterestLevelDTO {

    private String userId;
    private String level;  // GOING, INTERESTED, NOT_GOING
    private String userName;
    private String mainImagePath;

    public SeriesInterestLevelDTO() {
    }

    public SeriesInterestLevelDTO(String userId, String level, String userName, String mainImagePath) {
        this.userId = userId;
        this.level = level;
        this.userName = userName;
        this.mainImagePath = mainImagePath;
    }

    /**
     * Create from InterestLevel model.
     * Maps "status" field to "level" for series interest.
     */
    public static SeriesInterestLevelDTO fromInterestLevel(InterestLevel interestLevel) {
        return new SeriesInterestLevelDTO(
            interestLevel.getUserId(),
            interestLevel.getStatus(),  // "status" in model maps to "level" in DTO
            interestLevel.getUserName(),
            interestLevel.getMainImagePath()
        );
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getMainImagePath() {
        return mainImagePath;
    }

    public void setMainImagePath(String mainImagePath) {
        this.mainImagePath = mainImagePath;
    }
}
