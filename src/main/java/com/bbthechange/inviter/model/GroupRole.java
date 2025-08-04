package com.bbthechange.inviter.model;

/**
 * Constants for group membership roles.
 * Ensures consistent role strings across all classes.
 */
public final class GroupRole {
    
    public static final String ADMIN = "ADMIN";
    public static final String MEMBER = "MEMBER";
    
    // Private constructor to prevent instantiation
    private GroupRole() {
        throw new UnsupportedOperationException("Utility class");
    }
}