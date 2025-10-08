package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.exception.*;
import com.bbthechange.inviter.model.IdeaListCategory;
import com.bbthechange.inviter.service.IdeaListService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for IdeaListController focusing on HTTP contracts, input validation, and error handling.
 * Tests the API layer integration with proper status codes and response formats.
 */
@ExtendWith(MockitoExtension.class)
class IdeaListControllerTest {

    @Mock
    private IdeaListService ideaListService;

    @InjectMocks
    private IdeaListController ideaListController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    
    private String testGroupId;
    private String testListId;
    private String testIdeaId;
    private String testUserId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(ideaListController)
                .setControllerAdvice(ideaListController) // Include BaseController exception handling
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        
        testGroupId = UUID.randomUUID().toString();
        testListId = UUID.randomUUID().toString();
        testIdeaId = UUID.randomUUID().toString();
        testUserId = UUID.randomUUID().toString();
    }

    // ===== HTTP CONTRACT & INPUT VALIDATION TESTS =====

    @Test
    void getIdeaListsForGroup_ValidRequest_Returns200WithCorrectFormat() throws Exception {
        // Given: Valid request and service returns data
        IdeaListDTO list1 = createSampleIdeaListDTO("Restaurants", IdeaListCategory.RESTAURANT);
        IdeaListDTO list2 = createSampleIdeaListDTO("Movies", IdeaListCategory.MOVIE);
        List<IdeaListDTO> expectedLists = Arrays.asList(list1, list2);
        
        when(ideaListService.getIdeaListsForGroup(testGroupId, testUserId)).thenReturn(expectedLists);

        // When/Then: HTTP 200 status and correct JSON structure
        mockMvc.perform(get("/groups/{groupId}/idea-lists", testGroupId)
                .contentType(MediaType.APPLICATION_JSON)
                .requestAttr("userId", testUserId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Restaurants"))
                .andExpect(jsonPath("$[0].category").value("RESTAURANT"))
                .andExpect(jsonPath("$[0].ideas").isArray())
                .andExpect(jsonPath("$[1].name").value("Movies"))
                .andExpect(jsonPath("$[1].category").value("MOVIE"));

        verify(ideaListService).getIdeaListsForGroup(testGroupId, testUserId);
    }

    @Test
    void createIdeaList_InvalidGroupIdFormat_PassedToService() throws Exception {
        // Note: Path variable validation requires full Spring context, not standalone MockMvc
        // This test verifies the controller passes the parameter to service correctly
        String malformedGroupId = "not-a-uuid";
        CreateIdeaListRequest request = new CreateIdeaListRequest();
        request.setName("Test List");
        request.setCategory(IdeaListCategory.RESTAURANT);

        IdeaListDTO createdList = createSampleIdeaListDTO("Test List", IdeaListCategory.RESTAURANT);
        when(ideaListService.createIdeaList(eq(malformedGroupId), any(CreateIdeaListRequest.class), eq(testUserId)))
                .thenReturn(createdList);

        // When/Then: Controller passes malformed ID to service (service layer handles validation)
        mockMvc.perform(post("/groups/{groupId}/idea-lists", malformedGroupId)
                .contentType(MediaType.APPLICATION_JSON)
                .requestAttr("userId", testUserId)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        verify(ideaListService).createIdeaList(eq(malformedGroupId), any(CreateIdeaListRequest.class), eq(testUserId));
    }


    @Test
    void createIdeaList_InvalidRequestBody_Returns400() throws Exception {
        // Given: Request with invalid data (empty name)
        CreateIdeaListRequest request = new CreateIdeaListRequest();
        request.setName(""); // Invalid: empty name
        request.setCategory(IdeaListCategory.RESTAURANT);

        // When/Then: HTTP 400 for validation error
        mockMvc.perform(post("/groups/{groupId}/idea-lists", testGroupId)
                .contentType(MediaType.APPLICATION_JSON)
                .requestAttr("userId", testUserId)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(ideaListService);
    }

    @Test
    void getIdeaList_InvalidListIdFormat_PassedToService() throws Exception {
        // Note: Path variable validation requires full Spring context, not standalone MockMvc
        // This test verifies the controller passes the parameter to service correctly
        String malformedListId = "invalid-uuid-format";

        IdeaListDTO ideaList = createSampleIdeaListDTO("Test List", IdeaListCategory.RESTAURANT);
        when(ideaListService.getIdeaList(testGroupId, malformedListId, testUserId)).thenReturn(ideaList);

        // When/Then: Controller passes malformed ID to service (service layer handles validation)
        mockMvc.perform(get("/groups/{groupId}/idea-lists/{listId}", testGroupId, malformedListId)
                .contentType(MediaType.APPLICATION_JSON)
                .requestAttr("userId", testUserId))
                .andExpect(status().isOk());

        verify(ideaListService).getIdeaList(testGroupId, malformedListId, testUserId);
    }

    // ===== AUTHORIZATION INTEGRATION TESTS =====

    @Test
    void deleteIdeaList_UserNotGroupMember_Returns403() throws Exception {
        // Given: Valid JWT but user not in group (service throws UnauthorizedException)
        doThrow(new UnauthorizedException("User is not a member of group")).when(ideaListService)
                .deleteIdeaList(testGroupId, testListId, testUserId);

        // When/Then: HTTP 403 Forbidden with group membership message
        mockMvc.perform(delete("/groups/{groupId}/idea-lists/{listId}", testGroupId, testListId)
                .contentType(MediaType.APPLICATION_JSON)
                .requestAttr("userId", testUserId))
                .andExpect(status().isForbidden());

        verify(ideaListService).deleteIdeaList(testGroupId, testListId, testUserId);
    }

    @Test
    void addIdeaToList_UserNotGroupMember_Returns403() throws Exception {
        // Given: Valid request but unauthorized user
        CreateIdeaRequest request = new CreateIdeaRequest();
        request.setName("Test Idea");
        
        doThrow(new UnauthorizedException("User is not a member of group")).when(ideaListService)
                .addIdeaToList(eq(testGroupId), eq(testListId), any(CreateIdeaRequest.class), eq(testUserId));

        // When/Then: HTTP 403 Forbidden
        mockMvc.perform(post("/groups/{groupId}/idea-lists/{listId}/ideas", testGroupId, testListId)
                .contentType(MediaType.APPLICATION_JSON)
                .requestAttr("userId", testUserId)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        verify(ideaListService).addIdeaToList(eq(testGroupId), eq(testListId), any(CreateIdeaRequest.class), eq(testUserId));
    }

    @Test
    void getIdeaListsForGroup_UserNotGroupMember_Returns403() throws Exception {
        // Given: Service throws UnauthorizedException for unauthorized user
        doThrow(new UnauthorizedException("User is not a member of group")).when(ideaListService)
                .getIdeaListsForGroup(testGroupId, testUserId);

        // When/Then: HTTP 403 Forbidden
        mockMvc.perform(get("/groups/{groupId}/idea-lists", testGroupId)
                .contentType(MediaType.APPLICATION_JSON)
                .requestAttr("userId", testUserId))
                .andExpect(status().isForbidden());

        verify(ideaListService).getIdeaListsForGroup(testGroupId, testUserId);
    }

    // ===== SUCCESS PATH INTEGRATION TESTS =====

    @Test
    void createIdeaList_ValidRequest_Returns201WithCreatedResource() throws Exception {
        // Given: Valid authenticated request
        CreateIdeaListRequest request = new CreateIdeaListRequest();
        request.setName("My Restaurant List");
        request.setCategory(IdeaListCategory.RESTAURANT);
        request.setNote("Places to try");

        IdeaListDTO createdList = createSampleIdeaListDTO("My Restaurant List", IdeaListCategory.RESTAURANT);
        when(ideaListService.createIdeaList(eq(testGroupId), any(CreateIdeaListRequest.class), eq(testUserId)))
                .thenReturn(createdList);

        // When/Then: HTTP 201 Created status and response body contains created list with generated ID
        mockMvc.perform(post("/groups/{groupId}/idea-lists", testGroupId)
                .contentType(MediaType.APPLICATION_JSON)
                .requestAttr("userId", testUserId)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(createdList.getId()))
                .andExpect(jsonPath("$.name").value("My Restaurant List"))
                .andExpect(jsonPath("$.category").value("RESTAURANT"))
                .andExpect(jsonPath("$.note").value("Sample note"))
                .andExpect(jsonPath("$.createdBy").value(testUserId))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.ideas").isArray())
                .andExpect(jsonPath("$.ideas").isEmpty());

        verify(ideaListService).createIdeaList(eq(testGroupId), any(CreateIdeaListRequest.class), eq(testUserId));
    }

    @Test
    void updateIdea_PartialUpdate_Returns200WithUpdatedData() throws Exception {
        // Given: PATCH request updating only note field
        UpdateIdeaRequest request = new UpdateIdeaRequest();
        request.setNote("Updated note text");

        IdeaDTO updatedIdea = createSampleIdeaDTO("Original Name", "http://original.com", "Updated note text");
        when(ideaListService.updateIdea(eq(testGroupId), eq(testListId), eq(testIdeaId), any(UpdateIdeaRequest.class), eq(testUserId)))
                .thenReturn(updatedIdea);

        // When/Then: HTTP 200 with updated note, other fields unchanged
        mockMvc.perform(patch("/groups/{groupId}/idea-lists/{listId}/ideas/{ideaId}", 
                testGroupId, testListId, testIdeaId)
                .contentType(MediaType.APPLICATION_JSON)
                .requestAttr("userId", testUserId)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(testIdeaId))
                .andExpect(jsonPath("$.name").value("Original Name"))
                .andExpect(jsonPath("$.url").value("http://original.com"))
                .andExpect(jsonPath("$.note").value("Updated note text"));

        verify(ideaListService).updateIdea(eq(testGroupId), eq(testListId), eq(testIdeaId), any(UpdateIdeaRequest.class), eq(testUserId));
    }

    @Test
    void addIdeaToList_ValidRequest_Returns201WithCreatedIdea() throws Exception {
        // Given: Valid idea creation request
        CreateIdeaRequest request = new CreateIdeaRequest();
        request.setName("Great Restaurant");
        request.setUrl("http://restaurant.com");
        request.setNote("Amazing food");

        IdeaDTO createdIdea = createSampleIdeaDTO("Great Restaurant", "http://restaurant.com", "Amazing food");
        when(ideaListService.addIdeaToList(eq(testGroupId), eq(testListId), any(CreateIdeaRequest.class), eq(testUserId)))
                .thenReturn(createdIdea);

        // When/Then: HTTP 201 Created with new idea data
        mockMvc.perform(post("/groups/{groupId}/idea-lists/{listId}/ideas", testGroupId, testListId)
                .contentType(MediaType.APPLICATION_JSON)
                .requestAttr("userId", testUserId)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(testIdeaId))
                .andExpect(jsonPath("$.name").value("Great Restaurant"))
                .andExpect(jsonPath("$.url").value("http://restaurant.com"))
                .andExpect(jsonPath("$.note").value("Amazing food"))
                .andExpect(jsonPath("$.addedBy").value(testUserId))
                .andExpect(jsonPath("$.addedTime").exists());

        verify(ideaListService).addIdeaToList(eq(testGroupId), eq(testListId), any(CreateIdeaRequest.class), eq(testUserId));
    }

    // ===== ERROR PROPAGATION TESTS =====

    @Test
    void getIdeaList_NonexistentList_Returns404() throws Exception {
        // Given: Service throws ResourceNotFoundException for non-existent list
        doThrow(new ResourceNotFoundException("Idea list not found")).when(ideaListService)
                .getIdeaList(testGroupId, testListId, testUserId);

        // When/Then: HTTP 404 Not Found
        mockMvc.perform(get("/groups/{groupId}/idea-lists/{listId}", testGroupId, testListId)
                .contentType(MediaType.APPLICATION_JSON)
                .requestAttr("userId", testUserId))
                .andExpect(status().isNotFound());

        verify(ideaListService).getIdeaList(testGroupId, testListId, testUserId);
    }

    @Test
    void deleteIdeaList_NonexistentList_Returns404() throws Exception {
        // Given: Service throws ResourceNotFoundException for delete operation
        doThrow(new ResourceNotFoundException("Idea list not found")).when(ideaListService)
                .deleteIdeaList(testGroupId, testListId, testUserId);

        // When/Then: HTTP 404, not 500 or other error
        mockMvc.perform(delete("/groups/{groupId}/idea-lists/{listId}", testGroupId, testListId)
                .contentType(MediaType.APPLICATION_JSON)
                .requestAttr("userId", testUserId))
                .andExpect(status().isNotFound());

        verify(ideaListService).deleteIdeaList(testGroupId, testListId, testUserId);
    }

    @Test
    void updateIdea_NonexistentIdea_Returns404() throws Exception {
        // Given: Service throws ResourceNotFoundException
        UpdateIdeaRequest request = new UpdateIdeaRequest();
        request.setName("Updated Name");
        
        doThrow(new ResourceNotFoundException("Idea not found")).when(ideaListService)
                .updateIdea(eq(testGroupId), eq(testListId), eq(testIdeaId), any(UpdateIdeaRequest.class), eq(testUserId));

        // When/Then: HTTP 404 Not Found
        mockMvc.perform(patch("/groups/{groupId}/idea-lists/{listId}/ideas/{ideaId}", 
                testGroupId, testListId, testIdeaId)
                .contentType(MediaType.APPLICATION_JSON)
                .requestAttr("userId", testUserId)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());

        verify(ideaListService).updateIdea(eq(testGroupId), eq(testListId), eq(testIdeaId), any(UpdateIdeaRequest.class), eq(testUserId));
    }

    @Test
    void createIdeaList_ValidationException_Returns400() throws Exception {
        // Given: Service throws ValidationException for invalid data
        CreateIdeaListRequest request = new CreateIdeaListRequest();
        request.setName("Valid Name");
        request.setCategory(IdeaListCategory.RESTAURANT);
        
        doThrow(new ValidationException("Validation failed")).when(ideaListService)
                .createIdeaList(eq(testGroupId), any(CreateIdeaListRequest.class), eq(testUserId));

        // When/Then: HTTP 400 Bad Request
        mockMvc.perform(post("/groups/{groupId}/idea-lists", testGroupId)
                .contentType(MediaType.APPLICATION_JSON)
                .requestAttr("userId", testUserId)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(ideaListService).createIdeaList(eq(testGroupId), any(CreateIdeaListRequest.class), eq(testUserId));
    }

    @Test
    void deleteIdea_NoContent_Returns204() throws Exception {
        // Given: Successful delete operation (void return)
        doNothing().when(ideaListService).deleteIdea(testGroupId, testListId, testIdeaId, testUserId);

        // When/Then: HTTP 204 No Content
        mockMvc.perform(delete("/groups/{groupId}/idea-lists/{listId}/ideas/{ideaId}", 
                testGroupId, testListId, testIdeaId)
                .contentType(MediaType.APPLICATION_JSON)
                .requestAttr("userId", testUserId))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(ideaListService).deleteIdea(testGroupId, testListId, testIdeaId, testUserId);
    }

    // Helper methods

    private IdeaListDTO createSampleIdeaListDTO(String name, IdeaListCategory category) {
        IdeaListDTO dto = new IdeaListDTO();
        dto.setId(testListId);
        dto.setName(name);
        dto.setCategory(category);
        dto.setNote("Sample note");
        dto.setCreatedBy(testUserId);
        dto.setCreatedAt(Instant.now());
        return dto;
    }

    private IdeaDTO createSampleIdeaDTO(String name, String url, String note) {
        IdeaDTO dto = new IdeaDTO();
        dto.setId(testIdeaId);
        dto.setName(name);
        dto.setUrl(url);
        dto.setNote(note);
        dto.setAddedBy(testUserId);
        dto.setAddedTime(Instant.now());
        return dto;
    }
}