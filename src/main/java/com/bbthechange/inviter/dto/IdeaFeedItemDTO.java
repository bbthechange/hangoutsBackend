package com.bbthechange.inviter.dto;

/**
 * Feed item representing a high-interest idea surfaced from an idea list.
 * Appears in the group feed as a card with a "Let's do it!" action that
 * creates a hangout pre-filled from the idea.
 *
 * <p>Only ideas with interestCount >= 3 are surfaced, and surfacing is
 * suppressed when the group already has confirmed hangouts covering each
 * of the next 3 weeks.
 */
public class IdeaFeedItemDTO implements FeedItem {

    private final String type = "idea_suggestion";

    private String ideaId;
    private String listId;
    private String groupId;
    private String ideaName;
    private String listName;
    private String imageUrl;
    private String note;
    private int interestCount;

    // Place fields (for pre-filling hangout creation)
    private String googlePlaceId;
    private String address;
    private Double latitude;
    private Double longitude;
    private String placeCategory;

    /**
     * Surfacing reason set by the forward-fill pipeline. One of:
     * {@code SUPPORTED_IDEA, UNSUPPORTED_IDEA}. Clients may use this to differentiate
     * strongly-wanted ideas from last-resort fallback suggestions.
     */
    private String surfaceReason;

    public IdeaFeedItemDTO() {}

    public IdeaFeedItemDTO(String ideaId, String listId, String groupId,
                           String ideaName, String listName, String imageUrl,
                           String note, int interestCount,
                           String googlePlaceId, String address,
                           Double latitude, Double longitude, String placeCategory) {
        this.ideaId = ideaId;
        this.listId = listId;
        this.groupId = groupId;
        this.ideaName = ideaName;
        this.listName = listName;
        this.imageUrl = imageUrl;
        this.note = note;
        this.interestCount = interestCount;
        this.googlePlaceId = googlePlaceId;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.placeCategory = placeCategory;
    }

    public String getType() {
        return type;
    }

    public String getIdeaId() {
        return ideaId;
    }

    public void setIdeaId(String ideaId) {
        this.ideaId = ideaId;
    }

    public String getListId() {
        return listId;
    }

    public void setListId(String listId) {
        this.listId = listId;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getIdeaName() {
        return ideaName;
    }

    public void setIdeaName(String ideaName) {
        this.ideaName = ideaName;
    }

    public String getListName() {
        return listName;
    }

    public void setListName(String listName) {
        this.listName = listName;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public int getInterestCount() {
        return interestCount;
    }

    public void setInterestCount(int interestCount) {
        this.interestCount = interestCount;
    }

    public String getGooglePlaceId() {
        return googlePlaceId;
    }

    public void setGooglePlaceId(String googlePlaceId) {
        this.googlePlaceId = googlePlaceId;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getPlaceCategory() {
        return placeCategory;
    }

    public void setPlaceCategory(String placeCategory) {
        this.placeCategory = placeCategory;
    }

    public String getSurfaceReason() {
        return surfaceReason;
    }

    public void setSurfaceReason(String surfaceReason) {
        this.surfaceReason = surfaceReason;
    }
}
