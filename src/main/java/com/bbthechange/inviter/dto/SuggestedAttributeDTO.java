package com.bbthechange.inviter.dto;

/**
 * DTO representing the computed state of a suggestion poll for a hangout attribute.
 * Computed at read time from active polls with a non-null attributeType.
 */
public class SuggestedAttributeDTO {

    private String attributeType;      // "LOCATION", "DESCRIPTION"
    private String suggestedValue;     // Display text of the leading suggestion
    private String structuredValue;    // JSON for location data (nullable)
    private String suggestedBy;        // userId who added the leading option
    private String pollId;             // Link to the suggestion poll
    private String status;             // "PENDING" (<24h), "READY_TO_PROMOTE" (>24h unopposed), "CONTESTED"
    private int voteCount;             // Votes on the leading option
    private long createdAtMillis;      // When the poll was created

    public SuggestedAttributeDTO() {}

    public SuggestedAttributeDTO(String attributeType, String suggestedValue, String structuredValue,
                                  String suggestedBy, String pollId, String status,
                                  int voteCount, long createdAtMillis) {
        this.attributeType = attributeType;
        this.suggestedValue = suggestedValue;
        this.structuredValue = structuredValue;
        this.suggestedBy = suggestedBy;
        this.pollId = pollId;
        this.status = status;
        this.voteCount = voteCount;
        this.createdAtMillis = createdAtMillis;
    }

    public String getAttributeType() { return attributeType; }
    public void setAttributeType(String attributeType) { this.attributeType = attributeType; }

    public String getSuggestedValue() { return suggestedValue; }
    public void setSuggestedValue(String suggestedValue) { this.suggestedValue = suggestedValue; }

    public String getStructuredValue() { return structuredValue; }
    public void setStructuredValue(String structuredValue) { this.structuredValue = structuredValue; }

    public String getSuggestedBy() { return suggestedBy; }
    public void setSuggestedBy(String suggestedBy) { this.suggestedBy = suggestedBy; }

    public String getPollId() { return pollId; }
    public void setPollId(String pollId) { this.pollId = pollId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getVoteCount() { return voteCount; }
    public void setVoteCount(int voteCount) { this.voteCount = voteCount; }

    public long getCreatedAtMillis() { return createdAtMillis; }
    public void setCreatedAtMillis(long createdAtMillis) { this.createdAtMillis = createdAtMillis; }
}
