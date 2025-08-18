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
}