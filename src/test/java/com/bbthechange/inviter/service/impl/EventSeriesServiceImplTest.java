package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.CreateHangoutRequest;
import com.bbthechange.inviter.dto.EventSeriesDetailDTO;
import com.bbthechange.inviter.dto.HangoutDetailDTO;
import com.bbthechange.inviter.dto.UpdateSeriesRequest;
import com.bbthechange.inviter.exception.RepositoryException;
import com.bbthechange.inviter.exception.ResourceNotFoundException;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.exception.ValidationException;
import com.bbthechange.inviter.model.EventSeries;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.model.HangoutPointer;
import com.bbthechange.inviter.model.SeriesPointer;
import com.bbthechange.inviter.model.User;
import com.bbthechange.inviter.repository.EventSeriesRepository;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.repository.SeriesTransactionRepository;
import com.bbthechange.inviter.repository.UserRepository;
import com.bbthechange.inviter.service.HangoutService;
import com.bbthechange.inviter.testutil.HangoutPointerTestBuilder;
import com.bbthechange.inviter.testutil.HangoutTestBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventSeriesServiceImplTest {

    @Mock
    private HangoutRepository hangoutRepository;
    
    @Mock
    private EventSeriesRepository eventSeriesRepository;
    
    @Mock
    private SeriesTransactionRepository seriesTransactionRepository;
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private HangoutService hangoutService;
    
    @Mock
    private GroupRepository groupRepository;
    
    @InjectMocks
    private EventSeriesServiceImpl eventSeriesService;

    @Test
    void convertToSeriesWithNewMember_Success() {
        // Given
        String existingHangoutId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        CreateHangoutRequest newMemberRequest = new CreateHangoutRequest();
        newMemberRequest.setTitle("New Member Hangout");
        String group1Id = UUID.randomUUID().toString();
        String group2Id = UUID.randomUUID().toString();
        newMemberRequest.setAssociatedGroups(Arrays.asList(group1Id, group2Id));
        
        Hangout existingHangout = HangoutTestBuilder.aHangout()
            .withId(existingHangoutId)
            .withTitle("Movie Night")
            .withGroups(group1Id, group2Id)
            .build();
        
        List<HangoutPointer> existingPointers = Arrays.asList(
            HangoutPointerTestBuilder.aPointer()
                .forGroup(group1Id)
                .forHangout(existingHangoutId)
                .build(),
            HangoutPointerTestBuilder.aPointer()
                .forGroup(group2Id)
                .forHangout(existingHangoutId)
                .build()
        );
        
        User user = new User();
        user.setId(UUID.fromString(userId));
        
        Hangout newHangout = HangoutTestBuilder.aHangout()
            .withTitle("New Member Hangout")
            .withGroups(group1Id, group2Id)
            .build();
        
        // Mock expectations
        when(hangoutRepository.findHangoutById(existingHangoutId))
            .thenReturn(Optional.of(existingHangout));
        when(hangoutRepository.findPointersForHangout(existingHangout))
            .thenReturn(existingPointers);
        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(hangoutService.hangoutFromHangoutRequest(newMemberRequest, userId))
            .thenReturn(newHangout);
        
        // When
        EventSeries result = eventSeriesService.convertToSeriesWithNewMember(
            existingHangoutId, newMemberRequest, userId);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSeriesId()).isNotNull();
        assertThat(result.getSeriesTitle()).isEqualTo("Movie Night");
        assertThat(result.getPrimaryEventId()).isEqualTo(existingHangoutId);
        assertThat(result.getHangoutIds()).hasSize(2);
        assertThat(result.getHangoutIds()).contains(existingHangoutId);
        assertThat(result.getGroupId()).isEqualTo(group1Id);
        
        // Verify repository method calls
        verify(hangoutRepository).findHangoutById(existingHangoutId);
        verify(hangoutRepository).findPointersForHangout(existingHangout);
        verify(userRepository).findById(userId);
        verify(hangoutService).hangoutFromHangoutRequest(newMemberRequest, userId);
        
        // Verify transaction repository is called with correct data
        ArgumentCaptor<EventSeries> seriesCaptor = ArgumentCaptor.forClass(EventSeries.class);
        ArgumentCaptor<Hangout> existingHangoutCaptor = ArgumentCaptor.forClass(Hangout.class);
        ArgumentCaptor<List<HangoutPointer>> existingPointersCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Hangout> newHangoutCaptor = ArgumentCaptor.forClass(Hangout.class);
        ArgumentCaptor<List<HangoutPointer>> newPointersCaptor = ArgumentCaptor.forClass(List.class);
        
        verify(seriesTransactionRepository).createSeriesWithNewPart(
            seriesCaptor.capture(),
            existingHangoutCaptor.capture(),
            existingPointersCaptor.capture(),
            newHangoutCaptor.capture(),
            newPointersCaptor.capture(),
            any(List.class) // SeriesPointer list
        );
        
        // Verify transaction parameters
        EventSeries capturedSeries = seriesCaptor.getValue();
        assertThat(capturedSeries.getSeriesId()).isNotNull();
        assertThat(capturedSeries.getHangoutIds()).hasSize(2);
        
        Hangout capturedExistingHangout = existingHangoutCaptor.getValue();
        assertThat(capturedExistingHangout.getSeriesId()).isEqualTo(capturedSeries.getSeriesId());
        
        List<HangoutPointer> capturedExistingPointers = existingPointersCaptor.getValue();
        assertThat(capturedExistingPointers).hasSize(2);
        assertThat(capturedExistingPointers.get(0).getSeriesId()).isEqualTo(capturedSeries.getSeriesId());
        assertThat(capturedExistingPointers.get(1).getSeriesId()).isEqualTo(capturedSeries.getSeriesId());
        
        Hangout capturedNewHangout = newHangoutCaptor.getValue();
        assertThat(capturedNewHangout.getSeriesId()).isEqualTo(capturedSeries.getSeriesId());
        
        List<HangoutPointer> capturedNewPointers = newPointersCaptor.getValue();
        assertThat(capturedNewPointers).hasSize(2);
        capturedNewPointers.forEach(pointer -> {
            assertThat(pointer.getSeriesId()).isEqualTo(capturedSeries.getSeriesId());
            assertThat(pointer.getHangoutId()).isEqualTo(capturedNewHangout.getHangoutId());
        });
    }
    
    @Test
    void convertToSeriesWithNewMember_HangoutNotFound() {
        // Given
        String existingHangoutId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        CreateHangoutRequest newMemberRequest = new CreateHangoutRequest();
        
        when(hangoutRepository.findHangoutById(existingHangoutId))
            .thenReturn(Optional.empty());
        
        // When & Then
        assertThatThrownBy(() -> eventSeriesService.convertToSeriesWithNewMember(
            existingHangoutId, newMemberRequest, userId))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Hangout not found: " + existingHangoutId);
        
        // Verify no transaction methods are called
        verify(seriesTransactionRepository, never()).createSeriesWithNewPart(any(), any(), any(), any(), any(), any());
    }
    
    @Test
    void convertToSeriesWithNewMember_NoPointers() {
        // Given
        String existingHangoutId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        CreateHangoutRequest newMemberRequest = new CreateHangoutRequest();
        
        Hangout existingHangout = HangoutTestBuilder.aHangout()
            .withId(existingHangoutId)
            .build();
        
        User user = new User();
        user.setId(UUID.fromString(userId));
        
        when(hangoutRepository.findHangoutById(existingHangoutId))
            .thenReturn(Optional.of(existingHangout));
        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(hangoutRepository.findPointersForHangout(existingHangout))
            .thenReturn(Collections.emptyList());
        
        // When & Then
        assertThatThrownBy(() -> eventSeriesService.convertToSeriesWithNewMember(
            existingHangoutId, newMemberRequest, userId))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("No HangoutPointer records found for hangout: " + existingHangoutId);
        
        // Verify transaction is not attempted
        verify(seriesTransactionRepository, never()).createSeriesWithNewPart(any(), any(), any(), any(), any(), any());
    }
    
    @Test
    void convertToSeriesWithNewMember_UserNotAuthorized() {
        // Given
        String existingHangoutId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        CreateHangoutRequest newMemberRequest = new CreateHangoutRequest();
        
        Hangout existingHangout = HangoutTestBuilder.aHangout()
            .withId(existingHangoutId)
            .build();
        
        when(hangoutRepository.findHangoutById(existingHangoutId))
            .thenReturn(Optional.of(existingHangout));
        when(userRepository.findById(userId))
            .thenReturn(Optional.empty());
        
        // When & Then
        assertThatThrownBy(() -> eventSeriesService.convertToSeriesWithNewMember(
            existingHangoutId, newMemberRequest, userId))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("User " + userId + " not found");
        
        // Verify no transaction methods are called
        verify(seriesTransactionRepository, never()).createSeriesWithNewPart(any(), any(), any(), any(), any(), any());
    }
    
    @Test
    void convertToSeriesWithNewMember_TransactionFailure() {
        // Given
        String existingHangoutId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        CreateHangoutRequest newMemberRequest = new CreateHangoutRequest();
        String groupId = UUID.randomUUID().toString();
        newMemberRequest.setAssociatedGroups(Arrays.asList(groupId));
        
        Hangout existingHangout = HangoutTestBuilder.aHangout()
            .withId(existingHangoutId)
            .withGroups(groupId)
            .build();
        
        List<HangoutPointer> existingPointers = Arrays.asList(
            HangoutPointerTestBuilder.aPointer()
                .forGroup(groupId)
                .forHangout(existingHangoutId)
                .build()
        );
        
        User user = new User();
        user.setId(UUID.fromString(userId));
        
        Hangout newHangout = HangoutTestBuilder.aHangout()
            .withGroups(groupId)
            .build();
        
        when(hangoutRepository.findHangoutById(existingHangoutId))
            .thenReturn(Optional.of(existingHangout));
        when(hangoutRepository.findPointersForHangout(existingHangout))
            .thenReturn(existingPointers);
        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(hangoutService.hangoutFromHangoutRequest(newMemberRequest, userId))
            .thenReturn(newHangout);
        
        RuntimeException originalException = new RuntimeException("Transaction failed");
        doThrow(originalException).when(seriesTransactionRepository)
            .createSeriesWithNewPart(any(), any(), any(), any(), any(), any());
        
        // When & Then
        assertThatThrownBy(() -> eventSeriesService.convertToSeriesWithNewMember(
            existingHangoutId, newMemberRequest, userId))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("Failed to create series atomically")
            .hasCause(originalException);
    }
    
    @Test
    void createHangoutInExistingSeries_Success() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        CreateHangoutRequest newMemberRequest = new CreateHangoutRequest();
        newMemberRequest.setTitle("New Event");
        String group1Id = UUID.randomUUID().toString();
        String group2Id = UUID.randomUUID().toString();
        newMemberRequest.setAssociatedGroups(Arrays.asList(group1Id, group2Id));
        
        EventSeries existingSeries = new EventSeries();
        existingSeries.setSeriesId(seriesId);
        String existingHangoutId = UUID.randomUUID().toString();
        existingSeries.setHangoutIds(new ArrayList<>(Arrays.asList(existingHangoutId)));
        
        User user = new User();
        user.setId(UUID.fromString(userId));
        
        // Mock the hangout creation through hangoutService
        Hangout newHangout = new Hangout();
        newHangout.setHangoutId(UUID.randomUUID().toString());
        newHangout.setTitle("New Event");
        newHangout.setAssociatedGroups(Arrays.asList(group1Id, group2Id));
        // Set timestamp to test timestamp conversion
        newHangout.setStartTimestamp(System.currentTimeMillis() / 1000);
        
        when(eventSeriesRepository.findById(seriesId))
            .thenReturn(Optional.of(existingSeries));
        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(hangoutService.hangoutFromHangoutRequest(newMemberRequest, userId))
            .thenReturn(newHangout);
        
        // When
        EventSeries result = eventSeriesService.createHangoutInExistingSeries(
            seriesId, newMemberRequest, userId);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSeriesId()).isEqualTo(seriesId);
        assertThat(result.getHangoutIds()).hasSize(2);
        assertThat(result.getHangoutIds()).contains(existingHangoutId);
        
        // Verify repository method calls
        verify(eventSeriesRepository).findById(seriesId);
        verify(userRepository).findById(userId);
        
        // Verify transaction repository is called with correct data
        ArgumentCaptor<String> seriesIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Hangout> newHangoutCaptor = ArgumentCaptor.forClass(Hangout.class);
        ArgumentCaptor<List<HangoutPointer>> newPointersCaptor = ArgumentCaptor.forClass(List.class);
        
        verify(seriesTransactionRepository).addPartToExistingSeries(
            seriesIdCaptor.capture(),
            newHangoutCaptor.capture(),
            newPointersCaptor.capture(),
            any(List.class) // SeriesPointer list
        );
        
        // Verify transaction parameters
        String capturedSeriesId = seriesIdCaptor.getValue();
        assertThat(capturedSeriesId).isEqualTo(seriesId);
        
        Hangout capturedNewHangout = newHangoutCaptor.getValue();
        assertThat(capturedNewHangout.getSeriesId()).isEqualTo(seriesId);
        assertThat(capturedNewHangout.getTitle()).isEqualTo("New Event");
        
        List<HangoutPointer> capturedNewPointers = newPointersCaptor.getValue();
        assertThat(capturedNewPointers).hasSize(2);
        capturedNewPointers.forEach(pointer -> {
            assertThat(pointer.getSeriesId()).isEqualTo(seriesId);
            assertThat(pointer.getHangoutId()).isEqualTo(capturedNewHangout.getHangoutId());
        });
    }
    
    @Test
    void createHangoutInExistingSeries_SeriesNotFound() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        CreateHangoutRequest newMemberRequest = new CreateHangoutRequest();
        
        when(eventSeriesRepository.findById(seriesId))
            .thenReturn(Optional.empty());
        
        // When & Then
        assertThatThrownBy(() -> eventSeriesService.createHangoutInExistingSeries(
            seriesId, newMemberRequest, userId))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("EventSeries not found: " + seriesId);
        
        // Verify no transaction methods are called
        verify(seriesTransactionRepository, never()).addPartToExistingSeries(any(), any(), any(), any());
    }

    // Tests for unlinkHangoutFromSeries() method

    @Test
    void unlinkHangoutFromSeries_WithValidData_RemovesHangoutFromSeries() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String hangoutId = UUID.randomUUID().toString();
        String anotherHangoutId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();

        EventSeries series = new EventSeries();
        series.setSeriesId(seriesId);
        series.setHangoutIds(new ArrayList<>(Arrays.asList(hangoutId, anotherHangoutId)));
        series.setVersion(1L);

        Hangout hangout = HangoutTestBuilder.aHangout()
            .withId(hangoutId)
            .withSeriesId(seriesId)
            .build();

        String group1Id = UUID.randomUUID().toString();
        String group2Id = UUID.randomUUID().toString();
        
        List<HangoutPointer> hangoutPointers = Arrays.asList(
            HangoutPointerTestBuilder.aPointer()
                .forGroup(group1Id)
                .forHangout(hangoutId)
                .withSeriesId(seriesId)
                .build(),
            HangoutPointerTestBuilder.aPointer()
                .forGroup(group2Id)
                .forHangout(hangoutId)
                .withSeriesId(seriesId)
                .build()
        );

        User user = new User();
        user.setId(UUID.fromString(userId));

        when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));
        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(hangoutRepository.findPointersForHangout(hangout)).thenReturn(hangoutPointers);

        // When
        eventSeriesService.unlinkHangoutFromSeries(seriesId, hangoutId, userId);

        // Then
        assertThat(series.getHangoutIds()).doesNotContain(hangoutId);
        assertThat(series.getHangoutIds()).contains(anotherHangoutId);
        assertThat(hangout.getSeriesId()).isNull();
        assertThat(series.getVersion()).isEqualTo(2L);

        hangoutPointers.forEach(pointer -> assertThat(pointer.getSeriesId()).isNull());

        verify(seriesTransactionRepository).unlinkHangoutFromSeries(
            eq(series), eq(hangout), eq(hangoutPointers), any(List.class));
    }

    @Test
    void unlinkHangoutFromSeries_WithLastHangout_DeletesEntireSeries() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String hangoutId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();

        EventSeries series = new EventSeries();
        series.setSeriesId(seriesId);
        series.setHangoutIds(new ArrayList<>(Arrays.asList(hangoutId)));

        Hangout hangout = HangoutTestBuilder.aHangout()
            .withId(hangoutId)
            .withSeriesId(seriesId)
            .build();

        String groupId = UUID.randomUUID().toString();
        
        List<HangoutPointer> hangoutPointers = Arrays.asList(
            HangoutPointerTestBuilder.aPointer()
                .forGroup(groupId)
                .forHangout(hangoutId)
                .withSeriesId(seriesId)
                .build()
        );

        User user = new User();
        user.setId(UUID.fromString(userId));

        when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));
        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(hangoutRepository.findPointersForHangout(hangout)).thenReturn(hangoutPointers);

        // When
        eventSeriesService.unlinkHangoutFromSeries(seriesId, hangoutId, userId);

        // Then
        verify(seriesTransactionRepository).deleteEntireSeries(series, hangout, hangoutPointers);
        verify(seriesTransactionRepository, never()).unlinkHangoutFromSeries(any(), any(), any(), any());
    }

    @Test
    void unlinkHangoutFromSeries_WithNonExistentSeries_ThrowsResourceNotFoundException() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String hangoutId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();

        when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> eventSeriesService.unlinkHangoutFromSeries(seriesId, hangoutId, userId))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("EventSeries not found: " + seriesId);

        verify(seriesTransactionRepository, never()).deleteEntireSeries(any(), any(), any());
        verify(seriesTransactionRepository, never()).unlinkHangoutFromSeries(any(), any(), any(), any());
    }

    @Test
    void unlinkHangoutFromSeries_WithNonExistentHangout_ThrowsResourceNotFoundException() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String hangoutId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();

        EventSeries series = new EventSeries();
        series.setSeriesId(seriesId);

        when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));
        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> eventSeriesService.unlinkHangoutFromSeries(seriesId, hangoutId, userId))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Hangout not found: " + hangoutId);

        verify(seriesTransactionRepository, never()).deleteEntireSeries(any(), any(), any());
        verify(seriesTransactionRepository, never()).unlinkHangoutFromSeries(any(), any(), any(), any());
    }

    @Test
    void unlinkHangoutFromSeries_WithHangoutNotInSeries_ThrowsValidationException() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String hangoutId = UUID.randomUUID().toString();
        String differentSeriesId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();

        EventSeries series = new EventSeries();
        series.setSeriesId(seriesId);

        Hangout hangout = HangoutTestBuilder.aHangout()
            .withId(hangoutId)
            .withSeriesId(differentSeriesId)
            .build();

        when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));
        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));

        // When & Then
        assertThatThrownBy(() -> eventSeriesService.unlinkHangoutFromSeries(seriesId, hangoutId, userId))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Hangout " + hangoutId + " is not part of series " + seriesId);

        verify(seriesTransactionRepository, never()).deleteEntireSeries(any(), any(), any());
        verify(seriesTransactionRepository, never()).unlinkHangoutFromSeries(any(), any(), any(), any());
    }

    @Test
    void unlinkHangoutFromSeries_WithInvalidUser_ThrowsUnauthorizedException() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String hangoutId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();

        EventSeries series = new EventSeries();
        series.setSeriesId(seriesId);

        Hangout hangout = HangoutTestBuilder.aHangout()
            .withId(hangoutId)
            .withSeriesId(seriesId)
            .build();

        when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));
        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> eventSeriesService.unlinkHangoutFromSeries(seriesId, hangoutId, userId))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("User " + userId + " not found");

        verify(seriesTransactionRepository, never()).deleteEntireSeries(any(), any(), any());
        verify(seriesTransactionRepository, never()).unlinkHangoutFromSeries(any(), any(), any(), any());
    }

    // Tests for updateSeriesAfterHangoutModification() method

    @Test
    void updateSeriesAfterHangoutModification_WithValidHangoutInSeries_UpdatesSeries() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String seriesId = UUID.randomUUID().toString();

        Hangout hangout = HangoutTestBuilder.aHangout()
            .withId(hangoutId)
            .withSeriesId(seriesId)
            .build();

        EventSeries series = new EventSeries();
        series.setSeriesId(seriesId);
        series.setGroupId(UUID.randomUUID().toString());
        series.setVersion(1L);

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));

        // When
        eventSeriesService.updateSeriesAfterHangoutModification(hangoutId);

        // Then
        assertThat(series.getVersion()).isEqualTo(2L);
        verify(seriesTransactionRepository).updateSeriesAfterHangoutChange(eq(series), any(List.class));
    }

    @Test
    void updateSeriesAfterHangoutModification_WithHangoutNotInSeries_SkipsUpdate() {
        // Given
        String hangoutId = UUID.randomUUID().toString();

        Hangout hangout = HangoutTestBuilder.aHangout()
            .withId(hangoutId)
            .withSeriesId(null)
            .build();

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));

        // When
        eventSeriesService.updateSeriesAfterHangoutModification(hangoutId);

        // Then
        verify(eventSeriesRepository, never()).findById(any());
        verify(seriesTransactionRepository, never()).updateSeriesAfterHangoutChange(any(), any());
    }

    @Test
    void updateSeriesAfterHangoutModification_WithNonExistentHangout_ThrowsResourceNotFoundException() {
        // Given
        String hangoutId = UUID.randomUUID().toString();

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> eventSeriesService.updateSeriesAfterHangoutModification(hangoutId))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Hangout not found: " + hangoutId);

        verify(seriesTransactionRepository, never()).updateSeriesAfterHangoutChange(any(), any());
    }

    @Test
    void updateSeriesAfterHangoutModification_WithOrphanedSeries_SkipsUpdateGracefully() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String seriesId = UUID.randomUUID().toString();

        Hangout hangout = HangoutTestBuilder.aHangout()
            .withId(hangoutId)
            .withSeriesId(seriesId)
            .build();

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.empty());

        // When
        eventSeriesService.updateSeriesAfterHangoutModification(hangoutId);

        // Then
        verify(seriesTransactionRepository, never()).updateSeriesAfterHangoutChange(any(), any());
    }

    // Tests for removeHangoutFromSeries() method

    @Test
    void removeHangoutFromSeries_WithHangoutInSeries_RemovesAndDeletesHangout() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String seriesId = UUID.randomUUID().toString();
        String anotherHangoutId = UUID.randomUUID().toString();

        Hangout hangout = HangoutTestBuilder.aHangout()
            .withId(hangoutId)
            .withSeriesId(seriesId)
            .build();

        EventSeries series = new EventSeries();
        series.setSeriesId(seriesId);
        series.setHangoutIds(new ArrayList<>(Arrays.asList(hangoutId, anotherHangoutId)));
        series.setVersion(1L);

        String groupId = UUID.randomUUID().toString();
        
        List<HangoutPointer> hangoutPointers = Arrays.asList(
            HangoutPointerTestBuilder.aPointer()
                .forGroup(groupId)
                .forHangout(hangoutId)
                .build()
        );

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));
        when(hangoutRepository.findPointersForHangout(hangout)).thenReturn(hangoutPointers);

        // When
        eventSeriesService.removeHangoutFromSeries(hangoutId);

        // Then
        assertThat(series.getHangoutIds()).doesNotContain(hangoutId);
        assertThat(series.getHangoutIds()).contains(anotherHangoutId);
        assertThat(series.getVersion()).isEqualTo(2L);

        verify(seriesTransactionRepository).removeHangoutFromSeries(
            eq(series), eq(hangout), eq(hangoutPointers), any(List.class));
        verify(seriesTransactionRepository, never()).deleteSeriesAndFinalHangout(any(), any(), any());
    }

    @Test
    void removeHangoutFromSeries_WithLastHangoutInSeries_DeletesSeriesAndHangout() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String seriesId = UUID.randomUUID().toString();

        Hangout hangout = HangoutTestBuilder.aHangout()
            .withId(hangoutId)
            .withSeriesId(seriesId)
            .build();

        EventSeries series = new EventSeries();
        series.setSeriesId(seriesId);
        series.setHangoutIds(new ArrayList<>(Arrays.asList(hangoutId)));

        String groupId = UUID.randomUUID().toString();
        
        List<HangoutPointer> hangoutPointers = Arrays.asList(
            HangoutPointerTestBuilder.aPointer()
                .forGroup(groupId)
                .forHangout(hangoutId)
                .build()
        );

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));
        when(hangoutRepository.findPointersForHangout(hangout)).thenReturn(hangoutPointers);

        // When
        eventSeriesService.removeHangoutFromSeries(hangoutId);

        // Then
        verify(seriesTransactionRepository).deleteSeriesAndFinalHangout(series, hangout, hangoutPointers);
        verify(seriesTransactionRepository, never()).removeHangoutFromSeries(any(), any(), any(), any());
    }

    @Test
    void removeHangoutFromSeries_WithStandaloneHangout_UsesStandardDeletion() {
        // Given
        String hangoutId = UUID.randomUUID().toString();

        Hangout hangout = HangoutTestBuilder.aHangout()
            .withId(hangoutId)
            .withSeriesId(null)
            .build();

        String groupId = UUID.randomUUID().toString();
        
        List<HangoutPointer> hangoutPointers = Arrays.asList(
            HangoutPointerTestBuilder.aPointer()
                .forGroup(groupId)
                .forHangout(hangoutId)
                .build()
        );

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        when(hangoutRepository.findPointersForHangout(hangout)).thenReturn(hangoutPointers);

        // When
        eventSeriesService.removeHangoutFromSeries(hangoutId);

        // Then
        verify(eventSeriesRepository, never()).findById(any());
        verify(seriesTransactionRepository, never()).removeHangoutFromSeries(any(), any(), any(), any());
        verify(seriesTransactionRepository, never()).deleteSeriesAndFinalHangout(any(), any(), any());
        
        // Verify standard deletion process
        for (HangoutPointer pointer : hangoutPointers) {
            verify(groupRepository).deleteHangoutPointer(pointer.getGroupId(), hangoutId);
        }
        verify(hangoutRepository).deleteHangout(hangoutId);
    }

    @Test
    void removeHangoutFromSeries_WithNonExistentHangout_ThrowsResourceNotFoundException() {
        // Given
        String hangoutId = UUID.randomUUID().toString();

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> eventSeriesService.removeHangoutFromSeries(hangoutId))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Hangout not found: " + hangoutId);

        verify(seriesTransactionRepository, never()).removeHangoutFromSeries(any(), any(), any(), any());
        verify(seriesTransactionRepository, never()).deleteSeriesAndFinalHangout(any(), any(), any());
    }

    // ============================================================================
    // HELPER METHOD TESTS - Test Plan 4
    // ============================================================================

    // updateSeriesTimestamps() Tests

    @Test
    void updateSeriesTimestamps_WithMultipleHangouts_CalculatesCorrectRange() throws Exception {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String hangout1Id = UUID.randomUUID().toString();
        String hangout2Id = UUID.randomUUID().toString();
        String hangout3Id = UUID.randomUUID().toString();

        EventSeries series = new EventSeries();
        series.setSeriesId(seriesId);
        series.setHangoutIds(Arrays.asList(hangout1Id, hangout2Id, hangout3Id));
        series.setVersion(1L);

        // Create hangouts with different timestamps
        Hangout hangout1 = HangoutTestBuilder.aHangout()
            .withId(hangout1Id)
            .withStartTimestamp(1000L)
            .withEndTimestamp(2000L)
            .build();
        
        Hangout hangout2 = HangoutTestBuilder.aHangout()
            .withId(hangout2Id)
            .withStartTimestamp(500L)  // Earliest start
            .withEndTimestamp(1500L)
            .build();
        
        Hangout hangout3 = HangoutTestBuilder.aHangout()
            .withId(hangout3Id)
            .withStartTimestamp(1200L)
            .withEndTimestamp(3000L)  // Latest end
            .build();

        when(hangoutRepository.findHangoutById(hangout1Id)).thenReturn(Optional.of(hangout1));
        when(hangoutRepository.findHangoutById(hangout2Id)).thenReturn(Optional.of(hangout2));
        when(hangoutRepository.findHangoutById(hangout3Id)).thenReturn(Optional.of(hangout3));

        // When - use reflection to call private method
        Method updateTimestampsMethod = EventSeriesServiceImpl.class.getDeclaredMethod("updateSeriesTimestamps", EventSeries.class);
        updateTimestampsMethod.setAccessible(true);
        updateTimestampsMethod.invoke(eventSeriesService, series);

        // Then
        assertThat(series.getStartTimestamp()).isEqualTo(500L);  // Earliest
        assertThat(series.getEndTimestamp()).isEqualTo(3000L);   // Latest
        assertThat(series.getVersion()).isEqualTo(2L);           // Incremented
    }

    @Test
    void updateSeriesTimestamps_WithSomeNullTimestamps_HandlesGracefully() throws Exception {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String hangout1Id = UUID.randomUUID().toString();
        String hangout2Id = UUID.randomUUID().toString();
        String hangout3Id = UUID.randomUUID().toString();

        EventSeries series = new EventSeries();
        series.setSeriesId(seriesId);
        series.setHangoutIds(Arrays.asList(hangout1Id, hangout2Id, hangout3Id));
        series.setVersion(1L);

        // Create hangouts with mix of null and valid timestamps
        Hangout hangout1 = HangoutTestBuilder.aHangout()
            .withId(hangout1Id)
            .withStartTimestamp(null)  // Null timestamps
            .withEndTimestamp(null)
            .build();
        
        Hangout hangout2 = HangoutTestBuilder.aHangout()
            .withId(hangout2Id)
            .withStartTimestamp(1000L)
            .withEndTimestamp(2000L)
            .build();
        
        Hangout hangout3 = HangoutTestBuilder.aHangout()
            .withId(hangout3Id)
            .withStartTimestamp(500L)
            .withEndTimestamp(null)  // Null end timestamp
            .build();

        when(hangoutRepository.findHangoutById(hangout1Id)).thenReturn(Optional.of(hangout1));
        when(hangoutRepository.findHangoutById(hangout2Id)).thenReturn(Optional.of(hangout2));
        when(hangoutRepository.findHangoutById(hangout3Id)).thenReturn(Optional.of(hangout3));

        // When
        Method updateTimestampsMethod = EventSeriesServiceImpl.class.getDeclaredMethod("updateSeriesTimestamps", EventSeries.class);
        updateTimestampsMethod.setAccessible(true);
        updateTimestampsMethod.invoke(eventSeriesService, series);

        // Then - only non-null timestamps are considered
        assertThat(series.getStartTimestamp()).isEqualTo(500L);   // Min of valid start timestamps
        assertThat(series.getEndTimestamp()).isEqualTo(2000L);   // Max of valid end timestamps
        assertThat(series.getVersion()).isEqualTo(2L);
    }

    @Test
    void updateSeriesTimestamps_WithAllNullTimestamps_SetsNullTimestamps() throws Exception {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String hangout1Id = UUID.randomUUID().toString();
        String hangout2Id = UUID.randomUUID().toString();

        EventSeries series = new EventSeries();
        series.setSeriesId(seriesId);
        series.setHangoutIds(Arrays.asList(hangout1Id, hangout2Id));
        series.setVersion(1L);

        // Create hangouts with all null timestamps
        Hangout hangout1 = HangoutTestBuilder.aHangout()
            .withId(hangout1Id)
            .withStartTimestamp(null)
            .withEndTimestamp(null)
            .build();
        
        Hangout hangout2 = HangoutTestBuilder.aHangout()
            .withId(hangout2Id)
            .withStartTimestamp(null)
            .withEndTimestamp(null)
            .build();

        when(hangoutRepository.findHangoutById(hangout1Id)).thenReturn(Optional.of(hangout1));
        when(hangoutRepository.findHangoutById(hangout2Id)).thenReturn(Optional.of(hangout2));

        // When
        Method updateTimestampsMethod = EventSeriesServiceImpl.class.getDeclaredMethod("updateSeriesTimestamps", EventSeries.class);
        updateTimestampsMethod.setAccessible(true);
        updateTimestampsMethod.invoke(eventSeriesService, series);

        // Then
        assertThat(series.getStartTimestamp()).isNull();
        assertThat(series.getEndTimestamp()).isNull();
        assertThat(series.getVersion()).isEqualTo(2L);  // Still incremented
    }

    @Test
    void updateSeriesTimestamps_WithNonExistentHangouts_SkipsGracefully() throws Exception {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String existingHangoutId = UUID.randomUUID().toString();
        String nonExistentHangoutId = UUID.randomUUID().toString();

        EventSeries series = new EventSeries();
        series.setSeriesId(seriesId);
        series.setHangoutIds(Arrays.asList(existingHangoutId, nonExistentHangoutId));
        series.setVersion(1L);

        // Only one hangout exists
        Hangout existingHangout = HangoutTestBuilder.aHangout()
            .withId(existingHangoutId)
            .withStartTimestamp(1000L)
            .withEndTimestamp(2000L)
            .build();

        when(hangoutRepository.findHangoutById(existingHangoutId)).thenReturn(Optional.of(existingHangout));
        when(hangoutRepository.findHangoutById(nonExistentHangoutId)).thenReturn(Optional.empty());

        // When
        Method updateTimestampsMethod = EventSeriesServiceImpl.class.getDeclaredMethod("updateSeriesTimestamps", EventSeries.class);
        updateTimestampsMethod.setAccessible(true);
        updateTimestampsMethod.invoke(eventSeriesService, series);

        // Then - method completes without exceptions, only existing hangouts affect calculation
        assertThat(series.getStartTimestamp()).isEqualTo(1000L);
        assertThat(series.getEndTimestamp()).isEqualTo(2000L);
        assertThat(series.getVersion()).isEqualTo(2L);
    }

    // deleteStandaloneHangout() Tests

    @Test
    void deleteStandaloneHangout_WithValidHangout_DeletesAllRecords() throws Exception {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String group1Id = UUID.randomUUID().toString();
        String group2Id = UUID.randomUUID().toString();

        Hangout hangout = HangoutTestBuilder.aHangout()
            .withId(hangoutId)
            .build();

        List<HangoutPointer> pointers = Arrays.asList(
            HangoutPointerTestBuilder.aPointer()
                .forGroup(group1Id)
                .forHangout(hangoutId)
                .build(),
            HangoutPointerTestBuilder.aPointer()
                .forGroup(group2Id)
                .forHangout(hangoutId)
                .build()
        );

        when(hangoutRepository.findPointersForHangout(hangout)).thenReturn(pointers);
        doNothing().when(groupRepository).deleteHangoutPointer(anyString(), anyString());
        doNothing().when(hangoutRepository).deleteHangout(hangoutId);

        // When - use reflection to call private method
        Method deleteStandaloneMethod = EventSeriesServiceImpl.class.getDeclaredMethod("deleteStandaloneHangout", Hangout.class);
        deleteStandaloneMethod.setAccessible(true);
        deleteStandaloneMethod.invoke(eventSeriesService, hangout);

        // Then
        // Verify all pointers are deleted
        verify(groupRepository).deleteHangoutPointer(group1Id, hangoutId);
        verify(groupRepository).deleteHangoutPointer(group2Id, hangoutId);
        
        // Verify hangout is deleted after pointers
        verify(hangoutRepository).deleteHangout(hangoutId);
    }

    @Test
    void deleteStandaloneHangout_WithPointerDeletionFailure_ThrowsRepositoryException() throws Exception {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String groupId = UUID.randomUUID().toString();

        Hangout hangout = HangoutTestBuilder.aHangout()
            .withId(hangoutId)
            .build();

        List<HangoutPointer> pointers = Arrays.asList(
            HangoutPointerTestBuilder.aPointer()
                .forGroup(groupId)
                .forHangout(hangoutId)
                .build()
        );

        when(hangoutRepository.findPointersForHangout(hangout)).thenReturn(pointers);
        
        // Mock pointer deletion to throw exception
        RuntimeException originalException = new RuntimeException("Pointer deletion failed");
        doThrow(originalException).when(groupRepository).deleteHangoutPointer(groupId, hangoutId);

        // When & Then
        Method deleteStandaloneMethod = EventSeriesServiceImpl.class.getDeclaredMethod("deleteStandaloneHangout", Hangout.class);
        deleteStandaloneMethod.setAccessible(true);
        
        assertThatThrownBy(() -> deleteStandaloneMethod.invoke(eventSeriesService, hangout))
            .hasCauseInstanceOf(RepositoryException.class)
            .hasRootCauseInstanceOf(RuntimeException.class);

        // Verify hangout deletion was not attempted
        verify(hangoutRepository, never()).deleteHangout(anyString());
    }

    @Test
    void deleteStandaloneHangout_WithNoPointers_DeletesHangoutOnly() throws Exception {
        // Given
        String hangoutId = UUID.randomUUID().toString();

        Hangout hangout = HangoutTestBuilder.aHangout()
            .withId(hangoutId)
            .build();

        // Empty pointer list
        when(hangoutRepository.findPointersForHangout(hangout)).thenReturn(Collections.emptyList());
        doNothing().when(hangoutRepository).deleteHangout(hangoutId);

        // When
        Method deleteStandaloneMethod = EventSeriesServiceImpl.class.getDeclaredMethod("deleteStandaloneHangout", Hangout.class);
        deleteStandaloneMethod.setAccessible(true);
        deleteStandaloneMethod.invoke(eventSeriesService, hangout);

        // Then
        // Verify no pointer deletion attempts made
        verify(groupRepository, never()).deleteHangoutPointer(anyString(), anyString());
        
        // Verify hangout deletion proceeds normally
        verify(hangoutRepository).deleteHangout(hangoutId);
    }

    // ============================================================================
    // GET SERIES DETAIL TESTS - Test Plan 4: Detailed Read View
    // ============================================================================

    // Success Path Tests

    @Test
    void getSeriesDetail_WithValidInputs_ReturnsCompleteSeriesDetails() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String hangout1Id = UUID.randomUUID().toString();
        String hangout2Id = UUID.randomUUID().toString();
        
        User user = new User();
        user.setId(UUID.fromString(userId));
        
        EventSeries series = new EventSeries();
        series.setSeriesId(seriesId);
        series.setSeriesTitle("Test Series");
        series.setSeriesDescription("Test Description");
        series.setPrimaryEventId(hangout1Id);
        series.setGroupId(UUID.randomUUID().toString());
        series.setStartTimestamp(1000L);
        series.setEndTimestamp(2000L);
        series.setVersion(1L);
        series.setHangoutIds(Arrays.asList(hangout1Id, hangout2Id));
        
        Hangout hangout1 = HangoutTestBuilder.aHangout()
            .withId(hangout1Id)
            .withTitle("First Hangout")
            .withStartTimestamp(1000L)
            .withSeriesId(seriesId)
            .build();
            
        Hangout hangout2 = HangoutTestBuilder.aHangout()
            .withId(hangout2Id)
            .withTitle("Second Hangout")
            .withStartTimestamp(1500L)
            .withSeriesId(seriesId)
            .build();
        
        List<Hangout> hangouts = Arrays.asList(hangout1, hangout2);
        
        HangoutDetailDTO hangoutDetail1 = HangoutDetailDTO.builder()
                .withHangout(hangout1)
                .build();

        HangoutDetailDTO hangoutDetail2 = HangoutDetailDTO.builder()
                .withHangout(hangout2)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));
        when(hangoutService.getHangoutDetail(hangout1Id, userId)).thenReturn(hangoutDetail1);
        when(hangoutService.getHangoutDetail(hangout2Id, userId)).thenReturn(hangoutDetail2);

        // When
        EventSeriesDetailDTO result = eventSeriesService.getSeriesDetail(seriesId, userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSeriesId()).isEqualTo(seriesId);
        assertThat(result.getSeriesTitle()).isEqualTo("Test Series");
        assertThat(result.getSeriesDescription()).isEqualTo("Test Description");
        assertThat(result.getPrimaryEventId()).isEqualTo(hangout1Id);
        assertThat(result.getGroupId()).isEqualTo(series.getGroupId());
        assertThat(result.getStartTimestamp()).isEqualTo(1000L);
        assertThat(result.getEndTimestamp()).isEqualTo(2000L);
        assertThat(result.getVersion()).isEqualTo(1L);
        
        assertThat(result.getHangouts()).hasSize(2);
        assertThat(result.getHangouts().get(0).getHangout().getHangoutId()).isEqualTo(hangout1Id);
        assertThat(result.getHangouts().get(1).getHangout().getHangoutId()).isEqualTo(hangout2Id);
        
        // Verify all dependencies were called correctly
        verify(userRepository).findById(userId);
        verify(eventSeriesRepository).findById(seriesId);
        verify(hangoutService).getHangoutDetail(hangout1Id, userId);
        verify(hangoutService).getHangoutDetail(hangout2Id, userId);
    }

    @Test
    void getSeriesDetail_WithMixedTimestamps_SortsCorrectly() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String hangout1Id = UUID.randomUUID().toString();
        String hangout2Id = UUID.randomUUID().toString();
        String hangout3Id = UUID.randomUUID().toString();
        String hangout4Id = UUID.randomUUID().toString();
        
        User user = new User();
        user.setId(UUID.fromString(userId));
        
        EventSeries series = new EventSeries();
        series.setSeriesId(seriesId);
        series.setSeriesTitle("Test Series");
        series.setHangoutIds(Arrays.asList(hangout1Id, hangout2Id, hangout3Id, hangout4Id));
        
        // Create hangouts with mixed timestamps: null, early, late, and null
        Hangout hangout1 = HangoutTestBuilder.aHangout()
            .withId(hangout1Id)
            .withTitle("Null Timestamp 1")
            .withStartTimestamp(null)
            .withSeriesId(seriesId)
            .build();
            
        Hangout hangout2 = HangoutTestBuilder.aHangout()
            .withId(hangout2Id)
            .withTitle("Early Hangout")
            .withStartTimestamp(1000L)
            .withSeriesId(seriesId)
            .build();
            
        Hangout hangout3 = HangoutTestBuilder.aHangout()
            .withId(hangout3Id)
            .withTitle("Late Hangout")
            .withStartTimestamp(2000L)
            .withSeriesId(seriesId)
            .build();
            
        Hangout hangout4 = HangoutTestBuilder.aHangout()
            .withId(hangout4Id)
            .withTitle("Null Timestamp 2")
            .withStartTimestamp(null)
            .withSeriesId(seriesId)
            .build();
        
        List<Hangout> hangouts = Arrays.asList(hangout1, hangout2, hangout3, hangout4);
        
        HangoutDetailDTO hangoutDetail1 = HangoutDetailDTO.builder()
                .withHangout(hangout1)
                .build();

        HangoutDetailDTO hangoutDetail2 = HangoutDetailDTO.builder()
                .withHangout(hangout2)
                .build();

        HangoutDetailDTO hangoutDetail3 = HangoutDetailDTO.builder()
                .withHangout(hangout3)
                .build();

        HangoutDetailDTO hangoutDetail4 = HangoutDetailDTO.builder()
                .withHangout(hangout4)
                .build();
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));
        when(hangoutService.getHangoutDetail(hangout1Id, userId)).thenReturn(hangoutDetail1);
        when(hangoutService.getHangoutDetail(hangout2Id, userId)).thenReturn(hangoutDetail2);
        when(hangoutService.getHangoutDetail(hangout3Id, userId)).thenReturn(hangoutDetail3);
        when(hangoutService.getHangoutDetail(hangout4Id, userId)).thenReturn(hangoutDetail4);
        
        // When
        EventSeriesDetailDTO result = eventSeriesService.getSeriesDetail(seriesId, userId);
        
        // Then
        assertThat(result.getHangouts()).hasSize(4);
        
        // Verify chronological sorting: timestamped hangouts first (earliest to latest), then null timestamps
        assertThat(result.getHangouts().get(0).getHangout().getHangoutId()).isEqualTo(hangout2Id); // 1000L
        assertThat(result.getHangouts().get(1).getHangout().getHangoutId()).isEqualTo(hangout3Id); // 2000L
        // Null timestamps come last (order preserved for same timestamps)
        assertThat(result.getHangouts().get(2).getHangout().getStartTimestamp()).isNull();
        assertThat(result.getHangouts().get(3).getHangout().getStartTimestamp()).isNull();
    }

    // Authorization and Validation Tests

    @Test
    void getSeriesDetail_WithNonexistentUser_ThrowsUnauthorizedException() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        
        // When & Then
        assertThatThrownBy(() -> eventSeriesService.getSeriesDetail(seriesId, userId))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("User " + userId + " not found");
        
        // Verify no further repository calls were made
        verify(userRepository).findById(userId);
        verify(eventSeriesRepository, never()).findById(any());
        verify(hangoutRepository, never()).findHangoutsBySeriesId(any());
        verify(hangoutService, never()).getHangoutDetail(any(), any());
    }

    @Test
    void getSeriesDetail_WithNonexistentSeries_ThrowsResourceNotFoundException() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        
        User user = new User();
        user.setId(UUID.fromString(userId));
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.empty());
        
        // When & Then
        assertThatThrownBy(() -> eventSeriesService.getSeriesDetail(seriesId, userId))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("EventSeries not found: " + seriesId);
        
        // Verify user validation occurred first, but no hangout data fetching
        verify(userRepository).findById(userId);
        verify(eventSeriesRepository).findById(seriesId);
        verify(hangoutRepository, never()).findHangoutsBySeriesId(any());
        verify(hangoutService, never()).getHangoutDetail(any(), any());
    }

    // Data Integrity Tests

    @Test
    void getSeriesDetail_WithEmptySeries_ReturnsSeriesWithNoHangouts() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        
        User user = new User();
        user.setId(UUID.fromString(userId));
        
        EventSeries series = new EventSeries();
        series.setSeriesId(seriesId);
        series.setSeriesTitle("Empty Series");
        series.setSeriesDescription("Series with no hangouts");
        series.setHangoutIds(Collections.emptyList());
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));
        
        // When
        EventSeriesDetailDTO result = eventSeriesService.getSeriesDetail(seriesId, userId);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSeriesId()).isEqualTo(seriesId);
        assertThat(result.getSeriesTitle()).isEqualTo("Empty Series");
        assertThat(result.getSeriesDescription()).isEqualTo("Series with no hangouts");
        assertThat(result.getHangouts()).isNotNull();
        assertThat(result.getHangouts()).isEmpty();
        
        // Verify no hangout service calls were made
        verify(hangoutService, never()).getHangoutDetail(any(), any());
    }

    @Test
    void getSeriesDetail_WithSomeHangoutDetailFailures_ContinuesProcessing() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String hangout1Id = UUID.randomUUID().toString();
        String hangout2Id = UUID.randomUUID().toString();
        String hangout3Id = UUID.randomUUID().toString();
        
        User user = new User();
        user.setId(UUID.fromString(userId));
        
        EventSeries series = new EventSeries();
        series.setSeriesId(seriesId);
        series.setSeriesTitle("Partial Failure Series");
        series.setHangoutIds(Arrays.asList(hangout1Id, hangout2Id, hangout3Id));
        
        Hangout hangout1 = HangoutTestBuilder.aHangout()
            .withId(hangout1Id)
            .withTitle("Success Hangout 1")
            .withStartTimestamp(1000L)
            .withSeriesId(seriesId)
            .build();
            
        Hangout hangout2 = HangoutTestBuilder.aHangout()
            .withId(hangout2Id)
            .withTitle("Failure Hangout")
            .withStartTimestamp(1500L)
            .withSeriesId(seriesId)
            .build();
            
        Hangout hangout3 = HangoutTestBuilder.aHangout()
            .withId(hangout3Id)
            .withTitle("Success Hangout 2")
            .withStartTimestamp(2000L)
            .withSeriesId(seriesId)
            .build();
        
        List<Hangout> hangouts = Arrays.asList(hangout1, hangout2, hangout3);
        
        HangoutDetailDTO hangoutDetail1 = HangoutDetailDTO.builder()
                .withHangout(hangout1)
                .build();

        HangoutDetailDTO hangoutDetail3 = HangoutDetailDTO.builder()
                .withHangout(hangout3)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));
        when(hangoutService.getHangoutDetail(hangout1Id, userId)).thenReturn(hangoutDetail1);
        when(hangoutService.getHangoutDetail(hangout2Id, userId))
            .thenThrow(new RuntimeException("Failed to get hangout details"));
        when(hangoutService.getHangoutDetail(hangout3Id, userId)).thenReturn(hangoutDetail3);
        
        // When
        EventSeriesDetailDTO result = eventSeriesService.getSeriesDetail(seriesId, userId);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSeriesId()).isEqualTo(seriesId);
        assertThat(result.getSeriesTitle()).isEqualTo("Partial Failure Series");
        
        // Only successful hangouts are included
        assertThat(result.getHangouts()).hasSize(2);
        assertThat(result.getHangouts().get(0).getHangout().getHangoutId()).isEqualTo(hangout1Id);
        assertThat(result.getHangouts().get(1).getHangout().getHangoutId()).isEqualTo(hangout3Id);
        
        // Verify all hangout service calls were attempted
        verify(hangoutService).getHangoutDetail(hangout1Id, userId);
        verify(hangoutService).getHangoutDetail(hangout2Id, userId);
        verify(hangoutService).getHangoutDetail(hangout3Id, userId);
    }

    // Integration and Dependency Tests

    @Test
    void getSeriesDetail_CallsRepositoriesInCorrectOrder_VerifyInteractionSequence() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String hangoutId = UUID.randomUUID().toString();
        
        User user = new User();
        user.setId(UUID.fromString(userId));
        
        EventSeries series = new EventSeries();
        series.setSeriesId(seriesId);
        series.setSeriesTitle("Order Test Series");
        series.setHangoutIds(Arrays.asList(hangoutId));
        
        Hangout hangout = HangoutTestBuilder.aHangout()
            .withId(hangoutId)
            .withTitle("Test Hangout")
            .withSeriesId(seriesId)
            .build();
        
        HangoutDetailDTO hangoutDetail = HangoutDetailDTO.builder()
                .withHangout(hangout)
                .build();
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));
        when(hangoutService.getHangoutDetail(hangoutId, userId)).thenReturn(hangoutDetail);
        
        // When
        eventSeriesService.getSeriesDetail(seriesId, userId);
        
        // Then - verify order of calls using InOrder (no longer calls hangoutRepository.findHangoutsBySeriesId)
        var inOrder = inOrder(userRepository, eventSeriesRepository, hangoutService);
        inOrder.verify(userRepository).findById(userId);
        inOrder.verify(eventSeriesRepository).findById(seriesId);
        inOrder.verify(hangoutService).getHangoutDetail(hangoutId, userId);
    }

    @Test
    void getSeriesDetail_PassesCorrectParametersToHangoutService_VerifyUserIdPropagation() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String hangout1Id = UUID.randomUUID().toString();
        String hangout2Id = UUID.randomUUID().toString();
        
        User user = new User();
        user.setId(UUID.fromString(userId));
        
        EventSeries series = new EventSeries();
        series.setSeriesId(seriesId);
        series.setSeriesTitle("Parameter Test Series");
        series.setHangoutIds(Arrays.asList(hangout1Id, hangout2Id));
        
        Hangout hangout1 = HangoutTestBuilder.aHangout()
            .withId(hangout1Id)
            .withTitle("Hangout 1")
            .withSeriesId(seriesId)
            .build();
            
        Hangout hangout2 = HangoutTestBuilder.aHangout()
            .withId(hangout2Id)
            .withTitle("Hangout 2")
            .withSeriesId(seriesId)
            .build();
        
        List<Hangout> hangouts = Arrays.asList(hangout1, hangout2);
        
        HangoutDetailDTO hangoutDetail1 = HangoutDetailDTO.builder()
                .withHangout(hangout1)
                .build();

        HangoutDetailDTO hangoutDetail2 = HangoutDetailDTO.builder()
                .withHangout(hangout2)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));
        when(hangoutService.getHangoutDetail(hangout1Id, userId)).thenReturn(hangoutDetail1);
        when(hangoutService.getHangoutDetail(hangout2Id, userId)).thenReturn(hangoutDetail2);
        
        // When
        eventSeriesService.getSeriesDetail(seriesId, userId);
        
        // Then - verify each hangout service call receives the original userId
        verify(hangoutService).getHangoutDetail(hangout1Id, userId);
        verify(hangoutService).getHangoutDetail(hangout2Id, userId);
        
        // Verify no other user IDs were passed
        verify(hangoutService, times(2)).getHangoutDetail(anyString(), eq(userId));
    }

    // ============================================================================
    // DELETE ENTIRE SERIES TESTS - Test Plan 1
    // ============================================================================

    @Test
    void deleteEntireSeries_WithValidData_DeletesSeriesWithAllHangouts() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String hangout1Id = UUID.randomUUID().toString();
        String hangout2Id = UUID.randomUUID().toString();
        String hangout3Id = UUID.randomUUID().toString();
        String groupId = UUID.randomUUID().toString();
        
        User user = new User();
        user.setId(UUID.fromString(userId));
        
        EventSeries series = new EventSeries();
        series.setSeriesId(seriesId);
        series.setGroupId(groupId);
        series.setHangoutIds(Arrays.asList(hangout1Id, hangout2Id, hangout3Id));
        
        Hangout hangout1 = HangoutTestBuilder.aHangout()
            .withId(hangout1Id)
            .withSeriesId(seriesId)
            .build();
            
        Hangout hangout2 = HangoutTestBuilder.aHangout()
            .withId(hangout2Id)
            .withSeriesId(seriesId)
            .build();
            
        Hangout hangout3 = HangoutTestBuilder.aHangout()
            .withId(hangout3Id)
            .withSeriesId(seriesId)
            .build();
        
        List<HangoutPointer> hangout1Pointers = Arrays.asList(
            HangoutPointerTestBuilder.aPointer()
                .forGroup(groupId)
                .forHangout(hangout1Id)
                .withSeriesId(seriesId)
                .build(),
            HangoutPointerTestBuilder.aPointer()
                .forGroup(UUID.randomUUID().toString())
                .forHangout(hangout1Id)
                .withSeriesId(seriesId)
                .build()
        );
        
        List<HangoutPointer> hangout2Pointers = Arrays.asList(
            HangoutPointerTestBuilder.aPointer()
                .forGroup(groupId)
                .forHangout(hangout2Id)
                .withSeriesId(seriesId)
                .build(),
            HangoutPointerTestBuilder.aPointer()
                .forGroup(UUID.randomUUID().toString())
                .forHangout(hangout2Id)
                .withSeriesId(seriesId)
                .build()
        );
        
        List<HangoutPointer> hangout3Pointers = Arrays.asList(
            HangoutPointerTestBuilder.aPointer()
                .forGroup(groupId)
                .forHangout(hangout3Id)
                .withSeriesId(seriesId)
                .build()
        );
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));
        when(hangoutRepository.findHangoutById(hangout1Id)).thenReturn(Optional.of(hangout1));
        when(hangoutRepository.findHangoutById(hangout2Id)).thenReturn(Optional.of(hangout2));
        when(hangoutRepository.findHangoutById(hangout3Id)).thenReturn(Optional.of(hangout3));
        when(hangoutRepository.findPointersForHangout(hangout1)).thenReturn(hangout1Pointers);
        when(hangoutRepository.findPointersForHangout(hangout2)).thenReturn(hangout2Pointers);
        when(hangoutRepository.findPointersForHangout(hangout3)).thenReturn(hangout3Pointers);
        
        // When
        eventSeriesService.deleteEntireSeries(seriesId, userId);
        
        // Then
        // Verify transaction repository is called with correct parameters
        ArgumentCaptor<List<Hangout>> hangoutsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<HangoutPointer>> pointersCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<SeriesPointer>> seriesPointersCaptor = ArgumentCaptor.forClass(List.class);
        
        verify(seriesTransactionRepository).deleteEntireSeriesWithAllHangouts(
            eq(series),
            hangoutsCaptor.capture(),
            pointersCaptor.capture(),
            seriesPointersCaptor.capture()
        );
        
        // Verify all hangouts were collected
        List<Hangout> capturedHangouts = hangoutsCaptor.getValue();
        assertThat(capturedHangouts).hasSize(3);
        assertThat(capturedHangouts).extracting(Hangout::getHangoutId)
            .containsExactlyInAnyOrder(hangout1Id, hangout2Id, hangout3Id);
        
        // Verify all pointers were collected
        List<HangoutPointer> capturedPointers = pointersCaptor.getValue();
        assertThat(capturedPointers).hasSize(5); // 2 + 2 + 1 pointers
        
        // Verify series pointer was created
        List<SeriesPointer> capturedSeriesPointers = seriesPointersCaptor.getValue();
        assertThat(capturedSeriesPointers).hasSize(1);
        assertThat(capturedSeriesPointers.get(0).getSeriesId()).isEqualTo(seriesId);
        assertThat(capturedSeriesPointers.get(0).getGroupId()).isEqualTo(groupId);
        
        // Verify repository method calls
        verify(userRepository).findById(userId);
        verify(eventSeriesRepository).findById(seriesId);
        verify(hangoutRepository).findHangoutById(hangout1Id);
        verify(hangoutRepository).findHangoutById(hangout2Id);
        verify(hangoutRepository).findHangoutById(hangout3Id);
        verify(hangoutRepository).findPointersForHangout(hangout1);
        verify(hangoutRepository).findPointersForHangout(hangout2);
        verify(hangoutRepository).findPointersForHangout(hangout3);
    }

    @Test
    void deleteEntireSeries_WithUserNotFound_ThrowsUnauthorizedException() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        
        // When & Then
        assertThatThrownBy(() -> eventSeriesService.deleteEntireSeries(seriesId, userId))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("User " + userId + " not found");
        
        // Verify no further repository calls were made
        verify(userRepository).findById(userId);
        verify(eventSeriesRepository, never()).findById(any());
        verify(hangoutRepository, never()).findHangoutById(any());
        verify(seriesTransactionRepository, never()).deleteEntireSeriesWithAllHangouts(any(), any(), any(), any());
    }

    @Test
    void deleteEntireSeries_WithSeriesNotFound_ThrowsResourceNotFoundException() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        
        User user = new User();
        user.setId(UUID.fromString(userId));
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.empty());
        
        // When & Then
        assertThatThrownBy(() -> eventSeriesService.deleteEntireSeries(seriesId, userId))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("EventSeries not found: " + seriesId);
        
        // Verify authorization occurred first, but no hangout operations
        verify(userRepository).findById(userId);
        verify(eventSeriesRepository).findById(seriesId);
        verify(hangoutRepository, never()).findHangoutById(any());
        verify(seriesTransactionRepository, never()).deleteEntireSeriesWithAllHangouts(any(), any(), any(), any());
    }

    @Test
    void deleteEntireSeries_WithEmptySeries_DeletesSeriesOnly() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String groupId = UUID.randomUUID().toString();
        
        User user = new User();
        user.setId(UUID.fromString(userId));
        
        EventSeries series = new EventSeries();
        series.setSeriesId(seriesId);
        series.setGroupId(groupId);
        series.setHangoutIds(Collections.emptyList());
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));
        
        // When
        eventSeriesService.deleteEntireSeries(seriesId, userId);
        
        // Then
        ArgumentCaptor<List<Hangout>> hangoutsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<HangoutPointer>> pointersCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<SeriesPointer>> seriesPointersCaptor = ArgumentCaptor.forClass(List.class);
        
        verify(seriesTransactionRepository).deleteEntireSeriesWithAllHangouts(
            eq(series),
            hangoutsCaptor.capture(),
            pointersCaptor.capture(),
            seriesPointersCaptor.capture()
        );
        
        // Verify empty lists passed for hangouts and pointers
        assertThat(hangoutsCaptor.getValue()).isEmpty();
        assertThat(pointersCaptor.getValue()).isEmpty();
        
        // Verify series pointer still created
        List<SeriesPointer> capturedSeriesPointers = seriesPointersCaptor.getValue();
        assertThat(capturedSeriesPointers).hasSize(1);
        assertThat(capturedSeriesPointers.get(0).getSeriesId()).isEqualTo(seriesId);
        assertThat(capturedSeriesPointers.get(0).getGroupId()).isEqualTo(groupId);
        
        // Verify no hangout lookups were attempted
        verify(hangoutRepository, never()).findHangoutById(any());
        verify(hangoutRepository, never()).findPointersForHangout(any());
    }

    @Test
    void deleteEntireSeries_WithMissingHangouts_IgnoresMissingAndDeletesFound() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String hangout1Id = UUID.randomUUID().toString();
        String hangout2Id = UUID.randomUUID().toString();
        String hangout3Id = UUID.randomUUID().toString();
        String groupId = UUID.randomUUID().toString();
        
        User user = new User();
        user.setId(UUID.fromString(userId));
        
        EventSeries series = new EventSeries();
        series.setSeriesId(seriesId);
        series.setGroupId(groupId);
        series.setHangoutIds(Arrays.asList(hangout1Id, hangout2Id, hangout3Id));
        
        // Only hangout1 and hangout3 exist, hangout2 is missing
        Hangout hangout1 = HangoutTestBuilder.aHangout()
            .withId(hangout1Id)
            .withSeriesId(seriesId)
            .build();
            
        Hangout hangout3 = HangoutTestBuilder.aHangout()
            .withId(hangout3Id)
            .withSeriesId(seriesId)
            .build();
        
        List<HangoutPointer> hangout1Pointers = Arrays.asList(
            HangoutPointerTestBuilder.aPointer()
                .forGroup(groupId)
                .forHangout(hangout1Id)
                .withSeriesId(seriesId)
                .build()
        );
        
        List<HangoutPointer> hangout3Pointers = Arrays.asList(
            HangoutPointerTestBuilder.aPointer()
                .forGroup(groupId)
                .forHangout(hangout3Id)
                .withSeriesId(seriesId)
                .build()
        );
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));
        when(hangoutRepository.findHangoutById(hangout1Id)).thenReturn(Optional.of(hangout1));
        when(hangoutRepository.findHangoutById(hangout2Id)).thenReturn(Optional.empty()); // Missing hangout
        when(hangoutRepository.findHangoutById(hangout3Id)).thenReturn(Optional.of(hangout3));
        when(hangoutRepository.findPointersForHangout(hangout1)).thenReturn(hangout1Pointers);
        when(hangoutRepository.findPointersForHangout(hangout3)).thenReturn(hangout3Pointers);
        
        // When
        eventSeriesService.deleteEntireSeries(seriesId, userId);
        
        // Then
        ArgumentCaptor<List<Hangout>> hangoutsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<HangoutPointer>> pointersCaptor = ArgumentCaptor.forClass(List.class);
        
        verify(seriesTransactionRepository).deleteEntireSeriesWithAllHangouts(
            eq(series),
            hangoutsCaptor.capture(),
            pointersCaptor.capture(),
            any(List.class)
        );
        
        // Verify only found hangouts were collected
        List<Hangout> capturedHangouts = hangoutsCaptor.getValue();
        assertThat(capturedHangouts).hasSize(2);
        assertThat(capturedHangouts).extracting(Hangout::getHangoutId)
            .containsExactlyInAnyOrder(hangout1Id, hangout3Id);
        
        // Verify only found pointers were collected
        List<HangoutPointer> capturedPointers = pointersCaptor.getValue();
        assertThat(capturedPointers).hasSize(2);
        
        // Verify all hangout lookups were attempted, but missing ones were handled gracefully
        verify(hangoutRepository).findHangoutById(hangout1Id);
        verify(hangoutRepository).findHangoutById(hangout2Id);
        verify(hangoutRepository).findHangoutById(hangout3Id);
        verify(hangoutRepository).findPointersForHangout(hangout1);
        verify(hangoutRepository, never()).findPointersForHangout(argThat(h -> hangout2Id.equals(h.getHangoutId())));
        verify(hangoutRepository).findPointersForHangout(hangout3);
    }

    @Test
    void deleteEntireSeries_WithSeriesWithoutGroup_CreatesEmptySeriesPointersList() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String hangoutId = UUID.randomUUID().toString();
        
        User user = new User();
        user.setId(UUID.fromString(userId));
        
        EventSeries series = new EventSeries();
        series.setSeriesId(seriesId);
        series.setGroupId(null); // No group
        series.setHangoutIds(Arrays.asList(hangoutId));
        
        Hangout hangout = HangoutTestBuilder.aHangout()
            .withId(hangoutId)
            .withSeriesId(seriesId)
            .build();
        
        List<HangoutPointer> hangoutPointers = Arrays.asList(
            HangoutPointerTestBuilder.aPointer()
                .forGroup(UUID.randomUUID().toString())
                .forHangout(hangoutId)
                .withSeriesId(seriesId)
                .build()
        );
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));
        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        when(hangoutRepository.findPointersForHangout(hangout)).thenReturn(hangoutPointers);
        
        // When
        eventSeriesService.deleteEntireSeries(seriesId, userId);
        
        // Then
        ArgumentCaptor<List<SeriesPointer>> seriesPointersCaptor = ArgumentCaptor.forClass(List.class);
        
        verify(seriesTransactionRepository).deleteEntireSeriesWithAllHangouts(
            eq(series),
            any(List.class),
            any(List.class),
            seriesPointersCaptor.capture()
        );
        
        // Verify empty series pointers list when no groupId
        List<SeriesPointer> capturedSeriesPointers = seriesPointersCaptor.getValue();
        assertThat(capturedSeriesPointers).isEmpty();
        
        // Verify other operations still proceed normally
        verify(hangoutRepository).findHangoutById(hangoutId);
        verify(hangoutRepository).findPointersForHangout(hangout);
    }

    @Test
    void deleteEntireSeries_WithTransactionFailure_ThrowsRepositoryException() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String hangoutId = UUID.randomUUID().toString();
        String groupId = UUID.randomUUID().toString();
        
        User user = new User();
        user.setId(UUID.fromString(userId));
        
        EventSeries series = new EventSeries();
        series.setSeriesId(seriesId);
        series.setGroupId(groupId);
        series.setHangoutIds(Arrays.asList(hangoutId));
        
        Hangout hangout = HangoutTestBuilder.aHangout()
            .withId(hangoutId)
            .withSeriesId(seriesId)
            .build();
        
        List<HangoutPointer> hangoutPointers = Arrays.asList(
            HangoutPointerTestBuilder.aPointer()
                .forGroup(groupId)
                .forHangout(hangoutId)
                .withSeriesId(seriesId)
                .build()
        );
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));
        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        when(hangoutRepository.findPointersForHangout(hangout)).thenReturn(hangoutPointers);
        
        RuntimeException originalException = new RuntimeException("Transaction failed");
        doThrow(originalException).when(seriesTransactionRepository)
            .deleteEntireSeriesWithAllHangouts(any(), any(), any(), any());
        
        // When & Then
        assertThatThrownBy(() -> eventSeriesService.deleteEntireSeries(seriesId, userId))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("Failed to delete entire series atomically")
            .hasCause(originalException);
        
        // Verify data collection occurred before transaction failure
        verify(userRepository).findById(userId);
        verify(eventSeriesRepository).findById(seriesId);
        verify(hangoutRepository).findHangoutById(hangoutId);
        verify(hangoutRepository).findPointersForHangout(hangout);
        verify(seriesTransactionRepository).deleteEntireSeriesWithAllHangouts(any(), any(), any(), any());
    }

    @Test
    void convertToSeriesWithNewMember_CopiesMainImagePathFromExistingHangout() {
        // Given
        String existingHangoutId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String groupId = UUID.randomUUID().toString();

        Hangout existingHangout = HangoutTestBuilder.aHangout()
            .withId(existingHangoutId)
            .withTitle("Series Event")
            .withMainImagePath("/series-image.jpg")
            .build();

        HangoutPointer existingPointer = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId)
            .forHangout(existingHangoutId)
            .build();

        CreateHangoutRequest newMemberRequest = new CreateHangoutRequest();
        newMemberRequest.setTitle("New Member Hangout");
        newMemberRequest.setAssociatedGroups(List.of(groupId));

        Hangout newHangout = HangoutTestBuilder.aHangout()
            .withTitle("New Member Hangout")
            .build();
        newHangout.setAssociatedGroups(List.of(groupId));

        when(hangoutRepository.findHangoutById(existingHangoutId)).thenReturn(Optional.of(existingHangout));
        when(userRepository.findById(userId)).thenReturn(Optional.of(new User()));
        when(hangoutRepository.findPointersForHangout(existingHangout)).thenReturn(List.of(existingPointer));
        when(hangoutService.hangoutFromHangoutRequest(any(), eq(userId))).thenReturn(newHangout);
        doNothing().when(seriesTransactionRepository).createSeriesWithNewPart(any(), any(), any(), any(), any(), any());

        // When
        EventSeries result = eventSeriesService.convertToSeriesWithNewMember(existingHangoutId, newMemberRequest, userId);

        // Then
        assertThat(result.getMainImagePath()).isEqualTo("/series-image.jpg");

        // Verify seriesTransactionRepository was called
        ArgumentCaptor<EventSeries> seriesCaptor = ArgumentCaptor.forClass(EventSeries.class);
        verify(seriesTransactionRepository).createSeriesWithNewPart(
            seriesCaptor.capture(),
            any(), any(), any(), any(), any()
        );

        assertThat(seriesCaptor.getValue().getMainImagePath()).isEqualTo("/series-image.jpg");
    }

    @Test
    void convertToSeriesWithNewMember_DenormalizesImagePathToNewHangoutPointers() {
        // Given
        String existingHangoutId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String group1Id = UUID.randomUUID().toString();
        String group2Id = UUID.randomUUID().toString();

        Hangout existingHangout = HangoutTestBuilder.aHangout()
            .withId(existingHangoutId)
            .withTitle("Series Event")
            .build();

        HangoutPointer existingPointer = HangoutPointerTestBuilder.aPointer()
            .forGroup(group1Id)
            .forHangout(existingHangoutId)
            .build();

        CreateHangoutRequest newMemberRequest = new CreateHangoutRequest();
        newMemberRequest.setTitle("New Member Hangout");
        newMemberRequest.setMainImagePath("/new-part-image.jpg");
        newMemberRequest.setAssociatedGroups(List.of(group1Id, group2Id));

        Hangout newHangout = HangoutTestBuilder.aHangout()
            .withTitle("New Member Hangout")
            .withMainImagePath("/new-part-image.jpg")
            .build();
        newHangout.setAssociatedGroups(List.of(group1Id, group2Id));

        when(hangoutRepository.findHangoutById(existingHangoutId)).thenReturn(Optional.of(existingHangout));
        when(userRepository.findById(userId)).thenReturn(Optional.of(new User()));
        when(hangoutRepository.findPointersForHangout(existingHangout)).thenReturn(List.of(existingPointer));
        when(hangoutService.hangoutFromHangoutRequest(any(), eq(userId))).thenReturn(newHangout);
        doNothing().when(seriesTransactionRepository).createSeriesWithNewPart(any(), any(), any(), any(), any(), any());

        // When
        eventSeriesService.convertToSeriesWithNewMember(existingHangoutId, newMemberRequest, userId);

        // Then - Capture new HangoutPointers passed to repository
        ArgumentCaptor<List<HangoutPointer>> newPointersCaptor = ArgumentCaptor.forClass(List.class);
        verify(seriesTransactionRepository).createSeriesWithNewPart(
            any(),
            any(),
            any(),
            any(),
            newPointersCaptor.capture(),
            any()
        );

        List<HangoutPointer> newPointers = newPointersCaptor.getValue();
        assertThat(newPointers).hasSize(2); // One for each group
        assertThat(newPointers.get(0).getMainImagePath()).isEqualTo("/new-part-image.jpg");
        assertThat(newPointers.get(1).getMainImagePath()).isEqualTo("/new-part-image.jpg");
    }

    // Tests for updateSeries() method

    @Test
    void updateSeries_WithTitleUpdate_UpdatesSeriesTitle() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String groupId = UUID.randomUUID().toString();

        EventSeries series = new EventSeries();
        series.setSeriesId(seriesId);
        series.setSeriesTitle("Old Title");
        series.setVersion(1L);
        series.setGroupId(groupId);
        series.setHangoutIds(Arrays.asList("hangout1", "hangout2"));

        User user = new User();
        user.setId(UUID.fromString(userId));

        UpdateSeriesRequest request = new UpdateSeriesRequest();
        request.setVersion(1L);
        request.setSeriesTitle("New Title");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));
        when(hangoutRepository.findHangoutById("hangout1")).thenReturn(Optional.of(new Hangout()));
        when(hangoutRepository.findHangoutById("hangout2")).thenReturn(Optional.of(new Hangout()));
        when(hangoutRepository.findPointersForHangout(any())).thenReturn(Collections.emptyList());

        // When
        EventSeries result = eventSeriesService.updateSeries(seriesId, request, userId);

        // Then
        assertThat(result.getSeriesTitle()).isEqualTo("New Title");
        assertThat(result.getVersion()).isEqualTo(2L); // Version incremented
        verify(eventSeriesRepository).save(series);
        verify(groupRepository, atLeastOnce()).saveSeriesPointer(any(SeriesPointer.class));
    }

    @Test
    void updateSeries_WithDescriptionUpdate_UpdatesSeriesDescription() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String groupId = UUID.randomUUID().toString();

        EventSeries series = new EventSeries();
        series.setSeriesId(seriesId);
        series.setSeriesTitle("Title");
        series.setSeriesDescription("Old Description");
        series.setVersion(1L);
        series.setGroupId(groupId);
        series.setHangoutIds(Arrays.asList("hangout1"));

        User user = new User();
        user.setId(UUID.fromString(userId));

        UpdateSeriesRequest request = new UpdateSeriesRequest();
        request.setVersion(1L);
        request.setSeriesDescription("New Description");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));
        when(hangoutRepository.findHangoutById("hangout1")).thenReturn(Optional.of(new Hangout()));
        when(hangoutRepository.findPointersForHangout(any())).thenReturn(Collections.emptyList());

        // When
        EventSeries result = eventSeriesService.updateSeries(seriesId, request, userId);

        // Then
        assertThat(result.getSeriesDescription()).isEqualTo("New Description");
        assertThat(result.getVersion()).isEqualTo(2L);
        verify(eventSeriesRepository).save(series);
    }

    @Test
    void updateSeries_WithPrimaryEventIdUpdate_UpdatesPrimaryEventId() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String groupId = UUID.randomUUID().toString();
        String hangout1Id = UUID.randomUUID().toString();
        String hangout2Id = UUID.randomUUID().toString();

        EventSeries series = new EventSeries();
        series.setSeriesId(seriesId);
        series.setSeriesTitle("Title");
        series.setPrimaryEventId(hangout1Id);
        series.setVersion(1L);
        series.setGroupId(groupId);
        series.setHangoutIds(Arrays.asList(hangout1Id, hangout2Id));

        User user = new User();
        user.setId(UUID.fromString(userId));

        UpdateSeriesRequest request = new UpdateSeriesRequest();
        request.setVersion(1L);
        request.setPrimaryEventId(hangout2Id); // Change to hangout2

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));
        when(hangoutRepository.findHangoutById(hangout1Id)).thenReturn(Optional.of(new Hangout()));
        when(hangoutRepository.findHangoutById(hangout2Id)).thenReturn(Optional.of(new Hangout()));
        when(hangoutRepository.findPointersForHangout(any())).thenReturn(Collections.emptyList());

        // When
        EventSeries result = eventSeriesService.updateSeries(seriesId, request, userId);

        // Then
        assertThat(result.getPrimaryEventId()).isEqualTo(hangout2Id);
        assertThat(result.getVersion()).isEqualTo(2L);
        verify(eventSeriesRepository).save(series);
    }

    @Test
    void updateSeries_WithAllFieldsUpdated_UpdatesAllFields() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String groupId = UUID.randomUUID().toString();
        String hangout1Id = UUID.randomUUID().toString();
        String hangout2Id = UUID.randomUUID().toString();

        EventSeries series = new EventSeries();
        series.setSeriesId(seriesId);
        series.setSeriesTitle("Old Title");
        series.setSeriesDescription("Old Description");
        series.setPrimaryEventId(hangout1Id);
        series.setVersion(1L);
        series.setGroupId(groupId);
        series.setHangoutIds(Arrays.asList(hangout1Id, hangout2Id));

        User user = new User();
        user.setId(UUID.fromString(userId));

        UpdateSeriesRequest request = new UpdateSeriesRequest();
        request.setVersion(1L);
        request.setSeriesTitle("New Title");
        request.setSeriesDescription("New Description");
        request.setPrimaryEventId(hangout2Id);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));
        when(hangoutRepository.findHangoutById(hangout1Id)).thenReturn(Optional.of(new Hangout()));
        when(hangoutRepository.findHangoutById(hangout2Id)).thenReturn(Optional.of(new Hangout()));
        when(hangoutRepository.findPointersForHangout(any())).thenReturn(Collections.emptyList());

        // When
        EventSeries result = eventSeriesService.updateSeries(seriesId, request, userId);

        // Then
        assertThat(result.getSeriesTitle()).isEqualTo("New Title");
        assertThat(result.getSeriesDescription()).isEqualTo("New Description");
        assertThat(result.getPrimaryEventId()).isEqualTo(hangout2Id);
        assertThat(result.getVersion()).isEqualTo(2L);
        verify(eventSeriesRepository).save(series);
    }

    @Test
    void updateSeries_WithNoUpdates_ReturnsUnchangedSeries() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String groupId = UUID.randomUUID().toString();

        EventSeries series = new EventSeries();
        series.setSeriesId(seriesId);
        series.setSeriesTitle("Title");
        series.setVersion(1L);
        series.setGroupId(groupId);
        series.setHangoutIds(Arrays.asList("hangout1"));

        User user = new User();
        user.setId(UUID.fromString(userId));

        UpdateSeriesRequest request = new UpdateSeriesRequest();
        request.setVersion(1L);
        // No updates set - all fields are null

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));

        // When
        EventSeries result = eventSeriesService.updateSeries(seriesId, request, userId);

        // Then
        assertThat(result.getSeriesTitle()).isEqualTo("Title");
        assertThat(result.getVersion()).isEqualTo(1L); // Version not incremented
        verify(eventSeriesRepository, never()).save(any());
        verify(groupRepository, never()).saveSeriesPointer(any());
    }

    @Test
    void updateSeries_WithInvalidPrimaryEventId_ThrowsValidationException() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String groupId = UUID.randomUUID().toString();
        String hangout1Id = UUID.randomUUID().toString();
        String invalidHangoutId = UUID.randomUUID().toString(); // Not in series

        EventSeries series = new EventSeries();
        series.setSeriesId(seriesId);
        series.setSeriesTitle("Title");
        series.setVersion(1L);
        series.setGroupId(groupId);
        series.setHangoutIds(Arrays.asList(hangout1Id)); // Only contains hangout1

        User user = new User();
        user.setId(UUID.fromString(userId));

        UpdateSeriesRequest request = new UpdateSeriesRequest();
        request.setVersion(1L);
        request.setPrimaryEventId(invalidHangoutId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));

        // When & Then
        assertThatThrownBy(() -> eventSeriesService.updateSeries(seriesId, request, userId))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("not a member of series");

        verify(eventSeriesRepository, never()).save(any());
    }

    @Test
    void updateSeries_WithNonExistentUser_ThrowsUnauthorizedException() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();

        UpdateSeriesRequest request = new UpdateSeriesRequest();
        request.setVersion(1L);
        request.setSeriesTitle("New Title");

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> eventSeriesService.updateSeries(seriesId, request, userId))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("not found");

        verify(eventSeriesRepository, never()).findById(any());
        verify(eventSeriesRepository, never()).save(any());
    }

    @Test
    void updateSeries_WithNonExistentSeries_ThrowsResourceNotFoundException() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();

        User user = new User();
        user.setId(UUID.fromString(userId));

        UpdateSeriesRequest request = new UpdateSeriesRequest();
        request.setVersion(1L);
        request.setSeriesTitle("New Title");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> eventSeriesService.updateSeries(seriesId, request, userId))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("EventSeries not found");

        verify(eventSeriesRepository, never()).save(any());
    }

    @Test
    void updateSeries_WithSaveFailure_ThrowsRepositoryException() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String groupId = UUID.randomUUID().toString();

        EventSeries series = new EventSeries();
        series.setSeriesId(seriesId);
        series.setSeriesTitle("Old Title");
        series.setVersion(1L);
        series.setGroupId(groupId);
        series.setHangoutIds(Arrays.asList("hangout1"));

        User user = new User();
        user.setId(UUID.fromString(userId));

        UpdateSeriesRequest request = new UpdateSeriesRequest();
        request.setVersion(1L);
        request.setSeriesTitle("New Title");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));
        when(eventSeriesRepository.save(any())).thenThrow(new RuntimeException("Database error"));

        // When & Then
        assertThatThrownBy(() -> eventSeriesService.updateSeries(seriesId, request, userId))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("Failed to update series");
    }

    @Test
    void updateSeries_UpdatesSeriesPointers_ToMaintainConsistency() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String groupId = UUID.randomUUID().toString();
        String hangout1Id = UUID.randomUUID().toString();

        EventSeries series = new EventSeries();
        series.setSeriesId(seriesId);
        series.setSeriesTitle("Old Title");
        series.setVersion(1L);
        series.setGroupId(groupId);
        series.setHangoutIds(Arrays.asList(hangout1Id));

        User user = new User();
        user.setId(UUID.fromString(userId));

        UpdateSeriesRequest request = new UpdateSeriesRequest();
        request.setVersion(1L);
        request.setSeriesTitle("New Title");

        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangout1Id);

        HangoutPointer pointer = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId)
            .forHangout(hangout1Id)
            .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));
        when(hangoutRepository.findHangoutById(hangout1Id)).thenReturn(Optional.of(hangout));
        when(hangoutRepository.findPointersForHangout(hangout)).thenReturn(Arrays.asList(pointer));

        // When
        eventSeriesService.updateSeries(seriesId, request, userId);

        // Then
        ArgumentCaptor<SeriesPointer> seriesPointerCaptor = ArgumentCaptor.forClass(SeriesPointer.class);
        verify(groupRepository, atLeastOnce()).saveSeriesPointer(seriesPointerCaptor.capture());

        // Verify SeriesPointer has updated title
        SeriesPointer savedPointer = seriesPointerCaptor.getValue();
        assertThat(savedPointer.getSeriesTitle()).isEqualTo("New Title");
        assertThat(savedPointer.getGroupId()).isEqualTo(groupId);
    }

    // Tests for calculateLatestTimestamp() helper method

    @Test
    void calculateLatestTimestamp_WithMultipleHangoutsWithEndTimes_ReturnsLatestEndTime() throws Exception {
        // Given
        Hangout hangout1 = HangoutTestBuilder.aHangout()
            .withStartTimestamp(1000L)
            .withEndTimestamp(2000L)
            .build();

        Hangout hangout2 = HangoutTestBuilder.aHangout()
            .withStartTimestamp(1500L)
            .withEndTimestamp(3000L) // Latest
            .build();

        Hangout hangout3 = HangoutTestBuilder.aHangout()
            .withStartTimestamp(500L)
            .withEndTimestamp(1500L)
            .build();

        List<Hangout> hangouts = Arrays.asList(hangout1, hangout2, hangout3);

        // When
        Method calculateLatestMethod = EventSeriesServiceImpl.class.getDeclaredMethod("calculateLatestTimestamp", List.class);
        calculateLatestMethod.setAccessible(true);
        Long result = (Long) calculateLatestMethod.invoke(eventSeriesService, hangouts);

        // Then
        assertThat(result).isEqualTo(3000L); // Latest endTimestamp
    }

    @Test
    void calculateLatestTimestamp_WithHangoutsWithOnlyStartTimes_ReturnsLatestStartTime() throws Exception {
        // Given
        Hangout hangout1 = HangoutTestBuilder.aHangout()
            .withStartTimestamp(1000L)
            .build();

        Hangout hangout2 = HangoutTestBuilder.aHangout()
            .withStartTimestamp(3000L) // Latest
            .build();

        Hangout hangout3 = HangoutTestBuilder.aHangout()
            .withStartTimestamp(2000L)
            .build();

        List<Hangout> hangouts = Arrays.asList(hangout1, hangout2, hangout3);

        // When
        Method calculateLatestMethod = EventSeriesServiceImpl.class.getDeclaredMethod("calculateLatestTimestamp", List.class);
        calculateLatestMethod.setAccessible(true);
        Long result = (Long) calculateLatestMethod.invoke(eventSeriesService, hangouts);

        // Then
        assertThat(result).isEqualTo(3000L); // Latest startTimestamp (no endTimestamp)
    }

    @Test
    void calculateLatestTimestamp_WithMixedTimestamps_PrefersEndOverStart() throws Exception {
        // Given
        Hangout hangout1 = HangoutTestBuilder.aHangout()
            .withStartTimestamp(1000L)
            .withEndTimestamp(2000L)
            .build();

        Hangout hangout2 = HangoutTestBuilder.aHangout()
            .withStartTimestamp(5000L) // Latest start, but no end
            .build();

        Hangout hangout3 = HangoutTestBuilder.aHangout()
            .withStartTimestamp(1500L)
            .withEndTimestamp(4000L) // Latest end
            .build();

        List<Hangout> hangouts = Arrays.asList(hangout1, hangout2, hangout3);

        // When
        Method calculateLatestMethod = EventSeriesServiceImpl.class.getDeclaredMethod("calculateLatestTimestamp", List.class);
        calculateLatestMethod.setAccessible(true);
        Long result = (Long) calculateLatestMethod.invoke(eventSeriesService, hangouts);

        // Then
        assertThat(result).isEqualTo(5000L); // Latest overall (start from hangout2)
    }

    @Test
    void calculateLatestTimestamp_WithSomeNullTimestamps_HandlesGracefully() throws Exception {
        // Given
        Hangout hangout1 = HangoutTestBuilder.aHangout()
            .withStartTimestamp(1000L)
            .withEndTimestamp(2000L)
            .build();

        Hangout hangout2 = HangoutTestBuilder.aHangout()
            .build(); // No timestamps

        Hangout hangout3 = HangoutTestBuilder.aHangout()
            .withStartTimestamp(3000L) // Latest
            .build();

        List<Hangout> hangouts = Arrays.asList(hangout1, hangout2, hangout3);

        // When
        Method calculateLatestMethod = EventSeriesServiceImpl.class.getDeclaredMethod("calculateLatestTimestamp", List.class);
        calculateLatestMethod.setAccessible(true);
        Long result = (Long) calculateLatestMethod.invoke(eventSeriesService, hangouts);

        // Then
        assertThat(result).isEqualTo(3000L); // Ignores hangout with null timestamps
    }

    @Test
    void calculateLatestTimestamp_WithAllNullTimestamps_ReturnsNull() throws Exception {
        // Given
        Hangout hangout1 = HangoutTestBuilder.aHangout().build();
        Hangout hangout2 = HangoutTestBuilder.aHangout().build();

        List<Hangout> hangouts = Arrays.asList(hangout1, hangout2);

        // When
        Method calculateLatestMethod = EventSeriesServiceImpl.class.getDeclaredMethod("calculateLatestTimestamp", List.class);
        calculateLatestMethod.setAccessible(true);
        Long result = (Long) calculateLatestMethod.invoke(eventSeriesService, hangouts);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void calculateLatestTimestamp_WithEmptyList_ReturnsNull() throws Exception {
        // Given
        List<Hangout> hangouts = Collections.emptyList();

        // When
        Method calculateLatestMethod = EventSeriesServiceImpl.class.getDeclaredMethod("calculateLatestTimestamp", List.class);
        calculateLatestMethod.setAccessible(true);
        Long result = (Long) calculateLatestMethod.invoke(eventSeriesService, hangouts);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void calculateLatestTimestamp_WithSingleHangoutWithBothTimestamps_ReturnsEndTimestamp() throws Exception {
        // Given
        Hangout hangout = HangoutTestBuilder.aHangout()
            .withStartTimestamp(1000L)
            .withEndTimestamp(2000L)
            .build();

        List<Hangout> hangouts = Arrays.asList(hangout);

        // When
        Method calculateLatestMethod = EventSeriesServiceImpl.class.getDeclaredMethod("calculateLatestTimestamp", List.class);
        calculateLatestMethod.setAccessible(true);
        Long result = (Long) calculateLatestMethod.invoke(eventSeriesService, hangouts);

        // Then
        assertThat(result).isEqualTo(2000L); // Prefers endTimestamp when both exist
    }

    @Test
    void calculateLatestTimestamp_WithSingleHangoutOnlyStartTime_ReturnsStartTimestamp() throws Exception {
        // Given
        Hangout hangout = HangoutTestBuilder.aHangout()
            .withStartTimestamp(1000L)
            .build();

        List<Hangout> hangouts = Arrays.asList(hangout);

        // When
        Method calculateLatestMethod = EventSeriesServiceImpl.class.getDeclaredMethod("calculateLatestTimestamp", List.class);
        calculateLatestMethod.setAccessible(true);
        Long result = (Long) calculateLatestMethod.invoke(eventSeriesService, hangouts);

        // Then
        assertThat(result).isEqualTo(1000L);
    }
}