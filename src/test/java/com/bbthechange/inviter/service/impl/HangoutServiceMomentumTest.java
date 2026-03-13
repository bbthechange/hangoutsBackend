package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.service.FuzzyTimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for momentum integration in HangoutServiceImpl.
 *
 * Uses LENIENT strictness because @BeforeEach sets up stubs for the auto-RSVP flow
 * that is only exercised by creation tests (not update/getDetail tests).
 *
 * Coverage:
 * - createHangout: initializeMomentum called with correct confirmed flag
 * - createHangout: auto-RSVP creator based on momentum mode
 * - updateHangout: manual confirmation sets fields; recomputeMomentum on relevant changes
 * - setUserInterest: recomputeMomentum called after save; exception is non-fatal
 * - getHangoutDetail: momentum DTO built when category is set; null when not set
 */
@MockitoSettings(strictness = Strictness.LENIENT)
class HangoutServiceMomentumTest extends HangoutServiceTestBase {

    private static final String USER_ID = "87654321-4321-4321-4321-210987654321";
    private static final String GROUP_ID = "11111111-1111-1111-1111-111111111111";
    private static final String HANGOUT_ID = "33333333-3333-3333-3333-333333333333";

    /**
     * Common mocks needed by createHangout — prevents NPE in most tests.
     */
    @BeforeEach
    void setUpCommonMocks() {
        // Group membership validation
        GroupMembership membership = createTestMembership(GROUP_ID, USER_ID, "Test Group");
        when(groupRepository.findMembership(anyString(), anyString()))
                .thenReturn(Optional.of(membership));

        // Repository returns a saved hangout with association
        Hangout savedHangout = createTestHangout(HANGOUT_ID);
        savedHangout.setAssociatedGroups(List.of(GROUP_ID));
        when(hangoutRepository.createHangoutWithAttributes(any(), anyList(), anyList(), anyList(), anyList()))
                .thenReturn(savedHangout);

        // Mocks for the auto-RSVP setUserInterest call inside createHangout
        when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(savedHangout));
        HangoutDetailData emptyDetail = HangoutDetailData.builder()
                .withHangout(savedHangout)
                .build();
        when(hangoutRepository.getHangoutDetailData(HANGOUT_ID)).thenReturn(emptyDetail);

        UserSummaryDTO user = createTestUser(USER_ID);
        when(userService.getUserSummary(UUID.fromString(USER_ID))).thenReturn(Optional.of(user));
        when(hangoutRepository.saveInterestLevel(any(InterestLevel.class))).thenReturn(createTestInterestLevel());
        doNothing().when(groupRepository).atomicallyUpdateParticipantCount(anyString(), anyString(), anyInt());
    }

    // ============================================================================
    // 1.2.1 Creation Tests
    // ============================================================================

    @Test
    void createHangout_floatIt_callsInitializeMomentumWithConfirmedFalse() {
        CreateHangoutRequest request = buildRequest();
        request.setConfirmed(false);

        hangoutService.createHangout(request, USER_ID);

        verify(momentumService).initializeMomentum(any(Hangout.class), eq(false), eq(USER_ID));
    }

    @Test
    void createHangout_lockItIn_callsInitializeMomentumWithConfirmedTrue() {
        CreateHangoutRequest request = buildRequest();
        request.setConfirmed(true);

        hangoutService.createHangout(request, USER_ID);

        verify(momentumService).initializeMomentum(any(Hangout.class), eq(true), eq(USER_ID));
    }

    @Test
    void createHangout_confirmedNull_treatedAsFalse() {
        CreateHangoutRequest request = buildRequest();
        request.setConfirmed(null);

        hangoutService.createHangout(request, USER_ID);

        verify(momentumService).initializeMomentum(any(Hangout.class), eq(false), eq(USER_ID));
    }

    @Test
    void createHangout_floatIt_autoRsvpsCreatorAsInterested() {
        CreateHangoutRequest request = buildRequest();
        request.setConfirmed(false);

        hangoutService.createHangout(request, USER_ID);

        // Verify that saveInterestLevel is called with INTERESTED status
        verify(hangoutRepository).saveInterestLevel(argThat(interest ->
                "INTERESTED".equals(interest.getStatus()) &&
                USER_ID.equals(interest.getUserId())
        ));
    }

    @Test
    void createHangout_lockItIn_autoRsvpsCreatorAsGoing() {
        CreateHangoutRequest request = buildRequest();
        request.setConfirmed(true);

        hangoutService.createHangout(request, USER_ID);

        // Verify that saveInterestLevel is called with GOING status
        verify(hangoutRepository).saveInterestLevel(argThat(interest ->
                "GOING".equals(interest.getStatus()) &&
                USER_ID.equals(interest.getUserId())
        ));
    }

    // ============================================================================
    // 1.2.2 Update Tests
    // ============================================================================

    @Test
    void updateHangout_confirmedTrue_setsConfirmedFieldsOnHangout() {
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        Hangout existingHangout = createTestHangout(hangoutId);
        existingHangout.setMomentumCategory(MomentumCategory.BUILDING);
        existingHangout.setAssociatedGroups(List.of(GROUP_ID));
        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(existingHangout));

        UpdateHangoutRequest request = new UpdateHangoutRequest();
        request.setConfirmed(true);

        hangoutService.updateHangout(hangoutId, request, USER_ID);

        // Verify the hangout was saved with CONFIRMED fields
        ArgumentCaptor<Hangout> captor = ArgumentCaptor.forClass(Hangout.class);
        verify(hangoutRepository).createHangout(captor.capture());
        Hangout saved = captor.getValue();
        assertThat(saved.getMomentumCategory()).isEqualTo(MomentumCategory.CONFIRMED);
        assertThat(saved.getConfirmedBy()).isEqualTo(USER_ID);
        assertThat(saved.getConfirmedAt()).isNotNull();
    }

    @Test
    void updateHangout_confirmedTrueButAlreadyConfirmed_doesNotReconfirm() {
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        Hangout existingHangout = createTestHangout(hangoutId);
        existingHangout.setMomentumCategory(MomentumCategory.CONFIRMED);
        existingHangout.setConfirmedBy("original-user");
        existingHangout.setConfirmedAt(1700000000000L);
        existingHangout.setAssociatedGroups(List.of(GROUP_ID));
        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(existingHangout));

        UpdateHangoutRequest request = new UpdateHangoutRequest();
        request.setConfirmed(true);

        hangoutService.updateHangout(hangoutId, request, USER_ID);

        // confirmedBy should NOT be overwritten
        ArgumentCaptor<Hangout> captor = ArgumentCaptor.forClass(Hangout.class);
        verify(hangoutRepository).createHangout(captor.capture());
        assertThat(captor.getValue().getConfirmedBy()).isEqualTo("original-user");
    }

    @Test
    void updateHangout_timeChanged_recomputesMomentum() {
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        Hangout existingHangout = createTestHangout(hangoutId);
        existingHangout.setAssociatedGroups(List.of(GROUP_ID));
        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(existingHangout));

        // Provide a new time (different from null existing)
        TimeInfo newTime = new TimeInfo();
        newTime.setStartTime("2025-10-01T18:00:00Z");
        UpdateHangoutRequest request = new UpdateHangoutRequest();
        request.setTimeInfo(newTime);

        FuzzyTimeService.TimeConversionResult timeResult = new FuzzyTimeService.TimeConversionResult(1759000000L, 1759007200L);
        when(fuzzyTimeService.convert(newTime)).thenReturn(timeResult);

        // Mock needed for post-update notification call
        HangoutDetailData detail = HangoutDetailData.builder().withHangout(existingHangout).build();
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(detail);

        hangoutService.updateHangout(hangoutId, request, USER_ID);

        verify(momentumService).recomputeMomentum(hangoutId);
    }

    @Test
    void updateHangout_locationChanged_recomputesMomentum() {
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        Hangout existingHangout = createTestHangout(hangoutId);
        existingHangout.setLocation(null); // no existing location
        existingHangout.setAssociatedGroups(List.of(GROUP_ID));
        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(existingHangout));

        Address newLocation = new Address();
        newLocation.setName("New Location");
        UpdateHangoutRequest request = new UpdateHangoutRequest();
        request.setLocation(newLocation);

        // Mock needed for post-update notification call
        HangoutDetailData detail = HangoutDetailData.builder().withHangout(existingHangout).build();
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(detail);

        hangoutService.updateHangout(hangoutId, request, USER_ID);

        verify(momentumService).recomputeMomentum(hangoutId);
    }

    @Test
    void updateHangout_ticketLinkChanged_recomputesMomentum() {
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        Hangout existingHangout = createTestHangout(hangoutId);
        existingHangout.setTicketLink("old-link");
        existingHangout.setAssociatedGroups(List.of(GROUP_ID));
        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(existingHangout));

        UpdateHangoutRequest request = new UpdateHangoutRequest();
        request.setTicketLink("new-link");

        hangoutService.updateHangout(hangoutId, request, USER_ID);

        verify(momentumService).recomputeMomentum(hangoutId);
    }

    @Test
    void updateHangout_onlyTitleChanged_doesNotRecomputeMomentum() {
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        Hangout existingHangout = createTestHangout(hangoutId);
        existingHangout.setAssociatedGroups(List.of(GROUP_ID));
        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(existingHangout));

        UpdateHangoutRequest request = new UpdateHangoutRequest();
        request.setTitle("New Title Only");

        hangoutService.updateHangout(hangoutId, request, USER_ID);

        verify(momentumService, never()).recomputeMomentum(any());
    }

    @Test
    void updateHangout_confirmedTrueWithNoOtherChanges_doesNotRecomputeMomentum() {
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        Hangout existingHangout = createTestHangout(hangoutId);
        existingHangout.setMomentumCategory(MomentumCategory.BUILDING);
        existingHangout.setAssociatedGroups(List.of(GROUP_ID));
        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(existingHangout));

        UpdateHangoutRequest request = new UpdateHangoutRequest();
        request.setConfirmed(true);
        // No time/location/ticket changes

        hangoutService.updateHangout(hangoutId, request, USER_ID);

        verify(momentumService, never()).recomputeMomentum(any());
    }

    // ============================================================================
    // 1.2.3 RSVP / setUserInterest Tests
    // ============================================================================

    @Test
    void setUserInterest_afterSave_recomputesMomentum() {
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        Hangout hangout = createTestHangout(hangoutId);
        hangout.setVisibility(EventVisibility.PUBLIC);
        hangout.setAssociatedGroups(List.of(GROUP_ID));

        HangoutDetailData data = HangoutDetailData.builder().withHangout(hangout).build();
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);

        UserSummaryDTO user = createTestUser(USER_ID);
        when(userService.getUserSummary(UUID.fromString(USER_ID))).thenReturn(Optional.of(user));
        when(hangoutRepository.saveInterestLevel(any())).thenReturn(createTestInterestLevel());
        doNothing().when(groupRepository).atomicallyUpdateParticipantCount(anyString(), anyString(), anyInt());

        hangoutService.setUserInterest(hangoutId, new SetInterestRequest("GOING", null), USER_ID);

        verify(momentumService).recomputeMomentum(hangoutId);
    }

    @Test
    void setUserInterest_momentumRecomputeException_doesNotPropagateException() {
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        Hangout hangout = createTestHangout(hangoutId);
        hangout.setVisibility(EventVisibility.PUBLIC);
        hangout.setAssociatedGroups(List.of(GROUP_ID));

        HangoutDetailData data = HangoutDetailData.builder().withHangout(hangout).build();
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);

        UserSummaryDTO user = createTestUser(USER_ID);
        when(userService.getUserSummary(UUID.fromString(USER_ID))).thenReturn(Optional.of(user));
        when(hangoutRepository.saveInterestLevel(any())).thenReturn(createTestInterestLevel());
        doNothing().when(groupRepository).atomicallyUpdateParticipantCount(anyString(), anyString(), anyInt());

        doThrow(new RuntimeException("cache miss")).when(momentumService).recomputeMomentum(any());

        // Should NOT throw — momentum recompute failure is non-fatal
        assertThatNoException().isThrownBy(() ->
                hangoutService.setUserInterest(hangoutId, new SetInterestRequest("GOING", null), USER_ID));
    }

    // ============================================================================
    // 1.2.4 getHangoutDetail Momentum Tests
    // ============================================================================

    @Test
    void getHangoutDetail_hangoutHasMomentumCategory_buildsMomentumDTO() {
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        Hangout hangout = createTestHangout(hangoutId);
        hangout.setVisibility(EventVisibility.PUBLIC);
        hangout.setMomentumCategory(MomentumCategory.GAINING_MOMENTUM);
        hangout.setAssociatedGroups(List.of(GROUP_ID));

        HangoutDetailData data = HangoutDetailData.builder().withHangout(hangout).build();
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);
        when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());

        MomentumDTO mockMomentum = new MomentumDTO();
        mockMomentum.setCategory("GAINING_MOMENTUM");
        when(momentumService.buildMomentumDTO(any(), eq(GROUP_ID))).thenReturn(mockMomentum);

        HangoutDetailDTO result = hangoutService.getHangoutDetail(hangoutId, USER_ID);

        assertThat(result.getMomentum()).isNotNull();
        verify(momentumService).buildMomentumDTO(any(), eq(GROUP_ID));
    }

    @Test
    void getHangoutDetail_hangoutHasNoMomentumCategory_momentumIsNull() {
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        Hangout hangout = createTestHangout(hangoutId);
        hangout.setVisibility(EventVisibility.PUBLIC);
        hangout.setMomentumCategory(null);

        HangoutDetailData data = HangoutDetailData.builder().withHangout(hangout).build();
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);
        when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());

        HangoutDetailDTO result = hangoutService.getHangoutDetail(hangoutId, USER_ID);

        assertThat(result.getMomentum()).isNull();
        verify(momentumService, never()).buildMomentumDTO(any(), any());
    }

    // ============================================================================
    // HELPER
    // ============================================================================

    private CreateHangoutRequest buildRequest() {
        CreateHangoutRequest request = new CreateHangoutRequest();
        request.setTitle("Test Hangout");
        request.setAssociatedGroups(List.of(GROUP_ID));
        return request;
    }
}
