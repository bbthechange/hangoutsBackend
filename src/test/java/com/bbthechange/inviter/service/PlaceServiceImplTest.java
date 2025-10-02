package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.exception.InvalidPlaceOwnerException;
import com.bbthechange.inviter.exception.PlaceNotFoundException;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.exception.ValidationException;
import com.bbthechange.inviter.model.Place;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.repository.PlaceRepository;
import com.bbthechange.inviter.util.InviterKeyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaceServiceImplTest {

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private GroupRepository groupRepository;

    @InjectMocks
    private PlaceServiceImpl placeService;

    private String userId;
    private String groupId;
    private String authenticatedUserId;

    @BeforeEach
    void setUp() {
        userId = "12345678-1234-1234-1234-123456789abc";
        groupId = "45678901-2345-2345-2345-456789abcdef";
        authenticatedUserId = "12345678-1234-1234-1234-123456789abc";
    }

    @Test
    void getPlaces_UserPlaces_FiltersArchivedPlaces() {
        // Given
        Place activePlace1 = createUserPlace(userId, "Home", true, InviterKeyFactory.STATUS_ACTIVE);
        Place archivedPlace = createUserPlace(userId, "Old Work", false, InviterKeyFactory.STATUS_ARCHIVED);
        Place activePlace2 = createUserPlace(userId, "Gym", false, InviterKeyFactory.STATUS_ACTIVE);

        when(placeRepository.findPlacesByOwner(InviterKeyFactory.getUserGsi1Pk(userId)))
            .thenReturn(Arrays.asList(activePlace1, archivedPlace, activePlace2));

        // When
        PlacesResponse response = placeService.getPlaces(userId, null, authenticatedUserId);

        // Then
        assertThat(response.getUserPlaces()).hasSize(2);
        assertThat(response.getUserPlaces()).extracting(PlaceDto::getNickname)
            .containsExactlyInAnyOrder("Home", "Gym");
        assertThat(response.getGroupPlaces()).isEmpty();

        // Verify archived place is not in response
        assertThat(response.getUserPlaces()).extracting(PlaceDto::getNickname)
            .doesNotContain("Old Work");
    }

    @Test
    void getPlaces_NeitherUserIdNorGroupId_ThrowsValidationException() {
        // When/Then
        assertThatThrownBy(() -> placeService.getPlaces(null, null, authenticatedUserId))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("At least one");
    }

    @Test
    void getPlaces_BothUserIdAndGroupId_ReturnsBothPlacesLists() {
        // Given
        Place userPlace = createUserPlace(userId, "Home", true, InviterKeyFactory.STATUS_ACTIVE);
        Place groupPlace = createUserPlace(userId, "Group Office", false, InviterKeyFactory.STATUS_ACTIVE);
        groupPlace.setOwnerType(InviterKeyFactory.OWNER_TYPE_GROUP);

        when(placeRepository.findPlacesByOwner(InviterKeyFactory.getUserGsi1Pk(userId)))
            .thenReturn(List.of(userPlace));
        when(groupRepository.isUserMemberOfGroup(groupId, authenticatedUserId)).thenReturn(true);
        when(placeRepository.findPlacesByOwner(InviterKeyFactory.getGroupPk(groupId)))
            .thenReturn(List.of(groupPlace));

        // When
        PlacesResponse response = placeService.getPlaces(userId, groupId, authenticatedUserId);

        // Then
        assertThat(response.getUserPlaces()).hasSize(1);
        assertThat(response.getUserPlaces()).extracting(PlaceDto::getNickname).containsExactly("Home");
        assertThat(response.getGroupPlaces()).hasSize(1);
        assertThat(response.getGroupPlaces()).extracting(PlaceDto::getNickname).containsExactly("Group Office");
    }

    @Test
    void getPlaces_GroupPlaces_ChecksMembership() {
        // Given
        String nonMemberUserId = "99999999-9999-9999-9999-999999999999";
        when(groupRepository.isUserMemberOfGroup(groupId, nonMemberUserId)).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> placeService.getPlaces(null, groupId, nonMemberUserId))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("not a member");

        verify(groupRepository).isUserMemberOfGroup(groupId, nonMemberUserId);
    }

    @Test
    void createPlace_UserPrimaryPlace_UnsetsExistingPrimary() {
        // Given
        Place existingPrimary = createUserPlace(userId, "Old Primary", true, InviterKeyFactory.STATUS_ACTIVE);
        existingPrimary.setGsi1pk(InviterKeyFactory.getUserGsi1Pk(userId));
        existingPrimary.setGsi1sk(InviterKeyFactory.PRIMARY_PLACE);

        when(placeRepository.findPrimaryPlaceForUser(userId))
            .thenReturn(Optional.of(existingPrimary));
        when(placeRepository.save(any(Place.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreatePlaceRequest request = createUserPlaceRequest(userId, "New Primary", true);

        // When
        PlaceDto result = placeService.createPlace(request, authenticatedUserId);

        // Then
        verify(placeRepository, times(2)).save(any(Place.class));

        ArgumentCaptor<Place> placeCaptor = ArgumentCaptor.forClass(Place.class);
        verify(placeRepository, times(2)).save(placeCaptor.capture());

        List<Place> savedPlaces = placeCaptor.getAllValues();

        // First save: old primary being unset
        Place oldPrimary = savedPlaces.get(0);
        assertThat(oldPrimary.isPrimary()).isFalse();
        assertThat(oldPrimary.getGsi1pk()).isNull();
        assertThat(oldPrimary.getGsi1sk()).isNull();

        // Second save: new primary
        Place newPrimary = savedPlaces.get(1);
        assertThat(newPrimary.isPrimary()).isTrue();
        assertThat(newPrimary.getGsi1pk()).isEqualTo(InviterKeyFactory.getUserGsi1Pk(userId));
        assertThat(newPrimary.getGsi1sk()).isEqualTo(InviterKeyFactory.PRIMARY_PLACE);
    }

    @Test
    void createPlace_GroupWithPrimaryFlag_ThrowsInvalidPlaceOwnerException() {
        // Given
        when(groupRepository.isUserMemberOfGroup(groupId, authenticatedUserId)).thenReturn(true);

        CreatePlaceRequest request = createGroupPlaceRequest(groupId, "Group HQ", true);

        // When/Then
        assertThatThrownBy(() -> placeService.createPlace(request, authenticatedUserId))
            .isInstanceOf(InvalidPlaceOwnerException.class)
            .hasMessageContaining("Groups cannot have primary");
    }

    @Test
    void createPlace_GroupPlace_RequiresMembership() {
        // Given
        String nonMemberUserId = "99999999-9999-9999-9999-999999999999";
        when(groupRepository.isUserMemberOfGroup(groupId, nonMemberUserId)).thenReturn(false);

        CreatePlaceRequest request = createGroupPlaceRequest(groupId, "Group HQ", false);

        // When/Then
        assertThatThrownBy(() -> placeService.createPlace(request, nonMemberUserId))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("not a member");
    }

    @Test
    void createPlace_UserPlace_OnlyOwnPlaces() {
        // Given
        String otherUserId = "45600000-4560-4560-4560-456000000000";
        CreatePlaceRequest request = createUserPlaceRequest(otherUserId, "Their Place", false);

        // When/Then
        assertThatThrownBy(() -> placeService.createPlace(request, authenticatedUserId))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("only create places for yourself");
    }

    @Test
    void updatePlace_ChangeToPrimary_UnsetsOldPrimary() {
        // Given
        String placeId = "abcdef01-2345-2345-2345-abcdef012345";
        Place currentPlace = createUserPlace(userId, "Current Place", false, InviterKeyFactory.STATUS_ACTIVE);
        currentPlace.setPlaceId(placeId);

        Place existingPrimary = createUserPlace(userId, "Old Primary", true, InviterKeyFactory.STATUS_ACTIVE);
        existingPrimary.setGsi1pk(InviterKeyFactory.getUserGsi1Pk(userId));
        existingPrimary.setGsi1sk(InviterKeyFactory.PRIMARY_PLACE);

        when(placeRepository.findByOwnerAndPlaceId(InviterKeyFactory.getUserGsi1Pk(userId), placeId))
            .thenReturn(Optional.of(currentPlace));
        when(placeRepository.findPrimaryPlaceForUser(userId))
            .thenReturn(Optional.of(existingPrimary));
        when(placeRepository.save(any(Place.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdatePlaceRequest request = new UpdatePlaceRequest();
        request.setIsPrimary(true);

        // When
        PlaceDto result = placeService.updatePlace(placeId, userId, null, request, authenticatedUserId);

        // Then
        verify(placeRepository, times(2)).save(any(Place.class));

        ArgumentCaptor<Place> placeCaptor = ArgumentCaptor.forClass(Place.class);
        verify(placeRepository, times(2)).save(placeCaptor.capture());

        List<Place> savedPlaces = placeCaptor.getAllValues();

        // First save: old primary being unset
        Place unsetPrimary = savedPlaces.get(0);
        assertThat(unsetPrimary.isPrimary()).isFalse();
        assertThat(unsetPrimary.getGsi1pk()).isNull();
        assertThat(unsetPrimary.getGsi1sk()).isNull();

        // Second save: current place set to primary
        Place updatedPlace = savedPlaces.get(1);
        assertThat(updatedPlace.isPrimary()).isTrue();
        assertThat(updatedPlace.getGsi1pk()).isEqualTo(InviterKeyFactory.getUserGsi1Pk(userId));
        assertThat(updatedPlace.getGsi1sk()).isEqualTo(InviterKeyFactory.PRIMARY_PLACE);
    }

    @Test
    void updatePlace_ArchivedPlace_ThrowsPlaceNotFoundException() {
        // Given
        String placeId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        Place archivedPlace = createUserPlace(userId, "Archived Place", false, InviterKeyFactory.STATUS_ARCHIVED);
        archivedPlace.setPlaceId(placeId);

        when(placeRepository.findByOwnerAndPlaceId(InviterKeyFactory.getUserGsi1Pk(userId), placeId))
            .thenReturn(Optional.of(archivedPlace));

        UpdatePlaceRequest request = new UpdatePlaceRequest();
        request.setNickname("New Name");

        // When/Then
        assertThatThrownBy(() -> placeService.updatePlace(placeId, userId, null, request, authenticatedUserId))
            .isInstanceOf(PlaceNotFoundException.class)
            .hasMessageContaining("archived");
    }

    @Test
    void deletePlace_PrimaryPlace_UnsetsPrimaryFlag() {
        // Given
        String placeId = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
        Place primaryPlace = createUserPlace(userId, "Primary Home", true, InviterKeyFactory.STATUS_ACTIVE);
        primaryPlace.setPlaceId(placeId);
        primaryPlace.setGsi1pk(InviterKeyFactory.getUserGsi1Pk(userId));
        primaryPlace.setGsi1sk(InviterKeyFactory.PRIMARY_PLACE);

        when(placeRepository.findByOwnerAndPlaceId(InviterKeyFactory.getUserGsi1Pk(userId), placeId))
            .thenReturn(Optional.of(primaryPlace));
        when(placeRepository.save(any(Place.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        placeService.deletePlace(placeId, userId, null, authenticatedUserId);

        // Then
        ArgumentCaptor<Place> placeCaptor = ArgumentCaptor.forClass(Place.class);
        verify(placeRepository).save(placeCaptor.capture());

        Place deletedPlace = placeCaptor.getValue();
        assertThat(deletedPlace.getStatus()).isEqualTo(InviterKeyFactory.STATUS_ARCHIVED);
        assertThat(deletedPlace.isPrimary()).isFalse();
        assertThat(deletedPlace.getGsi1pk()).isNull();
        assertThat(deletedPlace.getGsi1sk()).isNull();
    }

    // Helper methods to create test data

    private Place createUserPlace(String userId, String nickname, boolean isPrimary, String status) {
        Address address = new Address();
        address.setStreetAddress("123 Main St");
        address.setCity("TestCity");
        address.setState("TS");
        address.setPostalCode("12345");

        Place place = new Place(userId, nickname, address, "Test notes", isPrimary, userId);
        place.setStatus(status);
        if (isPrimary) {
            place.setGsi1pk(InviterKeyFactory.getUserGsi1Pk(userId));
            place.setGsi1sk(InviterKeyFactory.PRIMARY_PLACE);
        }
        return place;
    }

    private CreatePlaceRequest createUserPlaceRequest(String userId, String nickname, boolean isPrimary) {
        Address address = new Address();
        address.setStreetAddress("123 Main St");
        address.setCity("TestCity");
        address.setState("TS");
        address.setPostalCode("12345");

        OwnerDto owner = new OwnerDto();
        owner.setId(userId);
        owner.setType(InviterKeyFactory.OWNER_TYPE_USER);

        return new CreatePlaceRequest(owner, nickname, address, "Test notes", isPrimary);
    }

    private CreatePlaceRequest createGroupPlaceRequest(String groupId, String nickname, boolean isPrimary) {
        Address address = new Address();
        address.setStreetAddress("456 Group Ave");
        address.setCity("GroupCity");
        address.setState("GC");
        address.setPostalCode("67890");

        OwnerDto owner = new OwnerDto();
        owner.setId(groupId);
        owner.setType(InviterKeyFactory.OWNER_TYPE_GROUP);

        return new CreatePlaceRequest(owner, nickname, address, "Group notes", isPrimary);
    }
}
