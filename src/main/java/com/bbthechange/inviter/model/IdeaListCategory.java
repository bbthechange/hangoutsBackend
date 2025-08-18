package com.bbthechange.inviter.model;

/**
 * Categories for organizing idea lists.
 * Each group can create lists with different categories for better organization.
 */
public enum IdeaListCategory {
    RESTAURANT("Restaurant"),
    ACTIVITY("Activity"), 
    TRAIL("Trail"),
    MOVIE("Movie"),
    BOOK("Book"),
    TRAVEL("Travel"),
    OTHER("Other");
    
    private final String displayName;
    
    IdeaListCategory(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}