package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.exception.ParticipationNotFoundException;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.exception.UserNotFoundException;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.repository.ParticipationRepository;
import com.bbthechange.inviter.service.GroupTimestampService;
import com.bbthechange.inviter.service.HangoutService;
import com.bbthechange.inviter.service.UserService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ParticipationServiceImpl using Mockito.
 * Tests business logic, authorization rules, and proper delegation to repository layer.
 */
@ExtendWith(MockitoExtension.class)
class ParticipationServiceImplTest {

    private static final String HANGOUT_ID = "12345678-1234-1234-1234-123456789012";
    private static final String PARTICIPATION_ID = "11111111-1111-1111-1111-111111111111";
    private static final String USER_ID = "22222222-2222-2222-2222-222222222222";
    private static final String USER_ID_2 = "33333333-3333-3333-3333-333333333333";
    private static final String DISPLAY_NAME = "John Doe";
    private static final String DISPLAY_NAME_2 = "Jane Smith";
    private static final String IMAGE_PATH = "path/to/image.jpg";
    private static final String IMAGE_PATH_2 = "path/to/image2.jpg";

    @Mock
    private ParticipationRepository participationRepository;

    @Mock
    private HangoutService hangoutService;

    @Mock
    private UserService userService;

    @Mock
    private PointerUpdateService pointerUpdateService;

    @Mock
    private GroupTimestampService groupTimestampService;

    @InjectMocks
    private ParticipationServiceImpl participationService;

    // Test 1: createParticipation_ValidRequest_CreatesAndReturnsDTO
    @Test
    void createParticipation_ValidRequest_CreatesAndReturnsDTO() {
        // Given
        CreateParticipationRequest request = new CreateParticipationRequest(ParticipationType.TICKET_NEEDED);
        request.setSection("A");
        request.setSeat("12");

        User user = createUser(USER_ID, DISPLAY_NAME, IMAGE_PATH);

        Participation savedParticipation = new Participation(HANGOUT_ID, PARTICIPATION_ID, USER_ID, ParticipationType.TICKET_NEEDED);
        savedParticipation.setSection("A");
        savedParticipation.setSeat("12");

        doNothing().when(hangoutService).verifyUserCanAccessHangout(HANGOUT_ID, USER_ID);
        when(userService.getUserById(UUID.fromString(USER_ID))).thenReturn(Optional.of(user));
        when(participationRepository.save(any(Participation.class))).thenReturn(savedParticipation);

        // When
        ParticipationDTO result = participationService.createParticipation(HANGOUT_ID, request, USER_ID);

        // Then
        verify(hangoutService).verifyUserCanAccessHangout(HANGOUT_ID, USER_ID);
        verify(userService).getUserById(UUID.fromString(USER_ID));

        ArgumentCaptor<Participation> captor = ArgumentCaptor.forClass(Participation.class);
        verify(participationRepository).save(captor.capture());

        Participation capturedParticipation = captor.getValue();
        assertThat(capturedParticipation.getUserId()).isEqualTo(USER_ID);
        assertThat(capturedParticipation.getType()).isEqualTo(ParticipationType.TICKET_NEEDED);
        assertThat(capturedParticipation.getSection()).isEqualTo("A");
        assertThat(capturedParticipation.getSeat()).isEqualTo("12");

        assertThat(result.getDisplayName()).isEqualTo(DISPLAY_NAME);
        assertThat(result.getMainImagePath()).isEqualTo(IMAGE_PATH);
        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getType()).isEqualTo(ParticipationType.TICKET_NEEDED);
        assertThat(result.getSection()).isEqualTo("A");
        assertThat(result.getSeat()).isEqualTo("12");
    }

    // Test 2: createParticipation_UnauthorizedUser_ThrowsException
    @Test
    void createParticipation_UnauthorizedUser_ThrowsException() {
        // Given
        String unauthorizedUserId = "dddddddd-dddd-dddd-dddd-dddddddddddd";
        CreateParticipationRequest request = new CreateParticipationRequest(ParticipationType.TICKET_NEEDED);

        doThrow(new UnauthorizedException("User not authorized"))
                .when(hangoutService).verifyUserCanAccessHangout(HANGOUT_ID, unauthorizedUserId);

        // When / Then
        assertThatThrownBy(() ->
                participationService.createParticipation(HANGOUT_ID, request, unauthorizedUserId))
                .isInstanceOf(UnauthorizedException.class);

        verify(participationRepository, never()).save(any(Participation.class));
    }

    // Test 3: createParticipation_UserNotFound_ThrowsException
    @Test
    void createParticipation_UserNotFound_ThrowsException() {
        // Given
        CreateParticipationRequest request = new CreateParticipationRequest(ParticipationType.TICKET_NEEDED);

        doNothing().when(hangoutService).verifyUserCanAccessHangout(HANGOUT_ID, USER_ID);
        when(userService.getUserById(UUID.fromString(USER_ID))).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() ->
                participationService.createParticipation(HANGOUT_ID, request, USER_ID))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining(USER_ID);

        verify(participationRepository, never()).save(any(Participation.class));
    }

    // Test 4: getParticipations_ValidRequest_ReturnsDTOList
    @Test
    void getParticipations_ValidRequest_ReturnsDTOList() {
        // Given
        String user1Id = "44444444-4444-4444-4444-444444444444";
        String user2Id = "55555555-5555-5555-5555-555555555555";
        String part1Id = "66666666-6666-6666-6666-666666666666";
        String part2Id = "77777777-7777-7777-7777-777777777777";
        String part3Id = "88888888-8888-8888-8888-888888888888";
        String requestingUserId = "99999999-9999-9999-9999-999999999999";

        Participation participation1 = new Participation(HANGOUT_ID, part1Id, user1Id, ParticipationType.TICKET_NEEDED);
        Participation participation2 = new Participation(HANGOUT_ID, part2Id, user2Id, ParticipationType.TICKET_PURCHASED);
        Participation participation3 = new Participation(HANGOUT_ID, part3Id, user1Id, ParticipationType.TICKET_EXTRA);

        List<Participation> participations = Arrays.asList(participation1, participation2, participation3);

        User user1 = createUser(user1Id, "Alice", "alice.jpg");
        User user2 = createUser(user2Id, "Bob", "bob.jpg");

        doNothing().when(hangoutService).verifyUserCanAccessHangout(HANGOUT_ID, requestingUserId);
        when(participationRepository.findByHangoutId(HANGOUT_ID)).thenReturn(participations);
        when(userService.getUserById(UUID.fromString(user1Id))).thenReturn(Optional.of(user1));
        when(userService.getUserById(UUID.fromString(user2Id))).thenReturn(Optional.of(user2));

        // When
        List<ParticipationDTO> result = participationService.getParticipations(HANGOUT_ID, requestingUserId);

        // Then
        verify(hangoutService).verifyUserCanAccessHangout(HANGOUT_ID, requestingUserId);
        verify(participationRepository).findByHangoutId(HANGOUT_ID);

        assertThat(result).hasSize(3);

        // Verify first participation (user1)
        assertThat(result.get(0).getUserId()).isEqualTo(user1Id);
        assertThat(result.get(0).getDisplayName()).isEqualTo("Alice");
        assertThat(result.get(0).getMainImagePath()).isEqualTo("alice.jpg");
        assertThat(result.get(0).getType()).isEqualTo(ParticipationType.TICKET_NEEDED);

        // Verify second participation (user2)
        assertThat(result.get(1).getUserId()).isEqualTo(user2Id);
        assertThat(result.get(1).getDisplayName()).isEqualTo("Bob");
        assertThat(result.get(1).getMainImagePath()).isEqualTo("bob.jpg");
        assertThat(result.get(1).getType()).isEqualTo(ParticipationType.TICKET_PURCHASED);

        // Verify third participation (user1 again)
        assertThat(result.get(2).getUserId()).isEqualTo(user1Id);
        assertThat(result.get(2).getDisplayName()).isEqualTo("Alice");
        assertThat(result.get(2).getMainImagePath()).isEqualTo("alice.jpg");
        assertThat(result.get(2).getType()).isEqualTo(ParticipationType.TICKET_EXTRA);

        // Verify getUserById called for each unique userId
        verify(userService, times(3)).getUserById(any(UUID.class));
    }

    // Test 5: getParticipations_EmptyList_ReturnsEmptyList
    @Test
    void getParticipations_EmptyList_ReturnsEmptyList() {
        // Given
        doNothing().when(hangoutService).verifyUserCanAccessHangout(HANGOUT_ID, USER_ID);
        when(participationRepository.findByHangoutId(HANGOUT_ID)).thenReturn(Collections.emptyList());

        // When
        List<ParticipationDTO> result = participationService.getParticipations(HANGOUT_ID, USER_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
        verify(userService, never()).getUserById(any(UUID.class));
    }

    // Test 6: getParticipation_ValidRequest_ReturnsDTO
    @Test
    void getParticipation_ValidRequest_ReturnsDTO() {
        // Given
        String requestingUserId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        Participation participation = new Participation(HANGOUT_ID, PARTICIPATION_ID, USER_ID_2, ParticipationType.SECTION);
        participation.setSection("B");

        User user = createUser(USER_ID_2, DISPLAY_NAME_2, IMAGE_PATH_2);

        doNothing().when(hangoutService).verifyUserCanAccessHangout(HANGOUT_ID, requestingUserId);
        when(participationRepository.findById(HANGOUT_ID, PARTICIPATION_ID)).thenReturn(Optional.of(participation));
        when(userService.getUserById(UUID.fromString(USER_ID_2))).thenReturn(Optional.of(user));

        // When
        ParticipationDTO result = participationService.getParticipation(HANGOUT_ID, PARTICIPATION_ID, requestingUserId);

        // Then
        verify(hangoutService).verifyUserCanAccessHangout(HANGOUT_ID, requestingUserId);
        verify(participationRepository).findById(HANGOUT_ID, PARTICIPATION_ID);
        verify(userService).getUserById(UUID.fromString(USER_ID_2));

        assertThat(result.getParticipationId()).isEqualTo(PARTICIPATION_ID);
        assertThat(result.getUserId()).isEqualTo(USER_ID_2);
        assertThat(result.getDisplayName()).isEqualTo(DISPLAY_NAME_2);
        assertThat(result.getMainImagePath()).isEqualTo(IMAGE_PATH_2);
        assertThat(result.getType()).isEqualTo(ParticipationType.SECTION);
        assertThat(result.getSection()).isEqualTo("B");
    }

    // Test 7: getParticipation_NotFound_ThrowsException
    @Test
    void getParticipation_NotFound_ThrowsException() {
        // Given
        String nonexistentId = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
        doNothing().when(hangoutService).verifyUserCanAccessHangout(HANGOUT_ID, USER_ID);
        when(participationRepository.findById(HANGOUT_ID, nonexistentId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() ->
                participationService.getParticipation(HANGOUT_ID, nonexistentId, USER_ID))
                .isInstanceOf(ParticipationNotFoundException.class)
                .hasMessageContaining(nonexistentId);

        verify(userService, never()).getUserById(any(UUID.class));
    }

    // Test 8: updateParticipation_ValidUpdate_UpdatesAndReturnsDTO
    @Test
    void updateParticipation_ValidUpdate_UpdatesAndReturnsDTO() {
        // Given
        Participation existingParticipation = new Participation(HANGOUT_ID, PARTICIPATION_ID, USER_ID, ParticipationType.TICKET_NEEDED);
        existingParticipation.setSection("A");
        existingParticipation.setSeat("10");

        UpdateParticipationRequest request = new UpdateParticipationRequest();
        request.setType(ParticipationType.TICKET_PURCHASED);  // Changed
        // section = null (no change)
        request.setSeat("12");  // Changed

        User user = createUser(USER_ID, DISPLAY_NAME, IMAGE_PATH);

        doNothing().when(hangoutService).verifyUserCanAccessHangout(HANGOUT_ID, USER_ID);
        when(participationRepository.findById(HANGOUT_ID, PARTICIPATION_ID)).thenReturn(Optional.of(existingParticipation));
        when(participationRepository.save(any(Participation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userService.getUserById(UUID.fromString(USER_ID))).thenReturn(Optional.of(user));

        // When
        ParticipationDTO result = participationService.updateParticipation(HANGOUT_ID, PARTICIPATION_ID, request, USER_ID);

        // Then
        verify(hangoutService).verifyUserCanAccessHangout(HANGOUT_ID, USER_ID);

        ArgumentCaptor<Participation> captor = ArgumentCaptor.forClass(Participation.class);
        verify(participationRepository).save(captor.capture());

        Participation capturedParticipation = captor.getValue();
        assertThat(capturedParticipation.getType()).isEqualTo(ParticipationType.TICKET_PURCHASED);  // Updated
        assertThat(capturedParticipation.getSection()).isEqualTo("A");  // Unchanged
        assertThat(capturedParticipation.getSeat()).isEqualTo("12");  // Updated

        assertThat(result.getType()).isEqualTo(ParticipationType.TICKET_PURCHASED);
        assertThat(result.getSection()).isEqualTo("A");
        assertThat(result.getSeat()).isEqualTo("12");
    }

    // Test 9: updateParticipation_NoUpdates_ThrowsException
    @Test
    void updateParticipation_NoUpdates_ThrowsException() {
        // Given
        UpdateParticipationRequest request = new UpdateParticipationRequest();
        // All fields are null, so hasUpdates() returns false

        // When / Then
        assertThatThrownBy(() ->
                participationService.updateParticipation(HANGOUT_ID, PARTICIPATION_ID, request, USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No updates provided");

        verify(hangoutService, never()).verifyUserCanAccessHangout(anyString(), anyString());
        verify(participationRepository, never()).findById(anyString(), anyString());
    }

    // Test 10: updateParticipation_ParticipationNotFound_ThrowsException
    @Test
    void updateParticipation_ParticipationNotFound_ThrowsException() {
        // Given
        String nonexistentId = "cccccccc-cccc-cccc-cccc-cccccccccccc";
        UpdateParticipationRequest request = new UpdateParticipationRequest();
        request.setType(ParticipationType.TICKET_PURCHASED);

        doNothing().when(hangoutService).verifyUserCanAccessHangout(HANGOUT_ID, USER_ID);
        when(participationRepository.findById(HANGOUT_ID, nonexistentId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() ->
                participationService.updateParticipation(HANGOUT_ID, nonexistentId, request, USER_ID))
                .isInstanceOf(ParticipationNotFoundException.class);

        verify(participationRepository, never()).save(any(Participation.class));
    }

    // Test 11: updateParticipation_NonOwnerEditing_SuccessfullyUpdates
    @Test
    void updateParticipation_NonOwnerEditing_SuccessfullyUpdates() {
        // Given - USER_ID_2 owns the participation, but USER_ID is editing it (different user)
        Participation existingParticipation = new Participation(HANGOUT_ID, PARTICIPATION_ID, USER_ID_2, ParticipationType.TICKET_NEEDED);
        existingParticipation.setSection("A");

        UpdateParticipationRequest request = new UpdateParticipationRequest();
        request.setSection("B");  // Only updating section

        User owner = createUser(USER_ID_2, DISPLAY_NAME_2, IMAGE_PATH_2);

        doNothing().when(hangoutService).verifyUserCanAccessHangout(HANGOUT_ID, USER_ID);
        when(participationRepository.findById(HANGOUT_ID, PARTICIPATION_ID)).thenReturn(Optional.of(existingParticipation));
        when(participationRepository.save(any(Participation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userService.getUserById(UUID.fromString(USER_ID_2))).thenReturn(Optional.of(owner));

        // When
        ParticipationDTO result = participationService.updateParticipation(HANGOUT_ID, PARTICIPATION_ID, request, USER_ID);

        // Then - should succeed because any group member can edit
        verify(hangoutService).verifyUserCanAccessHangout(HANGOUT_ID, USER_ID);
        verify(participationRepository).save(any(Participation.class));
        assertThat(result.getSection()).isEqualTo("B");
        assertThat(result.getUserId()).isEqualTo(USER_ID_2);  // Still owned by original user
    }

    // Test 12: updateParticipation_OnlyTypeUpdated_UpdatesTypeOnly
    @Test
    void updateParticipation_OnlyTypeUpdated_UpdatesTypeOnly() {
        // Given
        Participation existingParticipation = new Participation(HANGOUT_ID, PARTICIPATION_ID, USER_ID, ParticipationType.TICKET_NEEDED);
        existingParticipation.setSection("A");
        existingParticipation.setSeat("10");

        UpdateParticipationRequest request = new UpdateParticipationRequest();
        request.setType(ParticipationType.TICKET_PURCHASED);  // Only type updated
        // section and seat are null (no change)

        User user = createUser(USER_ID, DISPLAY_NAME, IMAGE_PATH);

        doNothing().when(hangoutService).verifyUserCanAccessHangout(HANGOUT_ID, USER_ID);
        when(participationRepository.findById(HANGOUT_ID, PARTICIPATION_ID)).thenReturn(Optional.of(existingParticipation));
        when(participationRepository.save(any(Participation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userService.getUserById(UUID.fromString(USER_ID))).thenReturn(Optional.of(user));

        // When
        ParticipationDTO result = participationService.updateParticipation(HANGOUT_ID, PARTICIPATION_ID, request, USER_ID);

        // Then
        ArgumentCaptor<Participation> captor = ArgumentCaptor.forClass(Participation.class);
        verify(participationRepository).save(captor.capture());

        Participation capturedParticipation = captor.getValue();
        assertThat(capturedParticipation.getType()).isEqualTo(ParticipationType.TICKET_PURCHASED);  // Updated
        assertThat(capturedParticipation.getSection()).isEqualTo("A");  // Unchanged
        assertThat(capturedParticipation.getSeat()).isEqualTo("10");  // Unchanged
    }

    // Test 13: updateParticipation_OnlySectionUpdated_UpdatesSectionOnly
    @Test
    void updateParticipation_OnlySectionUpdated_UpdatesSectionOnly() {
        // Given
        Participation existingParticipation = new Participation(HANGOUT_ID, PARTICIPATION_ID, USER_ID, ParticipationType.SECTION);
        existingParticipation.setSection("A");
        existingParticipation.setSeat("10");

        UpdateParticipationRequest request = new UpdateParticipationRequest();
        // type is null (no change)
        request.setSection("C");  // Only section updated
        // seat is null (no change)

        User user = createUser(USER_ID, DISPLAY_NAME, IMAGE_PATH);

        doNothing().when(hangoutService).verifyUserCanAccessHangout(HANGOUT_ID, USER_ID);
        when(participationRepository.findById(HANGOUT_ID, PARTICIPATION_ID)).thenReturn(Optional.of(existingParticipation));
        when(participationRepository.save(any(Participation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userService.getUserById(UUID.fromString(USER_ID))).thenReturn(Optional.of(user));

        // When
        ParticipationDTO result = participationService.updateParticipation(HANGOUT_ID, PARTICIPATION_ID, request, USER_ID);

        // Then
        ArgumentCaptor<Participation> captor = ArgumentCaptor.forClass(Participation.class);
        verify(participationRepository).save(captor.capture());

        Participation capturedParticipation = captor.getValue();
        assertThat(capturedParticipation.getType()).isEqualTo(ParticipationType.SECTION);  // Unchanged
        assertThat(capturedParticipation.getSection()).isEqualTo("C");  // Updated
        assertThat(capturedParticipation.getSeat()).isEqualTo("10");  // Unchanged
    }

    // Test 14: updateParticipation_OnlySeatUpdated_UpdatesSeatOnly
    @Test
    void updateParticipation_OnlySeatUpdated_UpdatesSeatOnly() {
        // Given
        Participation existingParticipation = new Participation(HANGOUT_ID, PARTICIPATION_ID, USER_ID, ParticipationType.SECTION);
        existingParticipation.setSection("A");
        existingParticipation.setSeat("10");

        UpdateParticipationRequest request = new UpdateParticipationRequest();
        // type is null (no change)
        // section is null (no change)
        request.setSeat("99");  // Only seat updated

        User user = createUser(USER_ID, DISPLAY_NAME, IMAGE_PATH);

        doNothing().when(hangoutService).verifyUserCanAccessHangout(HANGOUT_ID, USER_ID);
        when(participationRepository.findById(HANGOUT_ID, PARTICIPATION_ID)).thenReturn(Optional.of(existingParticipation));
        when(participationRepository.save(any(Participation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userService.getUserById(UUID.fromString(USER_ID))).thenReturn(Optional.of(user));

        // When
        ParticipationDTO result = participationService.updateParticipation(HANGOUT_ID, PARTICIPATION_ID, request, USER_ID);

        // Then
        ArgumentCaptor<Participation> captor = ArgumentCaptor.forClass(Participation.class);
        verify(participationRepository).save(captor.capture());

        Participation capturedParticipation = captor.getValue();
        assertThat(capturedParticipation.getType()).isEqualTo(ParticipationType.SECTION);  // Unchanged
        assertThat(capturedParticipation.getSection()).isEqualTo("A");  // Unchanged
        assertThat(capturedParticipation.getSeat()).isEqualTo("99");  // Updated
    }

    // Test 15: updateParticipation_AllFieldsUpdated_UpdatesAllFields
    @Test
    void updateParticipation_AllFieldsUpdated_UpdatesAllFields() {
        // Given
        Participation existingParticipation = new Participation(HANGOUT_ID, PARTICIPATION_ID, USER_ID, ParticipationType.TICKET_NEEDED);
        existingParticipation.setSection("A");
        existingParticipation.setSeat("10");

        UpdateParticipationRequest request = new UpdateParticipationRequest();
        request.setType(ParticipationType.TICKET_EXTRA);  // Updated
        request.setSection("VIP");  // Updated
        request.setSeat("1A");  // Updated

        User user = createUser(USER_ID, DISPLAY_NAME, IMAGE_PATH);

        doNothing().when(hangoutService).verifyUserCanAccessHangout(HANGOUT_ID, USER_ID);
        when(participationRepository.findById(HANGOUT_ID, PARTICIPATION_ID)).thenReturn(Optional.of(existingParticipation));
        when(participationRepository.save(any(Participation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userService.getUserById(UUID.fromString(USER_ID))).thenReturn(Optional.of(user));

        // When
        ParticipationDTO result = participationService.updateParticipation(HANGOUT_ID, PARTICIPATION_ID, request, USER_ID);

        // Then
        ArgumentCaptor<Participation> captor = ArgumentCaptor.forClass(Participation.class);
        verify(participationRepository).save(captor.capture());

        Participation capturedParticipation = captor.getValue();
        assertThat(capturedParticipation.getType()).isEqualTo(ParticipationType.TICKET_EXTRA);  // Updated
        assertThat(capturedParticipation.getSection()).isEqualTo("VIP");  // Updated
        assertThat(capturedParticipation.getSeat()).isEqualTo("1A");  // Updated
    }

    // Test 16: updateParticipation_TypeAndSectionUpdated_UpdatesBothFields
    @Test
    void updateParticipation_TypeAndSectionUpdated_UpdatesBothFields() {
        // Given
        Participation existingParticipation = new Participation(HANGOUT_ID, PARTICIPATION_ID, USER_ID, ParticipationType.TICKET_NEEDED);
        existingParticipation.setSection("A");
        existingParticipation.setSeat("10");

        UpdateParticipationRequest request = new UpdateParticipationRequest();
        request.setType(ParticipationType.CLAIMED_SPOT);  // Updated
        request.setSection("B");  // Updated
        // seat is null (no change)

        User user = createUser(USER_ID, DISPLAY_NAME, IMAGE_PATH);

        doNothing().when(hangoutService).verifyUserCanAccessHangout(HANGOUT_ID, USER_ID);
        when(participationRepository.findById(HANGOUT_ID, PARTICIPATION_ID)).thenReturn(Optional.of(existingParticipation));
        when(participationRepository.save(any(Participation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userService.getUserById(UUID.fromString(USER_ID))).thenReturn(Optional.of(user));

        // When
        ParticipationDTO result = participationService.updateParticipation(HANGOUT_ID, PARTICIPATION_ID, request, USER_ID);

        // Then
        ArgumentCaptor<Participation> captor = ArgumentCaptor.forClass(Participation.class);
        verify(participationRepository).save(captor.capture());

        Participation capturedParticipation = captor.getValue();
        assertThat(capturedParticipation.getType()).isEqualTo(ParticipationType.CLAIMED_SPOT);  // Updated
        assertThat(capturedParticipation.getSection()).isEqualTo("B");  // Updated
        assertThat(capturedParticipation.getSeat()).isEqualTo("10");  // Unchanged
    }

    // Test 17: updateParticipation_SectionAndSeatUpdated_UpdatesBothFields
    @Test
    void updateParticipation_SectionAndSeatUpdated_UpdatesBothFields() {
        // Given
        Participation existingParticipation = new Participation(HANGOUT_ID, PARTICIPATION_ID, USER_ID, ParticipationType.SECTION);
        existingParticipation.setSection("A");
        existingParticipation.setSeat("10");

        UpdateParticipationRequest request = new UpdateParticipationRequest();
        // type is null (no change)
        request.setSection("D");  // Updated
        request.setSeat("25");  // Updated

        User user = createUser(USER_ID, DISPLAY_NAME, IMAGE_PATH);

        doNothing().when(hangoutService).verifyUserCanAccessHangout(HANGOUT_ID, USER_ID);
        when(participationRepository.findById(HANGOUT_ID, PARTICIPATION_ID)).thenReturn(Optional.of(existingParticipation));
        when(participationRepository.save(any(Participation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userService.getUserById(UUID.fromString(USER_ID))).thenReturn(Optional.of(user));

        // When
        ParticipationDTO result = participationService.updateParticipation(HANGOUT_ID, PARTICIPATION_ID, request, USER_ID);

        // Then
        ArgumentCaptor<Participation> captor = ArgumentCaptor.forClass(Participation.class);
        verify(participationRepository).save(captor.capture());

        Participation capturedParticipation = captor.getValue();
        assertThat(capturedParticipation.getType()).isEqualTo(ParticipationType.SECTION);  // Unchanged
        assertThat(capturedParticipation.getSection()).isEqualTo("D");  // Updated
        assertThat(capturedParticipation.getSeat()).isEqualTo("25");  // Updated
    }

    // Test 18: deleteParticipation_ValidRequest_DeletesSuccessfully
    @Test
    void deleteParticipation_ValidRequest_DeletesSuccessfully() {
        // Given
        doNothing().when(hangoutService).verifyUserCanAccessHangout(HANGOUT_ID, USER_ID);
        doNothing().when(participationRepository).delete(HANGOUT_ID, PARTICIPATION_ID);

        // When
        participationService.deleteParticipation(HANGOUT_ID, PARTICIPATION_ID, USER_ID);

        // Then
        verify(hangoutService).verifyUserCanAccessHangout(HANGOUT_ID, USER_ID);
        verify(participationRepository).delete(HANGOUT_ID, PARTICIPATION_ID);
    }

    // Test 19: deleteParticipation_UnauthorizedUser_ThrowsException
    @Test
    void deleteParticipation_UnauthorizedUser_ThrowsException() {
        // Given
        String unauthorizedUserId = "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee";
        doThrow(new UnauthorizedException("User not authorized"))
                .when(hangoutService).verifyUserCanAccessHangout(HANGOUT_ID, unauthorizedUserId);

        // When / Then
        assertThatThrownBy(() ->
                participationService.deleteParticipation(HANGOUT_ID, PARTICIPATION_ID, unauthorizedUserId))
                .isInstanceOf(UnauthorizedException.class);

        verify(participationRepository, never()).delete(anyString(), anyString());
    }

    // Helper method to create User objects for testing
    private User createUser(String userId, String displayName, String imagePath) {
        User user = new User();
        user.setId(UUID.fromString(userId));
        user.setDisplayName(displayName);
        user.setMainImagePath(imagePath);
        return user;
    }

    // ============================================================================
    // POINTER SYNCHRONIZATION TESTS
    // ============================================================================

    @Nested
    class PointerSynchronizationTests {

        @Test
        void createParticipation_UpdatesAllAssociatedGroupPointers() {
            // Given
            String groupId1 = "group1-1111-1111-1111-111111111111";
            String groupId2 = "group2-2222-2222-2222-222222222222";

            CreateParticipationRequest request = new CreateParticipationRequest(ParticipationType.TICKET_NEEDED);
            User user = createUser(USER_ID, DISPLAY_NAME, IMAGE_PATH);

            Participation savedParticipation = new Participation(HANGOUT_ID, PARTICIPATION_ID, USER_ID, ParticipationType.TICKET_NEEDED);

            // Mock hangout with 2 associated groups
            Hangout hangout = new Hangout();
            hangout.setHangoutId(HANGOUT_ID);
            hangout.setAssociatedGroups(Arrays.asList(groupId1, groupId2));
            hangout.setTicketLink("https://tickets.example.com");
            hangout.setTicketsRequired(true);
            hangout.setDiscountCode("SAVE20");

            HangoutDetailDTO detailDTO = new HangoutDetailDTO(
                hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new ParticipationDTO(savedParticipation, DISPLAY_NAME, IMAGE_PATH)),
                List.of()
            );

            doNothing().when(hangoutService).verifyUserCanAccessHangout(HANGOUT_ID, USER_ID);
            when(userService.getUserById(UUID.fromString(USER_ID))).thenReturn(Optional.of(user));
            when(participationRepository.save(any(Participation.class))).thenReturn(savedParticipation);
            when(hangoutService.getHangoutDetail(HANGOUT_ID, USER_ID)).thenReturn(detailDTO);

            // When
            participationService.createParticipation(HANGOUT_ID, request, USER_ID);

            // Then - verify pointer update called for both groups
            verify(pointerUpdateService, times(2)).updatePointerWithRetry(
                anyString(), eq(HANGOUT_ID), any(Consumer.class), anyString()
            );
            verify(groupTimestampService).updateGroupTimestamps(Arrays.asList(groupId1, groupId2));
        }

        @Test
        void createParticipation_SetsTicketFieldsOnPointers() {
            // Given
            String groupId = "group1-1111-1111-1111-111111111111";

            CreateParticipationRequest request = new CreateParticipationRequest(ParticipationType.TICKET_NEEDED);
            User user = createUser(USER_ID, DISPLAY_NAME, IMAGE_PATH);

            Participation savedParticipation = new Participation(HANGOUT_ID, PARTICIPATION_ID, USER_ID, ParticipationType.TICKET_NEEDED);

            Hangout hangout = new Hangout();
            hangout.setHangoutId(HANGOUT_ID);
            hangout.setAssociatedGroups(List.of(groupId));
            hangout.setTicketLink("https://tickets.example.com");
            hangout.setTicketsRequired(true);
            hangout.setDiscountCode("SAVE20");

            HangoutDetailDTO detailDTO = new HangoutDetailDTO(
                hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new ParticipationDTO(savedParticipation, DISPLAY_NAME, IMAGE_PATH)),
                List.of()
            );

            doNothing().when(hangoutService).verifyUserCanAccessHangout(HANGOUT_ID, USER_ID);
            when(userService.getUserById(UUID.fromString(USER_ID))).thenReturn(Optional.of(user));
            when(participationRepository.save(any(Participation.class))).thenReturn(savedParticipation);
            when(hangoutService.getHangoutDetail(HANGOUT_ID, USER_ID)).thenReturn(detailDTO);

            // When
            participationService.createParticipation(HANGOUT_ID, request, USER_ID);

            // Then - capture the consumer and verify it sets ticket fields
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Consumer<HangoutPointer>> captor = ArgumentCaptor.forClass(Consumer.class);
            verify(pointerUpdateService).updatePointerWithRetry(
                eq(groupId), eq(HANGOUT_ID), captor.capture(), anyString()
            );

            // Execute the captured consumer on a test pointer
            HangoutPointer testPointer = new HangoutPointer();
            captor.getValue().accept(testPointer);

            // Verify ticket fields were set
            assertThat(testPointer.getTicketLink()).isEqualTo("https://tickets.example.com");
            assertThat(testPointer.getTicketsRequired()).isTrue();
            assertThat(testPointer.getDiscountCode()).isEqualTo("SAVE20");
        }

        @Test
        void createParticipation_SetsParticipationSummaryOnPointers() {
            // Given
            String groupId = "group1-1111-1111-1111-111111111111";

            CreateParticipationRequest request = new CreateParticipationRequest(ParticipationType.TICKET_NEEDED);
            User user = createUser(USER_ID, DISPLAY_NAME, IMAGE_PATH);

            Participation savedParticipation = new Participation(HANGOUT_ID, PARTICIPATION_ID, USER_ID, ParticipationType.TICKET_NEEDED);

            Hangout hangout = new Hangout();
            hangout.setHangoutId(HANGOUT_ID);
            hangout.setAssociatedGroups(List.of(groupId));

            ParticipationDTO participationDTO = new ParticipationDTO(savedParticipation, DISPLAY_NAME, IMAGE_PATH);

            HangoutDetailDTO detailDTO = new HangoutDetailDTO(
                hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(participationDTO),
                List.of()
            );

            doNothing().when(hangoutService).verifyUserCanAccessHangout(HANGOUT_ID, USER_ID);
            when(userService.getUserById(UUID.fromString(USER_ID))).thenReturn(Optional.of(user));
            when(participationRepository.save(any(Participation.class))).thenReturn(savedParticipation);
            when(hangoutService.getHangoutDetail(HANGOUT_ID, USER_ID)).thenReturn(detailDTO);

            // When
            participationService.createParticipation(HANGOUT_ID, request, USER_ID);

            // Then - capture the consumer and verify it sets participation summary
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Consumer<HangoutPointer>> captor = ArgumentCaptor.forClass(Consumer.class);
            verify(pointerUpdateService).updatePointerWithRetry(
                eq(groupId), eq(HANGOUT_ID), captor.capture(), anyString()
            );

            // Execute the captured consumer on a test pointer
            HangoutPointer testPointer = new HangoutPointer();
            captor.getValue().accept(testPointer);

            // Verify participation summary was set
            assertThat(testPointer.getParticipationSummary()).isNotNull();
            assertThat(testPointer.getParticipationSummary().getUsersNeedingTickets()).hasSize(1);
            assertThat(testPointer.getParticipationSummary().getUsersNeedingTickets().get(0).getDisplayName())
                .isEqualTo(DISPLAY_NAME);
        }

        @Test
        void createParticipation_WhenHangoutDetailThrowsException_LogsWarningAndContinues() {
            // Given
            CreateParticipationRequest request = new CreateParticipationRequest(ParticipationType.TICKET_NEEDED);
            User user = createUser(USER_ID, DISPLAY_NAME, IMAGE_PATH);

            Participation savedParticipation = new Participation(HANGOUT_ID, PARTICIPATION_ID, USER_ID, ParticipationType.TICKET_NEEDED);

            doNothing().when(hangoutService).verifyUserCanAccessHangout(HANGOUT_ID, USER_ID);
            when(userService.getUserById(UUID.fromString(USER_ID))).thenReturn(Optional.of(user));
            when(participationRepository.save(any(Participation.class))).thenReturn(savedParticipation);
            when(hangoutService.getHangoutDetail(HANGOUT_ID, USER_ID))
                .thenThrow(new UnauthorizedException("Test exception"));

            // When - should NOT throw, just log warning
            ParticipationDTO result = participationService.createParticipation(HANGOUT_ID, request, USER_ID);

            // Then - participation was still created successfully
            assertThat(result).isNotNull();
            assertThat(result.getType()).isEqualTo(ParticipationType.TICKET_NEEDED);

            // Verify pointer/timestamp services were never called
            verify(pointerUpdateService, never()).updatePointerWithRetry(any(), any(), any(), any());
            verify(groupTimestampService, never()).updateGroupTimestamps(any());
        }

        @Test
        void createParticipation_WithNoAssociatedGroups_SkipsPointerUpdates() {
            // Given
            CreateParticipationRequest request = new CreateParticipationRequest(ParticipationType.TICKET_NEEDED);
            User user = createUser(USER_ID, DISPLAY_NAME, IMAGE_PATH);

            Participation savedParticipation = new Participation(HANGOUT_ID, PARTICIPATION_ID, USER_ID, ParticipationType.TICKET_NEEDED);

            Hangout hangout = new Hangout();
            hangout.setHangoutId(HANGOUT_ID);
            hangout.setAssociatedGroups(List.of());  // No associated groups

            HangoutDetailDTO detailDTO = new HangoutDetailDTO(
                hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new ParticipationDTO(savedParticipation, DISPLAY_NAME, IMAGE_PATH)),
                List.of()
            );

            doNothing().when(hangoutService).verifyUserCanAccessHangout(HANGOUT_ID, USER_ID);
            when(userService.getUserById(UUID.fromString(USER_ID))).thenReturn(Optional.of(user));
            when(participationRepository.save(any(Participation.class))).thenReturn(savedParticipation);
            when(hangoutService.getHangoutDetail(HANGOUT_ID, USER_ID)).thenReturn(detailDTO);

            // When
            participationService.createParticipation(HANGOUT_ID, request, USER_ID);

            // Then - no pointer updates
            verify(pointerUpdateService, never()).updatePointerWithRetry(any(), any(), any(), any());
            verify(groupTimestampService, never()).updateGroupTimestamps(any());
        }

        @Test
        void createParticipation_GroupsParticipationsByType() {
            // Given
            String groupId = "group1-1111-1111-1111-111111111111";

            CreateParticipationRequest request = new CreateParticipationRequest(ParticipationType.TICKET_EXTRA);
            User user = createUser(USER_ID, DISPLAY_NAME, IMAGE_PATH);
            User user2 = createUser(USER_ID_2, DISPLAY_NAME_2, IMAGE_PATH_2);

            Participation p1 = new Participation(HANGOUT_ID, UUID.randomUUID().toString(), USER_ID, ParticipationType.TICKET_NEEDED);
            Participation p2 = new Participation(HANGOUT_ID, UUID.randomUUID().toString(), USER_ID_2, ParticipationType.TICKET_PURCHASED);
            Participation p3 = new Participation(HANGOUT_ID, UUID.randomUUID().toString(), USER_ID, ParticipationType.CLAIMED_SPOT);
            Participation p4 = new Participation(HANGOUT_ID, UUID.randomUUID().toString(), USER_ID, ParticipationType.TICKET_EXTRA);
            Participation p5 = new Participation(HANGOUT_ID, UUID.randomUUID().toString(), USER_ID_2, ParticipationType.TICKET_EXTRA);

            Participation savedParticipation = new Participation(HANGOUT_ID, PARTICIPATION_ID, USER_ID, ParticipationType.TICKET_EXTRA);

            Hangout hangout = new Hangout();
            hangout.setHangoutId(HANGOUT_ID);
            hangout.setAssociatedGroups(List.of(groupId));

            HangoutDetailDTO detailDTO = new HangoutDetailDTO(
                hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                Arrays.asList(
                    new ParticipationDTO(p1, DISPLAY_NAME, IMAGE_PATH),
                    new ParticipationDTO(p2, DISPLAY_NAME_2, IMAGE_PATH_2),
                    new ParticipationDTO(p3, DISPLAY_NAME, IMAGE_PATH),
                    new ParticipationDTO(p4, DISPLAY_NAME, IMAGE_PATH),
                    new ParticipationDTO(p5, DISPLAY_NAME_2, IMAGE_PATH_2)
                ),
                List.of()
            );

            doNothing().when(hangoutService).verifyUserCanAccessHangout(HANGOUT_ID, USER_ID);
            when(userService.getUserById(UUID.fromString(USER_ID))).thenReturn(Optional.of(user));
            when(participationRepository.save(any(Participation.class))).thenReturn(savedParticipation);
            when(hangoutService.getHangoutDetail(HANGOUT_ID, USER_ID)).thenReturn(detailDTO);

            // When
            participationService.createParticipation(HANGOUT_ID, request, USER_ID);

            // Then - capture the consumer and verify grouping
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Consumer<HangoutPointer>> captor = ArgumentCaptor.forClass(Consumer.class);
            verify(pointerUpdateService).updatePointerWithRetry(
                eq(groupId), eq(HANGOUT_ID), captor.capture(), anyString()
            );

            HangoutPointer testPointer = new HangoutPointer();
            captor.getValue().accept(testPointer);

            ParticipationSummaryDTO summary = testPointer.getParticipationSummary();
            assertThat(summary.getUsersNeedingTickets()).hasSize(1);  // 1 TICKET_NEEDED
            assertThat(summary.getUsersWithTickets()).hasSize(1);     // 1 TICKET_PURCHASED
            assertThat(summary.getUsersWithClaimedSpots()).hasSize(1); // 1 CLAIMED_SPOT
            assertThat(summary.getExtraTicketCount()).isEqualTo(2);   // 2 TICKET_EXTRA
        }

        @Test
        void createParticipation_DeduplicatesUsersByUserId() {
            // Given
            String groupId = "group1-1111-1111-1111-111111111111";

            CreateParticipationRequest request = new CreateParticipationRequest(ParticipationType.TICKET_NEEDED);
            User user = createUser(USER_ID, DISPLAY_NAME, IMAGE_PATH);

            // User has 2 TICKET_NEEDED participations (should appear once in list)
            Participation p1 = new Participation(HANGOUT_ID, UUID.randomUUID().toString(), USER_ID, ParticipationType.TICKET_NEEDED);
            Participation p2 = new Participation(HANGOUT_ID, UUID.randomUUID().toString(), USER_ID, ParticipationType.TICKET_NEEDED);
            Participation p3 = new Participation(HANGOUT_ID, UUID.randomUUID().toString(), USER_ID_2, ParticipationType.TICKET_NEEDED);

            Participation savedParticipation = new Participation(HANGOUT_ID, PARTICIPATION_ID, USER_ID, ParticipationType.TICKET_NEEDED);

            Hangout hangout = new Hangout();
            hangout.setHangoutId(HANGOUT_ID);
            hangout.setAssociatedGroups(List.of(groupId));

            User user2 = createUser(USER_ID_2, DISPLAY_NAME_2, IMAGE_PATH_2);

            HangoutDetailDTO detailDTO = new HangoutDetailDTO(
                hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                Arrays.asList(
                    new ParticipationDTO(p1, DISPLAY_NAME, IMAGE_PATH),
                    new ParticipationDTO(p2, DISPLAY_NAME, IMAGE_PATH),
                    new ParticipationDTO(p3, DISPLAY_NAME_2, IMAGE_PATH_2)
                ),
                List.of()
            );

            doNothing().when(hangoutService).verifyUserCanAccessHangout(HANGOUT_ID, USER_ID);
            when(userService.getUserById(UUID.fromString(USER_ID))).thenReturn(Optional.of(user));
            when(participationRepository.save(any(Participation.class))).thenReturn(savedParticipation);
            when(hangoutService.getHangoutDetail(HANGOUT_ID, USER_ID)).thenReturn(detailDTO);

            // When
            participationService.createParticipation(HANGOUT_ID, request, USER_ID);

            // Then - verify deduplication
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Consumer<HangoutPointer>> captor = ArgumentCaptor.forClass(Consumer.class);
            verify(pointerUpdateService).updatePointerWithRetry(
                eq(groupId), eq(HANGOUT_ID), captor.capture(), anyString()
            );

            HangoutPointer testPointer = new HangoutPointer();
            captor.getValue().accept(testPointer);

            // 2 unique users, not 3 participations
            assertThat(testPointer.getParticipationSummary().getUsersNeedingTickets()).hasSize(2);
        }

        @Test
        void createParticipation_IncludesAllReservationOffers() {
            // Given
            String groupId = "group1-1111-1111-1111-111111111111";

            CreateParticipationRequest request = new CreateParticipationRequest(ParticipationType.TICKET_NEEDED);
            User user = createUser(USER_ID, DISPLAY_NAME, IMAGE_PATH);

            Participation savedParticipation = new Participation(HANGOUT_ID, PARTICIPATION_ID, USER_ID, ParticipationType.TICKET_NEEDED);

            Hangout hangout = new Hangout();
            hangout.setHangoutId(HANGOUT_ID);
            hangout.setAssociatedGroups(List.of(groupId));

            ReservationOffer offer1 = new ReservationOffer(HANGOUT_ID, UUID.randomUUID().toString(), USER_ID, OfferType.TICKET);
            offer1.setStatus(OfferStatus.COLLECTING);
            ReservationOffer offer2 = new ReservationOffer(HANGOUT_ID, UUID.randomUUID().toString(), USER_ID_2, OfferType.RESERVATION);
            offer2.setStatus(OfferStatus.COMPLETED);
            ReservationOffer offer3 = new ReservationOffer(HANGOUT_ID, UUID.randomUUID().toString(), USER_ID, OfferType.TICKET);
            offer3.setStatus(OfferStatus.CANCELLED);

            HangoutDetailDTO detailDTO = new HangoutDetailDTO(
                hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new ParticipationDTO(savedParticipation, DISPLAY_NAME, IMAGE_PATH)),
                Arrays.asList(
                    new ReservationOfferDTO(offer1, DISPLAY_NAME, IMAGE_PATH),
                    new ReservationOfferDTO(offer2, DISPLAY_NAME_2, IMAGE_PATH_2),
                    new ReservationOfferDTO(offer3, DISPLAY_NAME, IMAGE_PATH)
                )
            );

            doNothing().when(hangoutService).verifyUserCanAccessHangout(HANGOUT_ID, USER_ID);
            when(userService.getUserById(UUID.fromString(USER_ID))).thenReturn(Optional.of(user));
            when(participationRepository.save(any(Participation.class))).thenReturn(savedParticipation);
            when(hangoutService.getHangoutDetail(HANGOUT_ID, USER_ID)).thenReturn(detailDTO);

            // When
            participationService.createParticipation(HANGOUT_ID, request, USER_ID);

            // Then - verify all offers are included (not filtered by status)
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Consumer<HangoutPointer>> captor = ArgumentCaptor.forClass(Consumer.class);
            verify(pointerUpdateService).updatePointerWithRetry(
                eq(groupId), eq(HANGOUT_ID), captor.capture(), anyString()
            );

            HangoutPointer testPointer = new HangoutPointer();
            captor.getValue().accept(testPointer);

            assertThat(testPointer.getParticipationSummary().getReservationOffers()).hasSize(3);
        }
    }
}
