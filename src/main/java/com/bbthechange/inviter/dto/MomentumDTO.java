package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.MomentumCategory;

/**
 * DTO representing the momentum state of a hangout for API responses.
 * Score is normalized to 0-100 before being sent to clients.
 */
public class MomentumDTO {
    private int score;          // Normalized 0-100
    private String category;    // "BUILDING", "GAINING_MOMENTUM", "CONFIRMED"
    private Long confirmedAt;   // Epoch millis, null if not confirmed
    private String confirmedBy; // User ID, null if not confirmed
    private String suggestedBy; // User ID of original suggester (for "Suggested by" display)

    public MomentumDTO() {}

    /**
     * Build a MomentumDTO with a normalized score.
     *
     * @param rawScore  The raw internal score
     * @param threshold The current dynamic threshold for this group
     * @param category  The current momentum category
     * @param confirmedAt  Epoch millis when confirmed (null if not confirmed)
     * @param confirmedBy  User ID who confirmed (null if not confirmed)
     * @param suggestedBy  User ID of the original suggester
     * @return A MomentumDTO with score normalized to 0-100
     */
    public static MomentumDTO fromRawScore(int rawScore, int threshold, MomentumCategory category,
                                           Long confirmedAt, String confirmedBy, String suggestedBy) {
        MomentumDTO dto = new MomentumDTO();
        // Normalize: score as percentage toward confirmation (threshold * 2 = 100%)
        int normalizedScore = threshold > 0
                ? Math.min(100, (rawScore * 100) / (threshold * 2))
                : 0;
        dto.score = normalizedScore;
        dto.category = category != null ? category.name() : null;
        dto.confirmedAt = confirmedAt;
        dto.confirmedBy = confirmedBy;
        dto.suggestedBy = suggestedBy;
        return dto;
    }

    /**
     * Build a MomentumDTO directly from stored fields (no threshold needed — score already normalized or category is enough).
     * Used when reading from pointer data where threshold context is unavailable.
     */
    public static MomentumDTO fromPointerFields(Integer momentumScore, MomentumCategory category,
                                                Long confirmedAt, String confirmedBy, String suggestedBy) {
        MomentumDTO dto = new MomentumDTO();
        dto.score = momentumScore != null ? momentumScore : 0;
        dto.category = category != null ? category.name() : null;
        dto.confirmedAt = confirmedAt;
        dto.confirmedBy = confirmedBy;
        dto.suggestedBy = suggestedBy;
        return dto;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Long getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Long confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public String getConfirmedBy() {
        return confirmedBy;
    }

    public void setConfirmedBy(String confirmedBy) {
        this.confirmedBy = confirmedBy;
    }

    public String getSuggestedBy() {
        return suggestedBy;
    }

    public void setSuggestedBy(String suggestedBy) {
        this.suggestedBy = suggestedBy;
    }
}
