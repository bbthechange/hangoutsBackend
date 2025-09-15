package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.CreateHangoutRequest;
import com.bbthechange.inviter.exception.RepositoryException;
import com.bbthechange.inviter.exception.ResourceNotFoundException;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.model.EventSeries;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.model.HangoutPointer;
import com.bbthechange.inviter.model.User;
import com.bbthechange.inviter.repository.EventSeriesRepository;
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
            newPointersCaptor.capture()
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
        verify(seriesTransactionRepository, never()).createSeriesWithNewPart(any(), any(), any(), any(), any());
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
        verify(seriesTransactionRepository, never()).createSeriesWithNewPart(any(), any(), any(), any(), any());
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
        verify(seriesTransactionRepository, never()).createSeriesWithNewPart(any(), any(), any(), any(), any());
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
            .createSeriesWithNewPart(any(), any(), any(), any(), any());
        
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
            newPointersCaptor.capture()
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
        verify(seriesTransactionRepository, never()).addPartToExistingSeries(any(), any(), any());
    }
}