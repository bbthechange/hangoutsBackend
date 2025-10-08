package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.exception.InvalidPlaceOwnerException;
import com.bbthechange.inviter.exception.PlaceNotFoundException;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.service.PlaceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for PlaceController
 *
 * Test Coverage:
 * - GET /places - Get user/group places
 * - POST /places - Create place
 * - PUT /places/{placeId} - Update place
 * - DELETE /places/{placeId} - Delete place
 * - Exception handling and validation
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlaceController Tests")
class PlaceControllerTest {

    @Mock
    private PlaceService placeService;

    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private PlaceController placeController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private String testUserId;
    private String testGroupId;
    private String testPlaceId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        testUserId = "12345678-1234-1234-1234-123456789012";
        testGroupId = "87654321-4321-4321-4321-210987654321";
        testPlaceId = "abcdef12-abcd-abcd-abcd-abcdefabcdef";

        // Override extractUserId for testing
        placeController = new PlaceController(placeService) {
            @Override
            protected String extractUserId(HttpServletRequest request) {
                return testUserId;
            }
        };
        mockMvc = MockMvcBuilders.standaloneSetup(placeController)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Nested
    @DisplayName("GET /places Tests")
    class GetPlacesTests {

        @Test
        @DisplayName("Test 1: getPlaces_ValidUserId_Returns200WithPlaces")
        void getPlaces_ValidUserId_Returns200WithPlaces() throws Exception {
            // Given
            PlaceDto place1 = PlaceDto.builder()
                .placeId("place-1")
                .nickname("Home")
                .ownerType("USER")
                .createdBy(testUserId)
                .primary(true)
                .build();

            PlaceDto place2 = PlaceDto.builder()
                .placeId("place-2")
                .nickname("Office")
                .ownerType("USER")
                .createdBy(testUserId)
                .primary(false)
                .build();

            PlacesResponse response = new PlacesResponse(
                Arrays.asList(place1, place2),
                Collections.emptyList()
            );

            when(placeService.getPlaces(eq(testUserId), isNull(), any()))
                .thenReturn(response);

            // When & Then
            mockMvc.perform(get("/places")
                    .param("userId", testUserId)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userPlaces").isArray())
                .andExpect(jsonPath("$.userPlaces.length()").value(2))
                .andExpect(jsonPath("$.groupPlaces").isArray())
                .andExpect(jsonPath("$.groupPlaces.length()").value(0));

            verify(placeService).getPlaces(eq(testUserId), isNull(), any());
        }

        @Test
        @DisplayName("Test 2: getPlaces_InvalidUuidFormat_Returns400")
        void getPlaces_InvalidUuidFormat_Returns400() throws Exception {
            // When & Then
            mockMvc.perform(get("/places")
                    .param("userId", "not-a-valid-uuid")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

            verify(placeService, never()).getPlaces(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Test 3: getPlaces_Unauthorized_Returns403")
        void getPlaces_Unauthorized_Returns403() throws Exception {
            // Given
            String otherUserId = "87654321-4321-4321-4321-210987654321";
            when(placeService.getPlaces(eq(otherUserId), isNull(), any()))
                .thenThrow(new UnauthorizedException("User not authorized to view these places"));

            // When & Then
            mockMvc.perform(get("/places")
                    .param("userId", otherUserId)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }
    }

    @Nested
    @DisplayName("POST /places Tests")
    class CreatePlaceTests {

        @Test
        @DisplayName("Test 4: createPlace_ValidRequest_Returns201Created")
        void createPlace_ValidRequest_Returns201Created() throws Exception {
            // Given
            Address address = new Address("Home", "123 Main St", "Springfield", "IL", "62701", "USA");

            CreatePlaceRequest request = new CreatePlaceRequest(
                new OwnerDto(testUserId, "USER"),
                "Home Sweet Home",
                address,
                "My primary residence",
                true
            );

            PlaceDto createdPlace = PlaceDto.builder()
                .placeId(testPlaceId)
                .nickname("Home Sweet Home")
                .address(address)
                .notes("My primary residence")
                .primary(true)
                .ownerType("USER")
                .createdBy(testUserId)
                .createdAt(System.currentTimeMillis())
                .build();

            when(placeService.createPlace(any(CreatePlaceRequest.class), any()))
                .thenReturn(createdPlace);

            // When & Then
            mockMvc.perform(post("/places")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.placeId").value(testPlaceId))
                .andExpect(jsonPath("$.nickname").value("Home Sweet Home"))
                .andExpect(jsonPath("$.primary").value(true));

            verify(placeService).createPlace(any(CreatePlaceRequest.class), any());
        }

        @Test
        @DisplayName("Test 5: createPlace_MissingRequiredField_Returns400")
        void createPlace_MissingRequiredField_Returns400() throws Exception {
            // Given - Request missing nickname field
            String requestJson = """
                {
                    "owner": {
                        "id": "%s",
                        "type": "USER"
                    },
                    "address": {
                        "streetAddress": "123 Main St",
                        "city": "Springfield",
                        "state": "IL",
                        "postalCode": "62701",
                        "country": "USA"
                    },
                    "isPrimary": true
                }
                """.formatted(testUserId);

            // When & Then
            mockMvc.perform(post("/places")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("nickname")));

            verify(placeService, never()).createPlace(any(), any());
        }

        @Test
        @DisplayName("Test 6: createPlace_InvalidOwnerType_Returns400")
        void createPlace_InvalidOwnerType_Returns400() throws Exception {
            // Given - Request with invalid owner type
            String requestJson = """
                {
                    "owner": {
                        "id": "%s",
                        "type": "INVALID"
                    },
                    "nickname": "Home",
                    "address": {
                        "streetAddress": "123 Main St",
                        "city": "Springfield",
                        "state": "IL",
                        "postalCode": "62701",
                        "country": "USA"
                    },
                    "isPrimary": true
                }
                """.formatted(testUserId);

            // When & Then
            mockMvc.perform(post("/places")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

            verify(placeService, never()).createPlace(any(), any());
        }

        @Test
        @DisplayName("Test 7: createPlace_GroupCannotBePrimary_Returns400")
        void createPlace_GroupCannotBePrimary_Returns400() throws Exception {
            // Given
            Address address = new Address("Group HQ", "123 Main St", "Springfield", "IL", "62701", "USA");

            CreatePlaceRequest request = new CreatePlaceRequest(
                new OwnerDto(testGroupId, "GROUP"),
                "Group Place",
                address,
                "Test notes",
                true // Groups cannot have primary places
            );

            when(placeService.createPlace(any(CreatePlaceRequest.class), any()))
                .thenThrow(new InvalidPlaceOwnerException("Groups cannot have primary places"));

            // When & Then
            mockMvc.perform(post("/places")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PLACE_OWNER"));
        }
    }

    @Nested
    @DisplayName("PUT /places/{placeId} Tests")
    class UpdatePlaceTests {

        @Test
        @DisplayName("Test 8: updatePlace_ValidRequest_Returns200")
        void updatePlace_ValidRequest_Returns200() throws Exception {
            // Given
            Address updatedAddress = new Address("New Office", "456 Oak Ave", "Chicago", "IL", "60601", "USA");

            UpdatePlaceRequest request = new UpdatePlaceRequest(
                "Updated Nickname",
                updatedAddress,
                "Updated notes",
                false
            );

            PlaceDto updatedPlace = PlaceDto.builder()
                .placeId(testPlaceId)
                .nickname("Updated Nickname")
                .address(updatedAddress)
                .notes("Updated notes")
                .primary(false)
                .ownerType("USER")
                .createdBy(testUserId)
                .updatedAt(System.currentTimeMillis())
                .build();

            when(placeService.updatePlace(eq(testPlaceId), eq(testUserId), isNull(), any(UpdatePlaceRequest.class), any()))
                .thenReturn(updatedPlace);

            // When & Then
            mockMvc.perform(put("/places/{placeId}", testPlaceId)
                    .param("userId", testUserId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeId").value(testPlaceId))
                .andExpect(jsonPath("$.nickname").value("Updated Nickname"))
                .andExpect(jsonPath("$.primary").value(false));

            verify(placeService).updatePlace(eq(testPlaceId), eq(testUserId), isNull(), any(UpdatePlaceRequest.class), any());
        }

        @Test
        @DisplayName("Test 9: updatePlace_PlaceNotFound_Returns404")
        void updatePlace_PlaceNotFound_Returns404() throws Exception {
            // Given
            String nonexistentId = "00000000-0000-0000-0000-000000000000";
            UpdatePlaceRequest request = new UpdatePlaceRequest(
                "Updated Nickname",
                null,
                null,
                null
            );

            when(placeService.updatePlace(eq(nonexistentId), eq(testUserId), isNull(), any(), any()))
                .thenThrow(new PlaceNotFoundException("Place not found"));

            // When & Then
            mockMvc.perform(put("/places/{placeId}", nonexistentId)
                    .param("userId", testUserId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PLACE_NOT_FOUND"));
        }

        @Test
        @DisplayName("Test 10: updatePlace_InvalidPlaceIdFormat_Returns400")
        void updatePlace_InvalidPlaceIdFormat_Returns400() throws Exception {
            // Given
            UpdatePlaceRequest request = new UpdatePlaceRequest(
                "Updated Nickname",
                null,
                null,
                null
            );

            // When & Then
            mockMvc.perform(put("/places/{placeId}", "invalid-uuid")
                    .param("userId", testUserId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

            verify(placeService, never()).updatePlace(anyString(), anyString(), anyString(), any(), any());
        }
    }

    @Nested
    @DisplayName("DELETE /places/{placeId} Tests")
    class DeletePlaceTests {

        @Test
        @DisplayName("Test 11: deletePlace_ValidRequest_Returns200")
        void deletePlace_ValidRequest_Returns200() throws Exception {
            // Given
            doNothing().when(placeService).deletePlace(eq(testPlaceId), eq(testUserId), isNull(), any());

            // When & Then
            mockMvc.perform(delete("/places/{placeId}", testPlaceId)
                    .param("userId", testUserId)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string("")); // Empty response body

            verify(placeService).deletePlace(eq(testPlaceId), eq(testUserId), isNull(), any());
        }

        @Test
        @DisplayName("Test 12: deletePlace_AlreadyArchived_Returns200")
        void deletePlace_AlreadyArchived_Returns200() throws Exception {
            // Given - Service handles idempotent deletes
            doNothing().when(placeService).deletePlace(eq(testPlaceId), eq(testUserId), isNull(), any());

            // When & Then - First delete
            mockMvc.perform(delete("/places/{placeId}", testPlaceId)
                    .param("userId", testUserId)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

            // When & Then - Second delete (idempotent)
            mockMvc.perform(delete("/places/{placeId}", testPlaceId)
                    .param("userId", testUserId)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

            verify(placeService, times(2)).deletePlace(eq(testPlaceId), eq(testUserId), isNull(), any());
        }
    }
}