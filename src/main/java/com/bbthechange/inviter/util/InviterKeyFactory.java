package com.bbthechange.inviter.util;

import com.bbthechange.inviter.exception.InvalidKeyException;
import java.util.regex.Pattern;

/**
 * Type-safe key factory for DynamoDB single-table design.
 * Provides validated key generation for the InviterTable with consistent patterns.
 */
public final class InviterKeyFactory {
    private static final String DELIMITER = "#";
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", 
        Pattern.CASE_INSENSITIVE
    );
    
    // Constants for magic strings
    public static final String GROUP_PREFIX = "GROUP";
    public static final String USER_PREFIX = "USER";
    public static final String EVENT_PREFIX = "EVENT";
    public static final String SERIES_PREFIX = "SERIES";
    public static final String METADATA_SUFFIX = "METADATA";
    public static final String POLL_PREFIX = "POLL";
    public static final String CAR_PREFIX = "CAR";
    public static final String CAR_RIDER_PREFIX = "CAR_RIDER";
    public static final String ATTENDANCE_PREFIX = "ATTENDANCE";
    public static final String HANGOUT_PREFIX = "HANGOUT";
    public static final String INVITE_PREFIX = "INVITE";
    public static final String NEEDS_RIDE_PREFIX = "NEEDS_RIDE";
    public static final String OPTION_PREFIX = "OPTION";
    public static final String VOTE_PREFIX = "VOTE";
    public static final String RIDER_PREFIX = "RIDER";
    public static final String ATTRIBUTE_PREFIX = "ATTRIBUTE";
    public static final String IDEALIST_PREFIX = "IDEALIST";
    public static final String IDEA_PREFIX = "IDEA";
    public static final String PLACE_PREFIX = "PLACE";
    public static final String PRIMARY_PLACE = "PRIMARY_PLACE";
    public static final String INVITE_CODE_PREFIX = "INVITE_CODE";
    public static final String CODE_PREFIX = "CODE";
    public static final String CREATED_PREFIX = "CREATED";

    // Status constants
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    // Owner type constants
    public static final String OWNER_TYPE_USER = "USER";
    public static final String OWNER_TYPE_GROUP = "GROUP";
    
    // Private constructor to prevent instantiation
    private InviterKeyFactory() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    // Validation methods
    private static void validateId(String id, String type) {
        if (id == null || id.trim().isEmpty()) {
            throw new InvalidKeyException(type + " ID cannot be null or empty");
        }
        if (!UUID_PATTERN.matcher(id).matches()) {
            throw new InvalidKeyException("Invalid " + type + " ID format: " + id);
        }
    }
    
    // Group Keys with validation
    public static String getGroupPk(String groupId) {
        validateId(groupId, "Group");
        return GROUP_PREFIX + DELIMITER + groupId;
    }
    
    public static String getUserSk(String userId) {
        validateId(userId, "User");
        return USER_PREFIX + DELIMITER + userId;
    }
    
    public static String getMetadataSk() {
        return METADATA_SUFFIX;
    }
    
    // Event/Hangout Keys
    public static String getEventPk(String eventId) {
        validateId(eventId, "Event");
        return EVENT_PREFIX + DELIMITER + eventId;
    }
    
    public static String getHangoutSk(String hangoutId) {
        validateId(hangoutId, "Hangout");
        return HANGOUT_PREFIX + DELIMITER + hangoutId;
    }
    
    public static String getInviteSk(String userId) {
        validateId(userId, "User");
        return INVITE_PREFIX + DELIMITER + userId;
    }
    
    public static String getAttendanceSk(String userId) {
        validateId(userId, "User");
        return ATTENDANCE_PREFIX + DELIMITER + userId;
    }
    
    // Series Keys
    public static String getSeriesPk(String seriesId) {
        validateId(seriesId, "Series");
        return SERIES_PREFIX + DELIMITER + seriesId;
    }
    
    public static String getSeriesSk(String seriesId) {
        validateId(seriesId, "Series");
        return SERIES_PREFIX + DELIMITER + seriesId;
    }
    
    // Attribute Keys (UUID-based for safety)
    public static String getAttributeSk(String attributeId) {
        validateId(attributeId, "Attribute");
        return ATTRIBUTE_PREFIX + DELIMITER + attributeId;
    }
    
    // Poll Keys (designed for efficient querying)
    public static String getPollSk(String pollId) {
        validateId(pollId, "Poll");
        return POLL_PREFIX + DELIMITER + pollId;
    }
    
    public static String getPollOptionSk(String pollId, String optionId) {
        validateId(pollId, "Poll");
        validateId(optionId, "Option");
        return String.join(DELIMITER, POLL_PREFIX, pollId, OPTION_PREFIX, optionId);
    }
    
    public static String getVoteSk(String pollId, String userId, String optionId) {
        validateId(pollId, "Poll");
        validateId(userId, "User");
        validateId(optionId, "Option");
        return String.join(DELIMITER, POLL_PREFIX, pollId, VOTE_PREFIX, userId, OPTION_PREFIX, optionId);
    }
    
    // Carpool Keys
    public static String getCarSk(String driverId) {
        validateId(driverId, "Driver");
        return CAR_PREFIX + DELIMITER + driverId;
    }
    
    public static String getCarRiderSk(String driverId, String riderId) {
        validateId(driverId, "Driver");
        validateId(riderId, "Rider");
        return String.join(DELIMITER, CAR_PREFIX, driverId, RIDER_PREFIX, riderId);
    }
    
    public static String getNeedsRideSk(String userId) {
        validateId(userId, "User");
        return NEEDS_RIDE_PREFIX + DELIMITER + userId;
    }
    
    // Query Prefixes for efficient filtering
    public static String getPollPrefix(String pollId) {
        validateId(pollId, "Poll");
        return POLL_PREFIX + DELIMITER + pollId;
    }
    
    public static String getCarPrefix(String driverId) {
        validateId(driverId, "Driver");
        return CAR_PREFIX + DELIMITER + driverId;
    }
    
    // GSI Keys
    public static String getUserGsi1Pk(String userId) {
        validateId(userId, "User");
        return USER_PREFIX + DELIMITER + userId;
    }
    
    public static String getGroupGsi1Sk(String groupId) {
        validateId(groupId, "Group");
        return GROUP_PREFIX + DELIMITER + groupId;
    }
    
    // Helper methods for type-safe filtering
    public static boolean isPollItem(String sortKey) {
        return sortKey.startsWith(POLL_PREFIX + DELIMITER) && 
               !sortKey.contains(DELIMITER + OPTION_PREFIX + DELIMITER) && 
               !sortKey.contains(DELIMITER + VOTE_PREFIX + DELIMITER);
    }
    
    public static boolean isPollOption(String sortKey) {
        return sortKey.contains(DELIMITER + OPTION_PREFIX + DELIMITER) &&
               !sortKey.contains(DELIMITER + VOTE_PREFIX + DELIMITER);
    }
    
    public static boolean isCarItem(String sortKey) {
        return sortKey.startsWith(CAR_PREFIX + DELIMITER) && 
               !sortKey.contains(DELIMITER + RIDER_PREFIX + DELIMITER);
    }
    
    public static boolean isCarRider(String sortKey) {
        return sortKey.contains(DELIMITER + RIDER_PREFIX + DELIMITER);
    }
    
    public static boolean isCarRiderItem(String sortKey) {
        return sortKey.contains(DELIMITER + RIDER_PREFIX + DELIMITER);
    }
    
    public static boolean isVoteItem(String sortKey) {
        return sortKey.contains(DELIMITER + VOTE_PREFIX + DELIMITER);
    }
    
    public static boolean isAttendanceItem(String sortKey) {
        return sortKey.startsWith(ATTENDANCE_PREFIX + DELIMITER);
    }
    
    public static boolean isInviteItem(String sortKey) {
        return sortKey.startsWith(INVITE_PREFIX + DELIMITER);
    }
    
    public static boolean isNeedsRideItem(String sortKey) {
        return sortKey.startsWith(NEEDS_RIDE_PREFIX + DELIMITER);
    }
    
    public static boolean isMetadata(String sortKey) {
        return METADATA_SUFFIX.equals(sortKey);
    }
    
    public static boolean isHangoutPointer(String sortKey) {
        return sortKey.startsWith(HANGOUT_PREFIX + DELIMITER);
    }
    
    public static boolean isSeriesPointer(String sortKey) {
        return sortKey != null && sortKey.startsWith(SERIES_PREFIX + DELIMITER);
    }
    
    public static boolean isAttributeItem(String sortKey) {
        return sortKey != null && sortKey.startsWith(ATTRIBUTE_PREFIX + DELIMITER);
    }
    
    public static boolean isGroupMembership(String sortKey) {
        return sortKey.startsWith(USER_PREFIX + DELIMITER);
    }
    
    public static boolean isSeriesItem(String sortKey) {
        return sortKey != null && METADATA_SUFFIX.equals(sortKey);
    }
    
    // Idea List Keys
    public static String getIdeaListSk(String listId) {
        validateId(listId, "IdeaList");
        return IDEALIST_PREFIX + DELIMITER + listId;
    }
    
    public static String getIdeaListMemberSk(String listId, String ideaId) {
        validateId(listId, "IdeaList");
        validateId(ideaId, "Idea");
        return String.join(DELIMITER, IDEALIST_PREFIX, listId, IDEA_PREFIX, ideaId);
    }
    
    // Query prefix for getting all items in an idea list
    public static String getIdeaListPrefix(String listId) {
        validateId(listId, "IdeaList");
        return IDEALIST_PREFIX + DELIMITER + listId;
    }
    
    // Query prefix for getting all idea lists in a group
    public static String getIdeaListQueryPrefix() {
        return IDEALIST_PREFIX + DELIMITER;
    }
    
    // Helper methods for type-safe filtering
    public static boolean isIdeaList(String sortKey) {
        return sortKey != null && sortKey.startsWith(IDEALIST_PREFIX + DELIMITER) &&
               !sortKey.contains(DELIMITER + IDEA_PREFIX + DELIMITER);
    }

    public static boolean isIdeaListMember(String sortKey) {
        return sortKey != null && sortKey.contains(DELIMITER + IDEA_PREFIX + DELIMITER);
    }

    // Place Keys
    public static String getPlaceSk(String placeId) {
        validateId(placeId, "Place");
        return PLACE_PREFIX + DELIMITER + placeId;
    }

    public static boolean isPlace(String sortKey) {
        return sortKey != null && sortKey.startsWith(PLACE_PREFIX + DELIMITER);
    }

    // Invite Code Keys
    public static String getInviteCodePk(String inviteCodeId) {
        validateId(inviteCodeId, "InviteCode");
        return INVITE_CODE_PREFIX + DELIMITER + inviteCodeId;
    }

    public static String getCodeLookupGsi3pk(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new InvalidKeyException("Code cannot be null or empty");
        }
        return CODE_PREFIX + DELIMITER + code;
    }

    public static String getCreatedSk(java.time.Instant createdAt) {
        if (createdAt == null) {
            throw new InvalidKeyException("CreatedAt cannot be null");
        }
        return CREATED_PREFIX + DELIMITER + createdAt.toString();
    }

    public static boolean isInviteCode(String sortKey) {
        return sortKey != null && METADATA_SUFFIX.equals(sortKey);
    }
}