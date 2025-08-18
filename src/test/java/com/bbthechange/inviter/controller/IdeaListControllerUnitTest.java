package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.exception.*;
import com.bbthechange.inviter.model.IdeaListCategory;
import com.bbthechange.inviter.service.IdeaListService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for IdeaListController focusing on business logic and response handling.
 * Tests the controller's interaction with the service layer and proper HTTP status codes.
 */
@ExtendWith(MockitoExtension.class)
class IdeaListControllerUnitTest {

    @Mock
    private IdeaListService ideaListService;

    @Mock
    private HttpServletRequest httpRequest;

    private IdeaListController controller;
    
    private String testGroupId;
    private String testListId;
    private String testIdeaId;
    private String testUserId;

    @BeforeEach
    void setUp() {
        controller = new IdeaListController(ideaListService) {
            @Override
            protected String extractUserId(HttpServletRequest request) {
                return testUserId; // Override to return test user ID
            }
        };
        
        testGroupId = UUID.randomUUID().toString();
        testListId = UUID.randomUUID().toString();
        testIdeaId = UUID.randomUUID().toString();
        testUserId = UUID.randomUUID().toString();
    }

    // ===== HTTP CONTRACT & SUCCESS PATH TESTS =====

    @Test
    void getIdeaListsForGroup_ValidRequest_Returns200WithCorrectFormat() {
        // Given: Valid request and service returns data
        IdeaListDTO list1 = createSampleIdeaListDTO("Restaurants", IdeaListCategory.RESTAURANT);
        IdeaListDTO list2 = createSampleIdeaListDTO("Movies", IdeaListCategory.MOVIE);
        List<IdeaListDTO> expectedLists = Arrays.asList(list1, list2);
        
        when(ideaListService.getIdeaListsForGroup(testGroupId, testUserId)).thenReturn(expectedLists);

        // When: Call controller method
        ResponseEntity<List<IdeaListDTO>> response = controller.getIdeaListsForGroup(testGroupId, httpRequest);

        // Then: HTTP 200 status and correct data structure
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody().get(0).getName()).isEqualTo("Restaurants");
        assertThat(response.getBody().get(0).getCategory()).isEqualTo(IdeaListCategory.RESTAURANT);
        assertThat(response.getBody().get(1).getName()).isEqualTo("Movies");
        assertThat(response.getBody().get(1).getCategory()).isEqualTo(IdeaListCategory.MOVIE);

        verify(ideaListService).getIdeaListsForGroup(testGroupId, testUserId);
    }

    @Test
    void createIdeaList_ValidRequest_Returns201WithCreatedResource() {
        // Given: Valid authenticated request
        CreateIdeaListRequest request = new CreateIdeaListRequest();
        request.setName("My Restaurant List");
        request.setCategory(IdeaListCategory.RESTAURANT);
        request.setNote("Places to try");

        IdeaListDTO createdList = createSampleIdeaListDTO("My Restaurant List", IdeaListCategory.RESTAURANT);
        when(ideaListService.createIdeaList(eq(testGroupId), any(CreateIdeaListRequest.class), eq(testUserId)))
                .thenReturn(createdList);

        // When: Call controller method
        ResponseEntity<IdeaListDTO> response = controller.createIdeaList(testGroupId, request, httpRequest);

        // Then: HTTP 201 Created status and response body contains created list with generated ID
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(createdList.getId());
        assertThat(response.getBody().getName()).isEqualTo("My Restaurant List");
        assertThat(response.getBody().getCategory()).isEqualTo(IdeaListCategory.RESTAURANT);
        assertThat(response.getBody().getNote()).isEqualTo("Sample note"); // This matches what createSampleIdeaListDTO returns
        assertThat(response.getBody().getCreatedBy()).isEqualTo(testUserId);
        assertThat(response.getBody().getCreatedAt()).isNotNull();
        assertThat(response.getBody().getIdeas()).isEmpty();

        verify(ideaListService).createIdeaList(eq(testGroupId), any(CreateIdeaListRequest.class), eq(testUserId));
    }

    @Test
    void getIdeaList_ValidRequest_Returns200WithData() {
        // Given: Valid request and existing list
        IdeaListDTO existingList = createSampleIdeaListDTO("My List", IdeaListCategory.BOOK);
        when(ideaListService.getIdeaList(testGroupId, testListId, testUserId)).thenReturn(existingList);

        // When: Call controller method
        ResponseEntity<IdeaListDTO> response = controller.getIdeaList(testGroupId, testListId, httpRequest);

        // Then: HTTP 200 with correct data
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getName()).isEqualTo("My List");
        assertThat(response.getBody().getCategory()).isEqualTo(IdeaListCategory.BOOK);

        verify(ideaListService).getIdeaList(testGroupId, testListId, testUserId);
    }

    @Test
    void updateIdeaList_ValidRequest_Returns200WithUpdatedData() {
        // Given: Valid update request
        UpdateIdeaListRequest request = new UpdateIdeaListRequest();
        request.setName("Updated Name");

        IdeaListDTO updatedList = createSampleIdeaListDTO("Updated Name", IdeaListCategory.RESTAURANT);
        when(ideaListService.updateIdeaList(eq(testGroupId), eq(testListId), any(UpdateIdeaListRequest.class), eq(testUserId)))
                .thenReturn(updatedList);

        // When: Call controller method
        ResponseEntity<IdeaListDTO> response = controller.updateIdeaList(testGroupId, testListId, request, httpRequest);

        // Then: HTTP 200 with updated data
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getName()).isEqualTo("Updated Name");

        verify(ideaListService).updateIdeaList(eq(testGroupId), eq(testListId), any(UpdateIdeaListRequest.class), eq(testUserId));
    }

    @Test
    void deleteIdeaList_ValidRequest_Returns204NoContent() {
        // Given: Valid delete request
        doNothing().when(ideaListService).deleteIdeaList(testGroupId, testListId, testUserId);

        // When: Call controller method
        ResponseEntity<Void> response = controller.deleteIdeaList(testGroupId, testListId, httpRequest);

        // Then: HTTP 204 No Content
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();

        verify(ideaListService).deleteIdeaList(testGroupId, testListId, testUserId);
    }

    @Test
    void addIdeaToList_ValidRequest_Returns201WithCreatedIdea() {
        // Given: Valid idea creation request
        CreateIdeaRequest request = new CreateIdeaRequest();
        request.setName("Great Restaurant");
        request.setUrl("http://restaurant.com");
        request.setNote("Amazing food");

        IdeaDTO createdIdea = createSampleIdeaDTO("Great Restaurant", "http://restaurant.com", "Amazing food");
        when(ideaListService.addIdeaToList(eq(testGroupId), eq(testListId), any(CreateIdeaRequest.class), eq(testUserId)))
                .thenReturn(createdIdea);

        // When: Call controller method
        ResponseEntity<IdeaDTO> response = controller.addIdeaToList(testGroupId, testListId, request, httpRequest);

        // Then: HTTP 201 Created with new idea data
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getId()).isEqualTo(testIdeaId);
        assertThat(response.getBody().getName()).isEqualTo("Great Restaurant");
        assertThat(response.getBody().getUrl()).isEqualTo("http://restaurant.com");
        assertThat(response.getBody().getNote()).isEqualTo("Amazing food");
        assertThat(response.getBody().getAddedBy()).isEqualTo(testUserId);
        assertThat(response.getBody().getAddedTime()).isNotNull();

        verify(ideaListService).addIdeaToList(eq(testGroupId), eq(testListId), any(CreateIdeaRequest.class), eq(testUserId));
    }

    @Test
    void updateIdea_ValidRequest_Returns200WithUpdatedData() {
        // Given: Valid update request
        UpdateIdeaRequest request = new UpdateIdeaRequest();
        request.setNote("Updated note text");

        IdeaDTO updatedIdea = createSampleIdeaDTO("Original Name", "http://original.com", "Updated note text");
        when(ideaListService.updateIdea(eq(testGroupId), eq(testListId), eq(testIdeaId), any(UpdateIdeaRequest.class), eq(testUserId)))
                .thenReturn(updatedIdea);

        // When: Call controller method
        ResponseEntity<IdeaDTO> response = controller.updateIdea(testGroupId, testListId, testIdeaId, request, httpRequest);

        // Then: HTTP 200 with updated data (PATCH semantics verified)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getId()).isEqualTo(testIdeaId);
        assertThat(response.getBody().getName()).isEqualTo("Original Name");
        assertThat(response.getBody().getUrl()).isEqualTo("http://original.com");
        assertThat(response.getBody().getNote()).isEqualTo("Updated note text");

        verify(ideaListService).updateIdea(eq(testGroupId), eq(testListId), eq(testIdeaId), any(UpdateIdeaRequest.class), eq(testUserId));
    }

    @Test
    void deleteIdea_ValidRequest_Returns204NoContent() {
        // Given: Valid delete request
        doNothing().when(ideaListService).deleteIdea(testGroupId, testListId, testIdeaId, testUserId);

        // When: Call controller method
        ResponseEntity<Void> response = controller.deleteIdea(testGroupId, testListId, testIdeaId, httpRequest);

        // Then: HTTP 204 No Content
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();

        verify(ideaListService).deleteIdea(testGroupId, testListId, testIdeaId, testUserId);
    }

    // ===== AUTHORIZATION INTEGRATION TESTS =====

    @Test
    void getIdeaListsForGroup_UserNotGroupMember_PropagatesUnauthorizedException() {
        // Given: Service throws UnauthorizedException for unauthorized user
        doThrow(new UnauthorizedException("User is not a member of group")).when(ideaListService)
                .getIdeaListsForGroup(testGroupId, testUserId);

        // When/Then: Exception propagated (will be handled by BaseController @ExceptionHandler)
        assertThatThrownBy(() -> controller.getIdeaListsForGroup(testGroupId, httpRequest))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("User is not a member of group");

        verify(ideaListService).getIdeaListsForGroup(testGroupId, testUserId);
    }

    @Test
    void deleteIdeaList_UserNotGroupMember_PropagatesUnauthorizedException() {
        // Given: Service throws UnauthorizedException
        doThrow(new UnauthorizedException("User is not a member of group")).when(ideaListService)
                .deleteIdeaList(testGroupId, testListId, testUserId);

        // When/Then: Exception propagated for proper error handling
        assertThatThrownBy(() -> controller.deleteIdeaList(testGroupId, testListId, httpRequest))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("User is not a member of group");

        verify(ideaListService).deleteIdeaList(testGroupId, testListId, testUserId);
    }

    // ===== ERROR PROPAGATION TESTS =====

    @Test
    void getIdeaList_NonexistentList_PropagatesResourceNotFoundException() {
        // Given: Service throws ResourceNotFoundException for non-existent list
        doThrow(new ResourceNotFoundException("Idea list not found")).when(ideaListService)
                .getIdeaList(testGroupId, testListId, testUserId);

        // When/Then: Exception propagated for proper HTTP status mapping
        assertThatThrownBy(() -> controller.getIdeaList(testGroupId, testListId, httpRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Idea list not found");

        verify(ideaListService).getIdeaList(testGroupId, testListId, testUserId);
    }

    @Test
    void createIdeaList_ValidationException_PropagatesValidationException() {
        // Given: Service throws ValidationException for invalid data
        CreateIdeaListRequest request = new CreateIdeaListRequest();
        request.setName("Valid Name");
        request.setCategory(IdeaListCategory.RESTAURANT);
        
        doThrow(new ValidationException("Validation failed")).when(ideaListService)
                .createIdeaList(eq(testGroupId), any(CreateIdeaListRequest.class), eq(testUserId));

        // When/Then: Exception propagated for proper error handling
        assertThatThrownBy(() -> controller.createIdeaList(testGroupId, request, httpRequest))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Validation failed");

        verify(ideaListService).createIdeaList(eq(testGroupId), any(CreateIdeaListRequest.class), eq(testUserId));
    }

    @Test
    void updateIdea_NonexistentIdea_PropagatesResourceNotFoundException() {
        // Given: Service throws ResourceNotFoundException
        UpdateIdeaRequest request = new UpdateIdeaRequest();
        request.setName("Updated Name");
        
        doThrow(new ResourceNotFoundException("Idea not found")).when(ideaListService)
                .updateIdea(eq(testGroupId), eq(testListId), eq(testIdeaId), any(UpdateIdeaRequest.class), eq(testUserId));

        // When/Then: Exception propagated for proper status code handling
        assertThatThrownBy(() -> controller.updateIdea(testGroupId, testListId, testIdeaId, request, httpRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Idea not found");

        verify(ideaListService).updateIdea(eq(testGroupId), eq(testListId), eq(testIdeaId), any(UpdateIdeaRequest.class), eq(testUserId));
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