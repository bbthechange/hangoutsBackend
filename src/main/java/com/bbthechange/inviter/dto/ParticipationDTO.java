package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.Participation;
import com.bbthechange.inviter.model.ParticipationType;

import java.time.Instant;

/**
 * Data Transfer Object for Participation information.
 * Used in API responses for participation details.
 */
public class ParticipationDTO {

    private String participationId;
    private String userId;
    private String displayName;         // Denormalized from User
    private String mainImagePath;       // Denormalized from User
    private ParticipationType type;
    private String section;
    private String seat;
    private String reservationOfferId;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Constructor from Participation entity with denormalized user info.
     */
    public ParticipationDTO(Participation participation, String displayName, String mainImagePath) {
        this.participationId = participation.getParticipationId();
        this.userId = participation.getUserId();
        this.displayName = displayName;
        this.mainImagePath = mainImagePath;
        this.type = participation.getType();
        this.section = participation.getSection();
        this.seat = participation.getSeat();
        this.reservationOfferId = participation.getReservationOfferId();
        this.createdAt = participation.getCreatedAt();
        this.updatedAt = participation.getUpdatedAt();
    }

    // Getters and Setters

    public String getParticipationId() {
        return participationId;
    }

    public void setParticipationId(String participationId) {
        this.participationId = participationId;
    }

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

    public ParticipationType getType() {
        return type;
    }

    public void setType(ParticipationType type) {
        this.type = type;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getSeat() {
        return seat;
    }

    public void setSeat(String seat) {
        this.seat = seat;
    }

    public String getReservationOfferId() {
        return reservationOfferId;
    }

    public void setReservationOfferId(String reservationOfferId) {
        this.reservationOfferId = reservationOfferId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
