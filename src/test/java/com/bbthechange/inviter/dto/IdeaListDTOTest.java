package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.IdeaList;
import com.bbthechange.inviter.model.IdeaListCategory;
import com.bbthechange.inviter.model.IdeaListMember;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for Model and DTO data integrity and serialization.
 * Tests the critical mapping between entities and DTOs and JSON serialization.
 */
class IdeaListDTOTest {

    private ObjectMapper objectMapper;
    private String testGroupId;
    private String testUserId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        
        testGroupId = UUID.randomUUID().toString();
        testUserId = UUID.randomUUID().toString();
    }

    // ===== DATA INTEGRITY TESTS =====

    @Test
    void ideaListDTO_FromIdeaList_MapsAllFieldsCorrectly() {
        // Given: IdeaList with all fields populated including members
        IdeaList ideaList = new IdeaList(testGroupId, "My Restaurant List", IdeaListCategory.RESTAURANT, "Great places", testUserId);
        
        IdeaListMember member1 = new IdeaListMember(testGroupId, ideaList.getListId(), "Restaurant 1", "http://rest1.com", "Good food", testUserId);
        IdeaListMember member2 = new IdeaListMember(testGroupId, ideaList.getListId(), "Restaurant 2", null, "Great ambiance", testUserId);
        ideaList.getMembers().add(member1);
        ideaList.getMembers().add(member2);

        // When: Convert to DTO
        IdeaListDTO dto = new IdeaListDTO(ideaList);

        // Then: All fields copied correctly, members NOT auto-converted (requires explicit service layer conversion)
        assertThat(dto.getId()).isEqualTo(ideaList.getListId());
        assertThat(dto.getName()).isEqualTo("My Restaurant List");
        assertThat(dto.getCategory()).isEqualTo(IdeaListCategory.RESTAURANT);
        assertThat(dto.getNote()).isEqualTo("Great places");
        assertThat(dto.getCreatedBy()).isEqualTo(testUserId);
        assertThat(dto.getCreatedAt()).isEqualTo(ideaList.getCreatedAt());
        assertThat(dto.getIdeas()).isEmpty(); // Constructor doesn't auto-convert members
    }

    @Test
    void ideaDTO_FromIdeaListMember_MapsAllFieldsCorrectly() {
        // Given: IdeaListMember with all fields populated
        String listId = UUID.randomUUID().toString();
        IdeaListMember member = new IdeaListMember(testGroupId, listId, "Great Restaurant", "http://restaurant.com", "Amazing food", testUserId);

        // When: Convert to DTO
        IdeaDTO dto = new IdeaDTO(member);

        // Then: All fields copied correctly
        assertThat(dto.getId()).isEqualTo(member.getIdeaId());
        assertThat(dto.getName()).isEqualTo("Great Restaurant");
        assertThat(dto.getUrl()).isEqualTo("http://restaurant.com");
        assertThat(dto.getNote()).isEqualTo("Amazing food");
        assertThat(dto.getAddedBy()).isEqualTo(testUserId);
        assertThat(dto.getAddedTime()).isEqualTo(member.getAddedTime());
    }

    @Test
    void ideaListDTO_WithNullFields_HandlesNullsSafely() {
        // Given: IdeaList with some null fields
        IdeaList ideaList = new IdeaList(testGroupId, "Simple List", IdeaListCategory.OTHER, null, testUserId);

        // When: Convert to DTO
        IdeaListDTO dto = new IdeaListDTO(ideaList);

        // Then: Null fields handled correctly
        assertThat(dto.getName()).isEqualTo("Simple List");
        assertThat(dto.getNote()).isNull();
        assertThat(dto.getCategory()).isEqualTo(IdeaListCategory.OTHER);
        assertThat(dto.getId()).isNotNull();
        assertThat(dto.getCreatedBy()).isNotNull();
        assertThat(dto.getCreatedAt()).isNotNull();
        assertThat(dto.getIdeas()).isEmpty();
    }

    @Test
    void ideaDTO_WithNullFields_HandlesNullsSafely() {
        // Given: IdeaListMember with some null fields
        String listId = UUID.randomUUID().toString();
        IdeaListMember member = new IdeaListMember(testGroupId, listId, null, "http://example.com", null, testUserId);

        // When: Convert to DTO
        IdeaDTO dto = new IdeaDTO(member);

        // Then: Null fields handled correctly
        assertThat(dto.getName()).isNull();
        assertThat(dto.getUrl()).isEqualTo("http://example.com");
        assertThat(dto.getNote()).isNull();
        assertThat(dto.getId()).isNotNull();
        assertThat(dto.getAddedBy()).isNotNull();
        assertThat(dto.getAddedTime()).isNotNull();
    }

    // ===== SERIALIZATION TESTS =====

    @Test
    void ideaListDTO_JsonSerialization_IncludesAllFields() throws JsonProcessingException {
        // Given: IdeaListDTO with all fields populated
        IdeaListDTO dto = new IdeaListDTO();
        dto.setId(UUID.randomUUID().toString());
        dto.setName("My Restaurant List");
        dto.setCategory(IdeaListCategory.RESTAURANT);
        dto.setNote("Great places to eat");
        dto.setCreatedBy(testUserId);
        dto.setCreatedAt(Instant.parse("2023-10-01T12:00:00Z"));
        
        IdeaDTO idea = new IdeaDTO();
        idea.setId(UUID.randomUUID().toString());
        idea.setName("Restaurant 1");
        idea.setUrl("http://rest1.com");
        idea.setNote("Good food");
        idea.setAddedBy(testUserId);
        idea.setAddedTime(Instant.parse("2023-10-01T13:00:00Z"));
        dto.getIdeas().add(idea);

        // When: Serialize to JSON
        String json = objectMapper.writeValueAsString(dto);

        // Then: All fields present in JSON (timestamp format may vary based on ObjectMapper configuration)
        assertThat(json).contains("\"id\":");
        assertThat(json).contains("\"name\":\"My Restaurant List\"");
        assertThat(json).contains("\"category\":\"RESTAURANT\"");
        assertThat(json).contains("\"note\":\"Great places to eat\"");
        assertThat(json).contains("\"createdBy\":");
        assertThat(json).contains("\"createdAt\":");
        assertThat(json).contains("\"ideas\":");
        assertThat(json).contains("\"Restaurant 1\"");
    }

    @Test
    void ideaListDTO_JsonDeserialization_RestoresAllFields() throws JsonProcessingException {
        // Given: JSON representation of IdeaListDTO
        String json = "{\n" +
                "  \"id\": \"" + UUID.randomUUID().toString() + "\",\n" +
                "  \"name\": \"My Movie List\",\n" +
                "  \"category\": \"MOVIE\",\n" +
                "  \"note\": \"Films to watch\",\n" +
                "  \"createdBy\": \"" + testUserId + "\",\n" +
                "  \"createdAt\": \"2023-10-01T12:00:00Z\",\n" +
                "  \"ideas\": [\n" +
                "    {\n" +
                "      \"id\": \"" + UUID.randomUUID().toString() + "\",\n" +
                "      \"name\": \"The Matrix\",\n" +
                "      \"url\": \"http://imdb.com/matrix\",\n" +
                "      \"note\": \"Great sci-fi\",\n" +
                "      \"addedBy\": \"" + testUserId + "\",\n" +
                "      \"addedTime\": \"2023-10-01T13:00:00Z\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        // When: Deserialize from JSON
        IdeaListDTO dto = objectMapper.readValue(json, IdeaListDTO.class);

        // Then: All fields restored correctly
        assertThat(dto.getId()).isNotNull();
        assertThat(dto.getName()).isEqualTo("My Movie List");
        assertThat(dto.getCategory()).isEqualTo(IdeaListCategory.MOVIE);
        assertThat(dto.getNote()).isEqualTo("Films to watch");
        assertThat(dto.getCreatedBy()).isEqualTo(testUserId);
        assertThat(dto.getCreatedAt()).isEqualTo(Instant.parse("2023-10-01T12:00:00Z"));
        assertThat(dto.getIdeas()).hasSize(1);
        assertThat(dto.getIdeas().get(0).getName()).isEqualTo("The Matrix");
        assertThat(dto.getIdeas().get(0).getUrl()).isEqualTo("http://imdb.com/matrix");
        assertThat(dto.getIdeas().get(0).getNote()).isEqualTo("Great sci-fi");
    }

    @Test
    void ideaDTO_JsonSerialization_IncludesAllFields() throws JsonProcessingException {
        // Given: IdeaDTO with all fields
        IdeaDTO dto = new IdeaDTO();
        dto.setId(UUID.randomUUID().toString());
        dto.setName("Great Restaurant");
        dto.setUrl("http://restaurant.com");
        dto.setNote("Amazing food");
        dto.setAddedBy(testUserId);
        dto.setAddedTime(Instant.parse("2023-10-01T14:30:00Z"));

        // When: Serialize to JSON
        String json = objectMapper.writeValueAsString(dto);

        // Then: All fields present in JSON (timestamp format may vary based on ObjectMapper configuration)
        assertThat(json).contains("\"id\":");
        assertThat(json).contains("\"name\":\"Great Restaurant\"");
        assertThat(json).contains("\"url\":\"http://restaurant.com\"");
        assertThat(json).contains("\"note\":\"Amazing food\"");
        assertThat(json).contains("\"addedBy\":");
        assertThat(json).contains("\"addedTime\":");
    }

    @Test
    void createIdeaListRequest_InputSanitization_TrimsWhitespace() {
        // Given: Request with whitespace around values
        CreateIdeaListRequest request = new CreateIdeaListRequest();
        request.setName("  My Restaurant List  ");
        request.setNote("  Great places to eat  ");

        // When: Access via getters (which trim)
        String trimmedName = request.getName();
        String trimmedNote = request.getNote();

        // Then: Values are trimmed
        assertThat(trimmedName).isEqualTo("My Restaurant List");
        assertThat(trimmedNote).isEqualTo("Great places to eat");
    }

    @Test
    void createIdeaRequest_InputSanitization_TrimsWhitespace() {
        // Given: Request with whitespace around values
        CreateIdeaRequest request = new CreateIdeaRequest();
        request.setName("  Great Restaurant  ");
        request.setUrl("  http://restaurant.com  ");
        request.setNote("  Amazing food  ");

        // When: Access via getters (which trim)
        String trimmedName = request.getName();
        String trimmedUrl = request.getUrl();
        String trimmedNote = request.getNote();

        // Then: Values are trimmed
        assertThat(trimmedName).isEqualTo("Great Restaurant");
        assertThat(trimmedUrl).isEqualTo("http://restaurant.com");
        assertThat(trimmedNote).isEqualTo("Amazing food");
    }

    @Test
    void ideaListCategory_EnumSerialization_UsesEnumNames() throws JsonProcessingException {
        // Given: DTO with category enum
        IdeaListDTO dto = new IdeaListDTO();
        dto.setCategory(IdeaListCategory.RESTAURANT);

        // When: Serialize to JSON
        String json = objectMapper.writeValueAsString(dto);

        // Then: Enum serialized as string name
        assertThat(json).contains("\"category\":\"RESTAURANT\"");
        
        // When: Deserialize back
        IdeaListDTO restored = objectMapper.readValue(json, IdeaListDTO.class);
        
        // Then: Enum restored correctly
        assertThat(restored.getCategory()).isEqualTo(IdeaListCategory.RESTAURANT);
    }

    @Test
    void ideaListDTO_AddIdeaHelper_WorksCorrectly() {
        // Given: Empty IdeaListDTO
        IdeaListDTO dto = new IdeaListDTO();
        
        IdeaDTO idea1 = new IdeaDTO();
        idea1.setName("Idea 1");
        IdeaDTO idea2 = new IdeaDTO();
        idea2.setName("Idea 2");

        // When: Add ideas using helper method
        dto.addIdea(idea1);
        dto.addIdea(idea2);

        // Then: Ideas added correctly
        assertThat(dto.getIdeas()).hasSize(2);
        assertThat(dto.getIdeas().get(0).getName()).isEqualTo("Idea 1");
        assertThat(dto.getIdeas().get(1).getName()).isEqualTo("Idea 2");
    }

    @Test
    void ideaListDTO_AddIdeaHelper_InitializesListIfNull() {
        // Given: DTO with null ideas list
        IdeaListDTO dto = new IdeaListDTO();
        dto.setIdeas(null);

        IdeaDTO idea = new IdeaDTO();
        idea.setName("Test Idea");

        // When: Add idea using helper method
        dto.addIdea(idea);

        // Then: List initialized and idea added
        assertThat(dto.getIdeas()).isNotNull();
        assertThat(dto.getIdeas()).hasSize(1);
        assertThat(dto.getIdeas().get(0).getName()).isEqualTo("Test Idea");
    }

    // ===== PLACE FIELD MAPPING TESTS =====

    @Test
    void ideaDTO_Constructor_MapsAllPlaceFields() {
        // Given: IdeaListMember with all place fields populated
        String listId = UUID.randomUUID().toString();
        IdeaListMember member = new IdeaListMember(testGroupId, listId, "Joe's Pizza", "http://joespizza.com", "Best pizza", testUserId);
        member.setGooglePlaceId("ChIJN1t_tDeuEmsRUsoyG83frY4");
        member.setApplePlaceId("apple-place-123");
        member.setAddress("123 Main St, New York, NY 10001");
        member.setLatitude(40.7128);
        member.setLongitude(-74.0060);
        member.setCachedPhotoUrl("places/photos/abc123.jpg");
        member.setCachedRating(4.3);
        member.setCachedPriceLevel(2);
        member.setPhoneNumber("+12125551234");
        member.setWebsiteUrl("http://joespizza.com");
        member.setMenuUrl("http://joespizza.com/menu");
        member.setCachedHoursJson("{\"monday\":\"9:00-22:00\"}");
        member.setPlaceCategory("restaurant");
        member.setLastEnrichedAt(Instant.parse("2025-06-15T10:00:00Z"));
        member.setEnrichmentStatus("ENRICHED");

        // When: Convert to DTO
        IdeaDTO dto = new IdeaDTO(member);

        // Then: All 15 place fields mapped correctly
        assertThat(dto.getGooglePlaceId()).isEqualTo("ChIJN1t_tDeuEmsRUsoyG83frY4");
        assertThat(dto.getApplePlaceId()).isEqualTo("apple-place-123");
        assertThat(dto.getAddress()).isEqualTo("123 Main St, New York, NY 10001");
        assertThat(dto.getLatitude()).isEqualTo(40.7128);
        assertThat(dto.getLongitude()).isEqualTo(-74.0060);
        assertThat(dto.getCachedPhotoUrl()).isEqualTo("places/photos/abc123.jpg");
        assertThat(dto.getCachedRating()).isEqualTo(4.3);
        assertThat(dto.getCachedPriceLevel()).isEqualTo(2);
        assertThat(dto.getPhoneNumber()).isEqualTo("+12125551234");
        assertThat(dto.getWebsiteUrl()).isEqualTo("http://joespizza.com");
        assertThat(dto.getMenuUrl()).isEqualTo("http://joespizza.com/menu");
        assertThat(dto.getCachedHoursJson()).isEqualTo("{\"monday\":\"9:00-22:00\"}");
        assertThat(dto.getPlaceCategory()).isEqualTo("restaurant");
        assertThat(dto.getLastEnrichedAt()).isEqualTo(Instant.parse("2025-06-15T10:00:00Z"));
        assertThat(dto.getEnrichmentStatus()).isEqualTo("ENRICHED");

        // Original fields also mapped
        assertThat(dto.getId()).isEqualTo(member.getIdeaId());
        assertThat(dto.getName()).isEqualTo("Joe's Pizza");
        assertThat(dto.getUrl()).isEqualTo("http://joespizza.com");
        assertThat(dto.getNote()).isEqualTo("Best pizza");
    }

    @Test
    void ideaDTO_Constructor_NullPlaceFields_HandledGracefully() {
        // Given: IdeaListMember with no place fields (backward compatibility)
        String listId = UUID.randomUUID().toString();
        IdeaListMember member = new IdeaListMember(testGroupId, listId, "Watch a Movie", null, "Classic film", testUserId);

        // When: Convert to DTO
        IdeaDTO dto = new IdeaDTO(member);

        // Then: All place fields are null (no NPE)
        assertThat(dto.getGooglePlaceId()).isNull();
        assertThat(dto.getApplePlaceId()).isNull();
        assertThat(dto.getAddress()).isNull();
        assertThat(dto.getLatitude()).isNull();
        assertThat(dto.getLongitude()).isNull();
        assertThat(dto.getCachedPhotoUrl()).isNull();
        assertThat(dto.getCachedRating()).isNull();
        assertThat(dto.getCachedPriceLevel()).isNull();
        assertThat(dto.getPhoneNumber()).isNull();
        assertThat(dto.getWebsiteUrl()).isNull();
        assertThat(dto.getMenuUrl()).isNull();
        assertThat(dto.getCachedHoursJson()).isNull();
        assertThat(dto.getPlaceCategory()).isNull();
        assertThat(dto.getLastEnrichedAt()).isNull();
        assertThat(dto.getEnrichmentStatus()).isNull();

        // Original fields still work
        assertThat(dto.getName()).isEqualTo("Watch a Movie");
        assertThat(dto.getNote()).isEqualTo("Classic film");
    }

    @Test
    void ideaDTO_PlaceFields_JsonSerialization_IncludesPlaceData() throws JsonProcessingException {
        // Given: IdeaDTO with place fields populated
        IdeaDTO dto = new IdeaDTO();
        dto.setId(UUID.randomUUID().toString());
        dto.setName("Joe's Pizza");
        dto.setGooglePlaceId("ChIJN1t_tDeuEmsRUsoyG83frY4");
        dto.setAddress("123 Main St");
        dto.setLatitude(40.7128);
        dto.setLongitude(-74.0060);
        dto.setCachedRating(4.3);
        dto.setCachedPriceLevel(2);
        dto.setPlaceCategory("restaurant");
        dto.setEnrichmentStatus("ENRICHED");

        // When: Serialize to JSON
        String json = objectMapper.writeValueAsString(dto);

        // Then: Place fields present in JSON
        assertThat(json).contains("\"googlePlaceId\":\"ChIJN1t_tDeuEmsRUsoyG83frY4\"");
        assertThat(json).contains("\"address\":\"123 Main St\"");
        assertThat(json).contains("\"latitude\":40.7128");
        assertThat(json).contains("\"longitude\":-74.006");
        assertThat(json).contains("\"cachedRating\":4.3");
        assertThat(json).contains("\"cachedPriceLevel\":2");
        assertThat(json).contains("\"placeCategory\":\"restaurant\"");
        assertThat(json).contains("\"enrichmentStatus\":\"ENRICHED\"");
    }

    @Test
    void createIdeaRequest_PlaceFields_InputSanitization_TrimsWhitespace() {
        // Given: CreateIdeaRequest with whitespace around place fields
        CreateIdeaRequest request = new CreateIdeaRequest();
        request.setGooglePlaceId("  ChIJN1t_tDeuEmsRUsoyG83frY4  ");
        request.setApplePlaceId("  apple-123  ");
        request.setAddress("  123 Main St  ");
        request.setPlaceCategory("  restaurant  ");

        // When/Then: Getters trim whitespace
        assertThat(request.getGooglePlaceId()).isEqualTo("ChIJN1t_tDeuEmsRUsoyG83frY4");
        assertThat(request.getApplePlaceId()).isEqualTo("apple-123");
        assertThat(request.getAddress()).isEqualTo("123 Main St");
        assertThat(request.getPlaceCategory()).isEqualTo("restaurant");
    }

    @Test
    void createIdeaRequest_PlaceFields_NullValues_ReturnNull() {
        // Given: CreateIdeaRequest with null place fields
        CreateIdeaRequest request = new CreateIdeaRequest();

        // When/Then: Null fields return null (no NPE)
        assertThat(request.getGooglePlaceId()).isNull();
        assertThat(request.getApplePlaceId()).isNull();
        assertThat(request.getAddress()).isNull();
        assertThat(request.getLatitude()).isNull();
        assertThat(request.getLongitude()).isNull();
        assertThat(request.getPlaceCategory()).isNull();
    }

    @Test
    void updateIdeaRequest_PlaceFields_InputSanitization_TrimsWhitespace() {
        // Given: UpdateIdeaRequest with whitespace around place fields
        UpdateIdeaRequest request = new UpdateIdeaRequest();
        request.setGooglePlaceId("  ChIJN1t_tDeuEmsRUsoyG83frY4  ");
        request.setApplePlaceId("  apple-123  ");
        request.setAddress("  123 Main St  ");
        request.setPlaceCategory("  restaurant  ");

        // When/Then: Getters trim whitespace
        assertThat(request.getGooglePlaceId()).isEqualTo("ChIJN1t_tDeuEmsRUsoyG83frY4");
        assertThat(request.getApplePlaceId()).isEqualTo("apple-123");
        assertThat(request.getAddress()).isEqualTo("123 Main St");
        assertThat(request.getPlaceCategory()).isEqualTo("restaurant");
    }
}