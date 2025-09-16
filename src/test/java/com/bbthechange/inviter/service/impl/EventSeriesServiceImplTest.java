package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.CreateHangoutRequest;
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
        assertThat(result.getSeriesTitle()).isEqualTo("Movie Night Series");
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
        
        // Mock the hangout creation in the service implementation directly
        // instead of through hangoutService since the service creates it manually
        
        when(eventSeriesRepository.findById(seriesId))
            .thenReturn(Optional.of(existingSeries));
        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        
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
}