package com.bbthechange.inviter.model;

import lombok.Getter;

@Getter
public enum PredefinedImage {
    BIRTHDAY_PARTY("birthday-party", "/images/predefined/birthday-party.jpg", "Birthday Party"),
    WEDDING("wedding", "/images/predefined/wedding.jpg", "Wedding"),
    CONFERENCE("conference", "/images/predefined/conference.jpg", "Conference"),
    GRADUATION("graduation", "/images/predefined/graduation.jpg", "Graduation"),
    BABY_SHOWER("baby-shower", "/images/predefined/baby-shower.jpg", "Baby Shower"),
    ANNIVERSARY("anniversary", "/images/predefined/anniversary.jpg", "Anniversary"),
    RETIREMENT("retirement", "/images/predefined/retirement.jpg", "Retirement"),
    HOLIDAY_PARTY("holiday-party", "/images/predefined/holiday-party.jpg", "Holiday Party"),
    BUSINESS_MEETING("business-meeting", "/images/predefined/business-meeting.jpg", "Business Meeting"),
    WORKSHOP("workshop", "/images/predefined/workshop.jpg", "Workshop");
    
    private final String key;
    private final String path;
    private final String displayName;
    
    PredefinedImage(String key, String path, String displayName) {
        this.key = key;
        this.path = path;
        this.displayName = displayName;
    }
    
    public static PredefinedImage fromKey(String key) {
        for (PredefinedImage image : values()) {
            if (image.key.equals(key)) {
                return image;
            }
        }
        return null;
    }
}