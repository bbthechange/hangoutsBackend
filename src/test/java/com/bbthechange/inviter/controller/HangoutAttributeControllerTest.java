package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.service.HangoutService;
import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for HangoutAttributeController
 * 
 * Test Coverage:
 * - POST /hangouts/{id}/attributes - Create attribute
 * - PUT /hangouts/{id}/attributes/{attributeId} - Update attribute 
 * - DELETE /hangouts/{id}/attributes/{attributeId} - Delete attribute
 * - Various error scenarios and edge cases
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HangoutAttributeController Tests")
class HangoutAttributeControllerTest {
    
    @Mock
    private HangoutService hangoutService;
    
    @Mock
    private HttpServletRequest httpServletRequest;
    
    @InjectMocks
    private HangoutAttributeController controller;
    
    private String validHangoutId;
    private String validAttributeId;
    private String validUserId;
    private HangoutAttributeDTO mockAttributeDTO;
    
    @BeforeEach
    void setUp() {
        validHangoutId = UUID.randomUUID().toString();
        validAttributeId = UUID.randomUUID().toString();
        validUserId = UUID.randomUUID().toString();
        
        mockAttributeDTO = new HangoutAttributeDTO(validAttributeId, "dress_code", "cocktail attire");
        
        // Mock the BaseController.extractUserId() method behavior
        when(httpServletRequest.getAttribute("userId")).thenReturn(validUserId);
    }
    
    @Nested
    @DisplayName("Create Attribute Tests")
    class CreateAttributeTests {
        
        @Test
        @DisplayName("Should create attribute successfully with valid request")
        void createAttribute_WithValidRequest_ShouldReturn201Created() {
            // Given
            CreateAttributeRequest request = new CreateAttributeRequest("dress_code", "cocktail attire");
            when(hangoutService.createAttribute(eq(validHangoutId), any(CreateAttributeRequest.class), eq(validUserId)))
                .thenReturn(mockAttributeDTO);
            
            // When
            ResponseEntity<HangoutAttributeDTO> response = controller.createAttribute(validHangoutId, request, httpServletRequest);
            
            // Then
            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertEquals(mockAttributeDTO, response.getBody());
            verify(hangoutService).createAttribute(eq(validHangoutId), any(CreateAttributeRequest.class), eq(validUserId));
        }
        
        @Test
        @DisplayName("Should throw UnauthorizedException when user lacks access")
        void createAttribute_WithUnauthorizedUser_ShouldThrowUnauthorizedException() {
            // Given
            CreateAttributeRequest request = new CreateAttributeRequest("dress_code", "cocktail attire");
            when(hangoutService.createAttribute(eq(validHangoutId), any(CreateAttributeRequest.class), eq(validUserId)))
                .thenThrow(new UnauthorizedException("User does not have access to this hangout"));
            
            // When & Then
            assertThrows(UnauthorizedException.class, () -> 
                controller.createAttribute(validHangoutId, request, httpServletRequest));
            
            verify(hangoutService).createAttribute(eq(validHangoutId), any(CreateAttributeRequest.class), eq(validUserId));
        }
        
        @Test
        @DisplayName("Should throw ValidationException when attribute name already exists")
        void createAttribute_WithDuplicateName_ShouldThrowValidationException() {
            // Given
            CreateAttributeRequest request = new CreateAttributeRequest("dress_code", "cocktail attire");
            when(hangoutService.createAttribute(eq(validHangoutId), any(CreateAttributeRequest.class), eq(validUserId)))
                .thenThrow(new ValidationException("Attribute with name 'dress_code' already exists"));
            
            // When & Then
            assertThrows(ValidationException.class, () -> 
                controller.createAttribute(validHangoutId, request, httpServletRequest));
            
            verify(hangoutService).createAttribute(eq(validHangoutId), any(CreateAttributeRequest.class), eq(validUserId));
        }
        
        @Test
        @DisplayName("Should throw ResourceNotFoundException when hangout doesn't exist")
        void createAttribute_WithNonExistentHangout_ShouldThrowResourceNotFoundException() {
            // Given
            CreateAttributeRequest request = new CreateAttributeRequest("dress_code", "cocktail attire");
            when(hangoutService.createAttribute(eq(validHangoutId), any(CreateAttributeRequest.class), eq(validUserId)))
                .thenThrow(new ResourceNotFoundException("Hangout not found: " + validHangoutId));
            
            // When & Then
            assertThrows(ResourceNotFoundException.class, () -> 
                controller.createAttribute(validHangoutId, request, httpServletRequest));
            
            verify(hangoutService).createAttribute(eq(validHangoutId), any(CreateAttributeRequest.class), eq(validUserId));
        }
    }
    
    @Nested
    @DisplayName("Update Attribute Tests")
    class UpdateAttributeTests {
        
        @Test
        @DisplayName("Should update attribute successfully with valid request")
        void updateAttribute_WithValidRequest_ShouldReturn200OK() {
            // Given
            UpdateAttributeRequest request = new UpdateAttributeRequest("dress_code", "business casual");
            HangoutAttributeDTO updatedDTO = new HangoutAttributeDTO(validAttributeId, "dress_code", "business casual");
            when(hangoutService.updateAttribute(eq(validHangoutId), eq(validAttributeId), any(UpdateAttributeRequest.class), eq(validUserId)))
                .thenReturn(updatedDTO);
            
            // When
            ResponseEntity<HangoutAttributeDTO> response = controller.updateAttribute(validHangoutId, validAttributeId, request, httpServletRequest);
            
            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(updatedDTO, response.getBody());
            verify(hangoutService).updateAttribute(eq(validHangoutId), eq(validAttributeId), any(UpdateAttributeRequest.class), eq(validUserId));
        }
        
        @Test
        @DisplayName("Should throw ResourceNotFoundException when attribute doesn't exist")
        void updateAttribute_WithNonExistentAttribute_ShouldThrowResourceNotFoundException() {
            // Given
            UpdateAttributeRequest request = new UpdateAttributeRequest("dress_code", "business casual");
            when(hangoutService.updateAttribute(eq(validHangoutId), eq(validAttributeId), any(UpdateAttributeRequest.class), eq(validUserId)))
                .thenThrow(new ResourceNotFoundException("Attribute not found: " + validAttributeId));
            
            // When & Then
            assertThrows(ResourceNotFoundException.class, () -> 
                controller.updateAttribute(validHangoutId, validAttributeId, request, httpServletRequest));
            
            verify(hangoutService).updateAttribute(eq(validHangoutId), eq(validAttributeId), any(UpdateAttributeRequest.class), eq(validUserId));
        }
        
        @Test
        @DisplayName("Should throw ValidationException when rename conflicts with existing attribute")
        void updateAttribute_WithRenameConflict_ShouldThrowValidationException() {
            // Given
            UpdateAttributeRequest request = new UpdateAttributeRequest("existing_name", "new value");
            when(hangoutService.updateAttribute(eq(validHangoutId), eq(validAttributeId), any(UpdateAttributeRequest.class), eq(validUserId)))
                .thenThrow(new ValidationException("Attribute with name 'existing_name' already exists"));
            
            // When & Then
            assertThrows(ValidationException.class, () -> 
                controller.updateAttribute(validHangoutId, validAttributeId, request, httpServletRequest));
            
            verify(hangoutService).updateAttribute(eq(validHangoutId), eq(validAttributeId), any(UpdateAttributeRequest.class), eq(validUserId));
        }
        
        @Test
        @DisplayName("Should support renaming attribute to new unique name")
        void updateAttribute_WithValidRename_ShouldReturnUpdatedAttribute() {
            // Given
            UpdateAttributeRequest request = new UpdateAttributeRequest("parking_info", "free parking available");
            HangoutAttributeDTO renamedDTO = new HangoutAttributeDTO(validAttributeId, "parking_info", "free parking available");
            when(hangoutService.updateAttribute(eq(validHangoutId), eq(validAttributeId), any(UpdateAttributeRequest.class), eq(validUserId)))
                .thenReturn(renamedDTO);
            
            // When
            ResponseEntity<HangoutAttributeDTO> response = controller.updateAttribute(validHangoutId, validAttributeId, request, httpServletRequest);
            
            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(renamedDTO, response.getBody());
            assertEquals("parking_info", response.getBody().getAttributeName());
            verify(hangoutService).updateAttribute(eq(validHangoutId), eq(validAttributeId), any(UpdateAttributeRequest.class), eq(validUserId));
        }
    }
    
    @Nested
    @DisplayName("Delete Attribute Tests")
    class DeleteAttributeTests {
        
        @Test
        @DisplayName("Should delete attribute successfully with valid request")
        void deleteAttribute_WithValidRequest_ShouldReturn200OK() {
            // Given
            doNothing().when(hangoutService).deleteAttribute(validHangoutId, validAttributeId, validUserId);
            
            // When
            ResponseEntity<Object> response = controller.deleteAttribute(validHangoutId, validAttributeId, httpServletRequest);
            
            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            @SuppressWarnings("unchecked")
            Map<String, String> responseBody = (Map<String, String>) response.getBody();
            assertEquals("Attribute deleted successfully", responseBody.get("message"));
            verify(hangoutService).deleteAttribute(validHangoutId, validAttributeId, validUserId);
        }
        
        @Test
        @DisplayName("Should throw UnauthorizedException when user lacks access")
        void deleteAttribute_WithUnauthorizedUser_ShouldThrowUnauthorizedException() {
            // Given
            doThrow(new UnauthorizedException("User does not have access to this hangout"))
                .when(hangoutService).deleteAttribute(validHangoutId, validAttributeId, validUserId);
            
            // When & Then
            assertThrows(UnauthorizedException.class, () -> 
                controller.deleteAttribute(validHangoutId, validAttributeId, httpServletRequest));
            
            verify(hangoutService).deleteAttribute(validHangoutId, validAttributeId, validUserId);
        }
        
        @Test
        @DisplayName("Should handle idempotent behavior - no exception for non-existent attribute")
        void deleteAttribute_IdempotentBehavior_ShouldReturn200OK() {
            // Given - Service doesn't throw exception for non-existent attribute (idempotent)
            doNothing().when(hangoutService).deleteAttribute(validHangoutId, validAttributeId, validUserId);
            
            // When
            ResponseEntity<Object> response = controller.deleteAttribute(validHangoutId, validAttributeId, httpServletRequest);
            
            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, String> responseBody = (Map<String, String>) response.getBody();
            assertEquals("Attribute deleted successfully", responseBody.get("message"));
            verify(hangoutService).deleteAttribute(validHangoutId, validAttributeId, validUserId);
        }
    }
    
    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {
        
        @Test
        @DisplayName("Should handle null string values in create request")
        void createAttribute_WithNullStringValue_ShouldCreateSuccessfully() {
            // Given
            CreateAttributeRequest request = new CreateAttributeRequest("optional_field", null);
            HangoutAttributeDTO nullValueDTO = new HangoutAttributeDTO(validAttributeId, "optional_field", null);
            when(hangoutService.createAttribute(eq(validHangoutId), any(CreateAttributeRequest.class), eq(validUserId)))
                .thenReturn(nullValueDTO);
            
            // When
            ResponseEntity<HangoutAttributeDTO> response = controller.createAttribute(validHangoutId, request, httpServletRequest);
            
            // Then
            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertEquals(nullValueDTO, response.getBody());
            assertNull(response.getBody().getStringValue());
            verify(hangoutService).createAttribute(eq(validHangoutId), any(CreateAttributeRequest.class), eq(validUserId));
        }
        
        @Test
        @DisplayName("Should handle empty string values in update request")
        void updateAttribute_WithEmptyStringValue_ShouldUpdateSuccessfully() {
            // Given
            UpdateAttributeRequest request = new UpdateAttributeRequest("dress_code", "");
            HangoutAttributeDTO emptyValueDTO = new HangoutAttributeDTO(validAttributeId, "dress_code", "");
            when(hangoutService.updateAttribute(eq(validHangoutId), eq(validAttributeId), any(UpdateAttributeRequest.class), eq(validUserId)))
                .thenReturn(emptyValueDTO);
            
            // When
            ResponseEntity<HangoutAttributeDTO> response = controller.updateAttribute(validHangoutId, validAttributeId, request, httpServletRequest);
            
            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(emptyValueDTO, response.getBody());
            assertEquals("", response.getBody().getStringValue());
            verify(hangoutService).updateAttribute(eq(validHangoutId), eq(validAttributeId), any(UpdateAttributeRequest.class), eq(validUserId));
        }
    }
}