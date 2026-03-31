package com.bbthechange.inviter.model;

import com.bbthechange.inviter.util.InviterKeyFactory;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;
import com.bbthechange.inviter.util.InstantAsLongAttributeConverter;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * IdeaListMember entity for the InviterTable.
 * Represents an individual idea within an idea list.
 * 
 * Key Pattern: PK = GROUP#{GroupID}, SK = IDEALIST#{ListID}#IDEA#{IdeaID}
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@DynamoDbBean
public class IdeaListMember extends BaseItem {
    
    private String ideaId;
    private String listId;
    private String groupId;
    private String name;
    private String url;
    private String note;
    private String addedBy;
    private Instant addedTime;
    private String imageUrl;        // URL to image (may be external or S3 path)
    private String externalId;      // ID from external source (e.g., Ticketmaster, Yelp)
    private String externalSource;  // Source system for externalId (e.g., "TICKETMASTER", "YELP")
    private Set<String> interestedUserIds; // User IDs who expressed interest ("I'd do this")
    private String lastMilestoneSent;    // Highest milestone notification sent: FIRST_INTEREST, BROAD_INTEREST, GROUP_CONSENSUS

    // Place Identity
    private String googlePlaceId;     // Google Places API place_id
    private String applePlaceId;      // Apple Maps identifier

    // Location
    private String address;           // Human-readable address
    private Double latitude;
    private Double longitude;

    // Enriched Data (populated by async enrichment service, not by clients)
    private String cachedPhotoUrl;    // S3 key e.g. "places/photos/{ideaId}.jpg"
    private Double cachedRating;      // e.g. 4.3
    private Integer cachedPriceLevel; // 1-4
    private String phoneNumber;
    private String websiteUrl;
    private String menuUrl;
    private String cachedHoursJson;   // JSON string of weekly hours
    private String placeCategory;     // "restaurant", "bar", "home", "event_space", "park", "trail", "other"

    // Enrichment Metadata
    private Instant lastEnrichedAt;   // When last enriched via Google
    private String enrichmentStatus;  // "PENDING", "ENRICHED", "FAILED", "PERMANENTLY_FAILED", or null for non-place ideas

    /**
     * Override Lombok getter to add DynamoDB converter annotation.
     * lastEnrichedAt is written as epoch millis (Number) by PlaceEnrichmentServiceImpl
     * via raw UpdateItem, so it must use InstantAsLongAttributeConverter to read correctly.
     */
    @DynamoDbConvertedBy(InstantAsLongAttributeConverter.class)
    public Instant getLastEnrichedAt() {
        return lastEnrichedAt;
    }

    public void setInterestedUserIds(Set<String> interestedUserIds) {
        if (interestedUserIds == null || interestedUserIds.isEmpty()) {
            this.interestedUserIds = null;  // DynamoDB can't store empty sets
        } else {
            this.interestedUserIds = interestedUserIds;
        }
        // No touch() — interest changes use atomic UpdateExpression, not PutItem
    }

    /**
     * Create a new idea list member with generated UUID.
     */
    public IdeaListMember(String groupId, String listId, String name, String url, String note, String addedBy) {
        super();
        setItemType("IDEA");
        this.ideaId = UUID.randomUUID().toString();
        this.listId = listId;
        this.groupId = groupId;
        this.name = name;
        this.url = url;
        this.note = note;
        this.addedBy = addedBy;
        this.addedTime = Instant.now();
        
        // Set keys using InviterKeyFactory
        setPk(InviterKeyFactory.getGroupPk(this.groupId));
        setSk(InviterKeyFactory.getIdeaListMemberSk(this.listId, this.ideaId));
    }
}