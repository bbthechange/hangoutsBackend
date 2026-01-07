package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.exception.EventNotFoundException;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.service.AuthorizationService;
import com.bbthechange.inviter.service.GroupTimestampService;
import com.bbthechange.inviter.service.PollService;
import com.bbthechange.inviter.testutil.HangoutPointerTestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.MockedStatic;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import org.junit.jupiter.api.Nested;

@ExtendWith(MockitoExtension.class)
class PollServiceImplTest {

    @Mock
    private HangoutRepository hangoutRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private PointerUpdateService pointerUpdateService;

    @Mock
    private GroupTimestampService groupTimestampService;

    @InjectMocks
    private PollServiceImpl pollService;

    private String eventId;
    private String pollId;
    private String userId;
    private String optionId;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID().toString();
        pollId = UUID.randomUUID().toString();
        userId = UUID.randomUUID().toString();
        optionId = UUID.randomUUID().toString();
    }

    @Test
    void createPoll_WithValidRequest_CreatesPollAndOptions() {
        // Given
        CreatePollRequest request = new CreatePollRequest();
        request.setTitle("Test Poll");
        request.setDescription("Description");
        request.setMultipleChoice(false);
        request.setOptions(Arrays.asList("Option A", "Option B", "Option C"));

        Hangout hangout = new Hangout();
        hangout.setHangoutId(eventId);
        HangoutDetailData hangoutData = HangoutDetailData.builder().withHangout(hangout).build();

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(authorizationService.canUserEditHangout(userId, hangout)).thenReturn(true);
        when(hangoutRepository.savePoll(any(Poll.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(hangoutRepository.savePollOption(any(PollOption.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Poll result = pollService.createPoll(eventId, request, userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Test Poll");
        verify(hangoutRepository).savePoll(any(Poll.class));
        verify(hangoutRepository, times(3)).savePollOption(any(PollOption.class));
    }

    @Test
    void createPoll_WhenUserCannotEdit_ThrowsUnauthorizedException() {
        // Given
        CreatePollRequest request = new CreatePollRequest();
        Hangout hangout = new Hangout();
        HangoutDetailData hangoutData = HangoutDetailData.builder().withHangout(hangout).build();

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(authorizationService.canUserEditHangout(userId, hangout)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> pollService.createPoll(eventId, request, userId))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void voteOnPoll_SingleChoice_FirstVote_CreatesVote() {
        // Given
        VoteRequest request = new VoteRequest();
        request.setOptionId(optionId);
        request.setVoteType("YES");

        Hangout hangout = new Hangout();
        // Hangout ID is handled differently - using eventId as string in tests
        HangoutDetailData hangoutData = HangoutDetailData.builder().withHangout(hangout).build();

        Poll poll = new Poll(eventId, "Test Poll", "Description", false);
        PollOption option = new PollOption(eventId, pollId, "Option Text");
        List<BaseItem> pollData = Arrays.asList(poll, option);

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(authorizationService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(hangoutRepository.getSpecificPollData(eventId, pollId)).thenReturn(pollData);
        when(hangoutRepository.saveVote(any(Vote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Vote result = pollService.voteOnPoll(eventId, pollId, request, userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getOptionId()).isEqualTo(optionId);
        verify(hangoutRepository).saveVote(any(Vote.class));
        verify(hangoutRepository, never()).deleteVote(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void voteOnPoll_SingleChoice_ChangeVote_DeletesOldCreatesNew() {
        // Given
        String oldOptionId = UUID.randomUUID().toString();
        VoteRequest request = new VoteRequest();
        request.setOptionId(optionId);
        request.setVoteType("YES");

        Hangout hangout = new Hangout();
        // Hangout ID is handled differently - using eventId as string in tests
        HangoutDetailData hangoutData = HangoutDetailData.builder().withHangout(hangout).build();

        Poll poll = new Poll(eventId, "Test Poll", "Description", false);
        Vote existingVote = new Vote(eventId, pollId, oldOptionId, userId, "YES");
        List<BaseItem> pollData = Arrays.asList(poll, existingVote);

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(authorizationService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(hangoutRepository.getSpecificPollData(eventId, pollId)).thenReturn(pollData);
        when(hangoutRepository.saveVote(any(Vote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Vote result = pollService.voteOnPoll(eventId, pollId, request, userId);

        // Then
        assertThat(result).isNotNull();
        verify(hangoutRepository).deleteVote(eventId, pollId, userId, oldOptionId);
        verify(hangoutRepository).saveVote(any(Vote.class));
    }

    @Test
    void voteOnPoll_MultipleChoice_AllowsMultipleVotes() {
        // Given
        VoteRequest request = new VoteRequest();
        request.setOptionId(optionId);
        request.setVoteType("YES");

        Hangout hangout = new Hangout();
        // Hangout ID is handled differently - using eventId as string in tests
        HangoutDetailData hangoutData = HangoutDetailData.builder().withHangout(hangout).build();

        Poll poll = new Poll(eventId, "Test Poll", "Description", true);
        String otherOptionId = UUID.randomUUID().toString();
        Vote existingVote = new Vote(eventId, pollId, otherOptionId, userId, "YES");
        List<BaseItem> pollData = Arrays.asList(poll, existingVote);

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(authorizationService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(hangoutRepository.getSpecificPollData(eventId, pollId)).thenReturn(pollData);
        when(hangoutRepository.saveVote(any(Vote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Vote result = pollService.voteOnPoll(eventId, pollId, request, userId);

        // Then
        assertThat(result).isNotNull();
        verify(hangoutRepository, never()).deleteVote(anyString(), anyString(), anyString(), anyString());
        verify(hangoutRepository).saveVote(any(Vote.class));
    }

    @Test
    void getEventPolls_WithValidUser_ReturnsTransformedPolls() {
        // Given
        Hangout hangout = new Hangout();
        // Hangout ID is handled differently - using eventId as string in tests
        HangoutDetailData hangoutData = HangoutDetailData.builder().withHangout(hangout).build();

        Poll poll = new Poll(eventId, "Test Poll", "Description", false);
        PollOption option1 = new PollOption(eventId, poll.getPollId(), "Option 1");
        PollOption option2 = new PollOption(eventId, poll.getPollId(), "Option 2");
        Vote vote1 = new Vote(eventId, poll.getPollId(), option1.getOptionId(), userId, "YES");
        Vote vote2 = new Vote(eventId, poll.getPollId(), option2.getOptionId(), UUID.randomUUID().toString(), "YES");

        List<BaseItem> pollData = Arrays.asList(poll, option1, option2, vote1, vote2);

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(authorizationService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(hangoutRepository.getAllPollData(eventId)).thenReturn(pollData);

        // When
        List<PollWithOptionsDTO> result = pollService.getEventPolls(eventId, userId);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOptions()).hasSize(2);
        assertThat(result.get(0).getTotalVotes()).isEqualTo(2);
    }

    @Test
    void addPollOption_AsHost_AddsOption() {
        // Given
        AddPollOptionRequest request = new AddPollOptionRequest("New Option");

        Hangout hangout = new Hangout();
        hangout.setHangoutId(eventId);
        HangoutDetailData hangoutData = HangoutDetailData.builder().withHangout(hangout).build();

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(authorizationService.canUserEditHangout(userId, hangout)).thenReturn(true);
        when(hangoutRepository.savePollOption(any(PollOption.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        PollOption result = pollService.addPollOption(eventId, pollId, request, userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getText()).isEqualTo("New Option");
        verify(hangoutRepository).savePollOption(any(PollOption.class));
    }

    @Test
    void deletePollOption_AsHost_DeletesOptionAndVotes() {
        // Given
        Hangout hangout = new Hangout();
        // Hangout ID is handled differently - using eventId as string in tests
        HangoutDetailData hangoutData = HangoutDetailData.builder().withHangout(hangout).build();

        Poll poll = new Poll(eventId, "Test Poll", "Description", false);
        PollOption option = new PollOption(eventId, pollId, "Option to delete");
        option.setOptionId(optionId);
        List<BaseItem> pollData = Arrays.asList(poll, option);

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(authorizationService.canUserEditHangout(userId, hangout)).thenReturn(true);
        when(hangoutRepository.getSpecificPollData(eventId, pollId)).thenReturn(pollData);

        // When
        pollService.deletePollOption(eventId, pollId, optionId, userId);

        // Then
        verify(hangoutRepository).deletePollOptionTransaction(eventId, pollId, optionId);
    }

    @Test
    void deletePollOption_WhenOptionNotFound_ThrowsException() {
        // Given
        Hangout hangout = new Hangout();
        // Hangout ID is handled differently - using eventId as string in tests
        HangoutDetailData hangoutData = HangoutDetailData.builder().withHangout(hangout).build();

        Poll poll = new Poll(eventId, "Test Poll", "Description", false);
        List<BaseItem> pollData = Arrays.asList(poll);

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(authorizationService.canUserEditHangout(userId, hangout)).thenReturn(true);
        when(hangoutRepository.getSpecificPollData(eventId, pollId)).thenReturn(pollData);

        // When & Then
        assertThatThrownBy(() -> pollService.deletePollOption(eventId, pollId, optionId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Poll option not found");
    }

    @Test
    void getEventPolls_WhenUserCannotView_ThrowsUnauthorizedException() {
        // Given
        Hangout hangout = new Hangout();
        HangoutDetailData hangoutData = HangoutDetailData.builder().withHangout(hangout).build();

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(authorizationService.canUserViewHangout(userId, hangout)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> pollService.getEventPolls(eventId, userId))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void addPollOption_WhenUserCannotEdit_ThrowsUnauthorizedException() {
        // Given
        AddPollOptionRequest request = new AddPollOptionRequest("New Option");
        Hangout hangout = new Hangout();
        HangoutDetailData hangoutData = HangoutDetailData.builder().withHangout(hangout).build();

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(authorizationService.canUserEditHangout(userId, hangout)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> pollService.addPollOption(eventId, pollId, request, userId))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void deletePoll_WhenUserCannotEdit_ThrowsUnauthorizedException() {
        // Given
        Hangout hangout = new Hangout();
        HangoutDetailData hangoutData = HangoutDetailData.builder().withHangout(hangout).build();

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(authorizationService.canUserEditHangout(userId, hangout)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> pollService.deletePoll(eventId, pollId, userId))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void deletePollOption_WhenUserCannotEdit_ThrowsUnauthorizedException() {
        // Given
        Hangout hangout = new Hangout();
        HangoutDetailData hangoutData = HangoutDetailData.builder().withHangout(hangout).build();

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(authorizationService.canUserEditHangout(userId, hangout)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> pollService.deletePollOption(eventId, pollId, optionId, userId))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void voteOnPoll_WhenUserCannotView_ThrowsUnauthorizedException() {
        // Given
        VoteRequest request = new VoteRequest();
        request.setOptionId(optionId);
        request.setVoteType("YES");

        Hangout hangout = new Hangout();
        HangoutDetailData hangoutData = HangoutDetailData.builder().withHangout(hangout).build();

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(authorizationService.canUserViewHangout(userId, hangout)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> pollService.voteOnPoll(eventId, pollId, request, userId))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void removeVote_WhenUserCannotView_ThrowsUnauthorizedException() {
        // Given
        Hangout hangout = new Hangout();
        HangoutDetailData hangoutData = HangoutDetailData.builder().withHangout(hangout).build();

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(authorizationService.canUserViewHangout(userId, hangout)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> pollService.removeVote(eventId, pollId, optionId, userId))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void removeVote_WithValidRequest_DeletesVote() {
        // Given
        Hangout hangout = new Hangout();
        // Hangout ID is handled differently - using eventId as string in tests
        HangoutDetailData hangoutData = HangoutDetailData.builder().withHangout(hangout).build();

        Poll poll = new Poll(eventId, "Test Poll", "Description", false);
        Vote existingVote = new Vote(eventId, pollId, optionId, userId, "YES");
        List<BaseItem> pollData = Arrays.asList(poll, existingVote);

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(authorizationService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(hangoutRepository.getSpecificPollData(eventId, pollId)).thenReturn(pollData);

        // When
        pollService.removeVote(eventId, pollId, null, userId);

        // Then
        verify(hangoutRepository).deleteVote(eventId, pollId, userId, optionId);
    }

    // ==================== Pointer Synchronization Tests ====================

    @Test
    void createPoll_WithValidPoll_ShouldUpdateAllPointers() {
        // Given
        String groupId1 = UUID.randomUUID().toString();
        String groupId2 = UUID.randomUUID().toString();

        CreatePollRequest request = new CreatePollRequest();
        request.setTitle("Test Poll");
        request.setDescription("Description");
        request.setMultipleChoice(false);
        request.setOptions(Arrays.asList("Option A", "Option B", "Option C"));

        Hangout hangout = new Hangout();
        hangout.setHangoutId(eventId);
        hangout.setAssociatedGroups(Arrays.asList(groupId1, groupId2));
        HangoutDetailData hangoutData = HangoutDetailData.builder().withHangout(hangout).build();

        Poll newPoll = new Poll(eventId, "Test Poll", "Description", false);
        PollOption option1 = new PollOption(eventId, newPoll.getPollId(), "Option A");
        PollOption option2 = new PollOption(eventId, newPoll.getPollId(), "Option B");
        PollOption option3 = new PollOption(eventId, newPoll.getPollId(), "Option C");
        List<BaseItem> pollData = Arrays.asList(newPoll, option1, option2, option3);

        HangoutPointer pointer1 = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId1)
            .forHangout(eventId)
            .build();
        HangoutPointer pointer2 = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId2)
            .forHangout(eventId)
            .build();

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(authorizationService.canUserEditHangout(userId, hangout)).thenReturn(true);
        when(hangoutRepository.savePoll(any(Poll.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(hangoutRepository.savePollOption(any(PollOption.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(hangoutRepository.findHangoutById(eventId)).thenReturn(Optional.of(hangout));
        when(hangoutRepository.getAllPollData(eventId)).thenReturn(pollData);

        // When
        Poll result = pollService.createPoll(eventId, request, userId);

        // Then
        assertThat(result).isNotNull();
        verify(hangoutRepository).savePoll(any(Poll.class));
        verify(hangoutRepository, times(3)).savePollOption(any(PollOption.class));
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId1), eq(eventId), any(), eq("poll data"));
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId2), eq(eventId), any(), eq("poll data"));
    }

    @Test
    void createPoll_WithNoAssociatedGroups_ShouldNotUpdatePointers() {
        // Given
        CreatePollRequest request = new CreatePollRequest();
        request.setTitle("Test Poll");
        request.setDescription("Description");
        request.setMultipleChoice(false);
        request.setOptions(Arrays.asList("Option A", "Option B"));

        Hangout hangout = new Hangout();
        hangout.setHangoutId(eventId);
        hangout.setAssociatedGroups(Collections.emptyList());
        HangoutDetailData hangoutData = HangoutDetailData.builder().withHangout(hangout).build();

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(authorizationService.canUserEditHangout(userId, hangout)).thenReturn(true);
        when(hangoutRepository.savePoll(any(Poll.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(hangoutRepository.savePollOption(any(PollOption.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(hangoutRepository.findHangoutById(eventId)).thenReturn(Optional.of(hangout));

        // When
        Poll result = pollService.createPoll(eventId, request, userId);

        // Then
        assertThat(result).isNotNull();
        verify(hangoutRepository).savePoll(any(Poll.class));
        verify(hangoutRepository, times(2)).savePollOption(any(PollOption.class));
        verify(pointerUpdateService, never()).updatePointerWithRetry(anyString(), anyString(), any(), anyString());
    }

    @Test
    void voteOnPoll_WithValidVote_ShouldUpdateAllPointers() {
        // Given
        String groupId = UUID.randomUUID().toString();

        VoteRequest request = new VoteRequest();
        request.setOptionId(optionId);
        request.setVoteType("YES");

        Hangout hangout = new Hangout();
        hangout.setAssociatedGroups(Arrays.asList(groupId));
        HangoutDetailData hangoutData = HangoutDetailData.builder().withHangout(hangout).build();

        Poll poll = new Poll(eventId, "Test Poll", "Description", true);
        PollOption option = new PollOption(eventId, pollId, "Option Text");
        option.setOptionId(optionId);
        List<BaseItem> specificPollData = Arrays.asList(poll, option);

        Vote newVote = new Vote(eventId, pollId, optionId, userId, "YES");
        List<BaseItem> allPollData = Arrays.asList(poll, option, newVote);

        HangoutPointer pointer = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId)
            .forHangout(eventId)
            .build();

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(authorizationService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(hangoutRepository.getSpecificPollData(eventId, pollId)).thenReturn(specificPollData);
        when(hangoutRepository.saveVote(any(Vote.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(hangoutRepository.findHangoutById(eventId)).thenReturn(Optional.of(hangout));
        when(hangoutRepository.getAllPollData(eventId)).thenReturn(allPollData);

        // When
        Vote result = pollService.voteOnPoll(eventId, pollId, request, userId);

        // Then
        assertThat(result).isNotNull();
        verify(hangoutRepository).saveVote(any(Vote.class));
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId), eq(eventId), any(), eq("poll data"));
    }

    @Test
    void voteOnPoll_WhenPointerNotFound_ShouldContinueWithoutFailure() {
        // Given
        String groupId = UUID.randomUUID().toString();

        VoteRequest request = new VoteRequest();
        request.setOptionId(optionId);
        request.setVoteType("YES");

        Hangout hangout = new Hangout();
        hangout.setAssociatedGroups(Arrays.asList(groupId));
        HangoutDetailData hangoutData = HangoutDetailData.builder().withHangout(hangout).build();

        Poll poll = new Poll(eventId, "Test Poll", "Description", true);
        PollOption option = new PollOption(eventId, pollId, "Option Text");
        option.setOptionId(optionId);
        List<BaseItem> pollData = Arrays.asList(poll, option);

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(authorizationService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(hangoutRepository.getSpecificPollData(eventId, pollId)).thenReturn(pollData);
        when(hangoutRepository.saveVote(any(Vote.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(hangoutRepository.findHangoutById(eventId)).thenReturn(Optional.of(hangout));

        // When
        Vote result = pollService.voteOnPoll(eventId, pollId, request, userId);

        // Then
        assertThat(result).isNotNull();
        verify(hangoutRepository).saveVote(any(Vote.class));
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId), eq(eventId), any(), eq("poll data"));
    }

    @Test
    void removeVote_WithValidVote_ShouldUpdateAllPointers() {
        // Given
        String groupId1 = UUID.randomUUID().toString();
        String groupId2 = UUID.randomUUID().toString();

        Hangout hangout = new Hangout();
        hangout.setAssociatedGroups(Arrays.asList(groupId1, groupId2));
        HangoutDetailData hangoutData = HangoutDetailData.builder().withHangout(hangout).build();

        Poll poll = new Poll(eventId, "Test Poll", "Description", false);
        Vote existingVote = new Vote(eventId, pollId, optionId, userId, "YES");
        List<BaseItem> specificPollData = Arrays.asList(poll, existingVote);

        // After vote removal
        List<BaseItem> allPollData = Arrays.asList(poll);

        HangoutPointer pointer1 = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId1)
            .forHangout(eventId)
            .build();
        HangoutPointer pointer2 = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId2)
            .forHangout(eventId)
            .build();

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(authorizationService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(hangoutRepository.getSpecificPollData(eventId, pollId)).thenReturn(specificPollData);
        when(hangoutRepository.findHangoutById(eventId)).thenReturn(Optional.of(hangout));
        when(hangoutRepository.getAllPollData(eventId)).thenReturn(allPollData);

        // When
        pollService.removeVote(eventId, pollId, optionId, userId);

        // Then
        verify(hangoutRepository).deleteVote(eventId, pollId, userId, optionId);
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId1), eq(eventId), any(), eq("poll data"));
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId2), eq(eventId), any(), eq("poll data"));
    }

    @Test
    void removeVote_WithNoVotesToRemove_ShouldNotUpdatePointers() {
        // Given
        String groupId = UUID.randomUUID().toString();

        Hangout hangout = new Hangout();
        hangout.setAssociatedGroups(Arrays.asList(groupId));
        HangoutDetailData hangoutData = HangoutDetailData.builder().withHangout(hangout).build();

        Poll poll = new Poll(eventId, "Test Poll", "Description", false);
        List<BaseItem> pollData = Arrays.asList(poll);

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(authorizationService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(hangoutRepository.getSpecificPollData(eventId, pollId)).thenReturn(pollData);

        // When
        pollService.removeVote(eventId, pollId, optionId, userId);

        // Then
        verify(hangoutRepository, never()).deleteVote(anyString(), anyString(), anyString(), anyString());
        verify(pointerUpdateService, never()).updatePointerWithRetry(anyString(), anyString(), any(), anyString());
    }

    @Test
    void deletePoll_WithValidPoll_ShouldRemoveFromAllPointers() {
        // Given
        String groupId1 = UUID.randomUUID().toString();
        String groupId2 = UUID.randomUUID().toString();
        String groupId3 = UUID.randomUUID().toString();

        Hangout hangout = new Hangout();
        hangout.setAssociatedGroups(Arrays.asList(groupId1, groupId2, groupId3));
        HangoutDetailData hangoutData = HangoutDetailData.builder().withHangout(hangout).build();

        // After deletion, only 1 poll remains
        Poll remainingPoll = new Poll(eventId, "Other Poll", "Description", false);
        List<BaseItem> pollData = Arrays.asList(remainingPoll);

        HangoutPointer pointer1 = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId1)
            .forHangout(eventId)
            .build();
        HangoutPointer pointer2 = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId2)
            .forHangout(eventId)
            .build();
        HangoutPointer pointer3 = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId3)
            .forHangout(eventId)
            .build();

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(authorizationService.canUserEditHangout(userId, hangout)).thenReturn(true);
        when(hangoutRepository.findHangoutById(eventId)).thenReturn(Optional.of(hangout));
        when(hangoutRepository.getAllPollData(eventId)).thenReturn(pollData);

        // When
        pollService.deletePoll(eventId, pollId, userId);

        // Then
        verify(hangoutRepository).deletePoll(eventId, pollId);
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId1), eq(eventId), any(), eq("poll data"));
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId2), eq(eventId), any(), eq("poll data"));
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId3), eq(eventId), any(), eq("poll data"));
    }

    @Test
    void addPollOption_WithValidOption_ShouldUpdateAllPointers() {
        // Given
        String groupId = UUID.randomUUID().toString();

        AddPollOptionRequest request = new AddPollOptionRequest("New Option");

        Hangout hangout = new Hangout();
        hangout.setHangoutId(eventId);
        hangout.setAssociatedGroups(Arrays.asList(groupId));
        HangoutDetailData hangoutData = HangoutDetailData.builder().withHangout(hangout).build();

        Poll poll = new Poll(eventId, "Test Poll", "Description", false);
        PollOption option1 = new PollOption(eventId, pollId, "Option 1");
        PollOption option2 = new PollOption(eventId, pollId, "Option 2");
        PollOption option3 = new PollOption(eventId, pollId, "New Option");
        List<BaseItem> pollData = Arrays.asList(poll, option1, option2, option3);

        HangoutPointer pointer = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId)
            .forHangout(eventId)
            .build();

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(authorizationService.canUserEditHangout(userId, hangout)).thenReturn(true);
        when(hangoutRepository.savePollOption(any(PollOption.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(hangoutRepository.findHangoutById(eventId)).thenReturn(Optional.of(hangout));
        when(hangoutRepository.getAllPollData(eventId)).thenReturn(pollData);

        // When
        PollOption result = pollService.addPollOption(eventId, pollId, request, userId);

        // Then
        assertThat(result).isNotNull();
        verify(hangoutRepository).savePollOption(any(PollOption.class));
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId), eq(eventId), any(), eq("poll data"));
    }

    @Test
    void deletePollOption_WithValidOption_ShouldUpdateAllPointers() {
        // Given
        String groupId1 = UUID.randomUUID().toString();
        String groupId2 = UUID.randomUUID().toString();

        Hangout hangout = new Hangout();
        hangout.setAssociatedGroups(Arrays.asList(groupId1, groupId2));
        HangoutDetailData hangoutData = HangoutDetailData.builder().withHangout(hangout).build();

        Poll poll = new Poll(eventId, "Test Poll", "Description", false);
        PollOption optionToDelete = new PollOption(eventId, pollId, "Option to delete");
        optionToDelete.setOptionId(optionId);
        List<BaseItem> specificPollData = Arrays.asList(poll, optionToDelete);

        // After deletion
        PollOption option1 = new PollOption(eventId, pollId, "Option 1");
        PollOption option2 = new PollOption(eventId, pollId, "Option 2");
        List<BaseItem> allPollData = Arrays.asList(poll, option1, option2);

        HangoutPointer pointer1 = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId1)
            .forHangout(eventId)
            .build();
        HangoutPointer pointer2 = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId2)
            .forHangout(eventId)
            .build();

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(authorizationService.canUserEditHangout(userId, hangout)).thenReturn(true);
        when(hangoutRepository.getSpecificPollData(eventId, pollId)).thenReturn(specificPollData);
        when(hangoutRepository.findHangoutById(eventId)).thenReturn(Optional.of(hangout));
        when(hangoutRepository.getAllPollData(eventId)).thenReturn(allPollData);

        // When
        pollService.deletePollOption(eventId, pollId, optionId, userId);

        // Then
        verify(hangoutRepository).deletePollOptionTransaction(eventId, pollId, optionId);
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId1), eq(eventId), any(), eq("poll data"));
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId2), eq(eventId), any(), eq("poll data"));
    }

    // ============================================================================
    // PHASE 4: OPTIMISTIC LOCKING RETRY TESTS
    // ============================================================================

    @Nested
    class OptimisticLockingRetryTests {

        @Test
        void updatePointersWithPolls_WithNoConflict_ShouldSucceedOnFirstAttempt() {
            // Given
            String groupId1 = UUID.randomUUID().toString();
            String groupId2 = UUID.randomUUID().toString();

            CreatePollRequest request = new CreatePollRequest();
            request.setTitle("Test Poll");
            request.setDescription("Description");
            request.setMultipleChoice(false);
            request.setOptions(Arrays.asList("Option A", "Option B"));

            Hangout hangout = new Hangout();
            hangout.setHangoutId(eventId);
            hangout.setAssociatedGroups(Arrays.asList(groupId1, groupId2));
            HangoutDetailData hangoutData = HangoutDetailData.builder().withHangout(hangout).build();

            Poll newPoll = new Poll(eventId, "Test Poll", "Description", false);
            PollOption option1 = new PollOption(eventId, newPoll.getPollId(), "Option A");
            PollOption option2 = new PollOption(eventId, newPoll.getPollId(), "Option B");
            List<BaseItem> pollData = Arrays.asList(newPoll, option1, option2);

            HangoutPointer pointer1 = HangoutPointerTestBuilder.aPointer()
                .forGroup(groupId1)
                .forHangout(eventId)
                .build();
            HangoutPointer pointer2 = HangoutPointerTestBuilder.aPointer()
                .forGroup(groupId2)
                .forHangout(eventId)
                .build();

            when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
            when(authorizationService.canUserEditHangout(userId, hangout)).thenReturn(true);
            when(hangoutRepository.savePoll(any(Poll.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(hangoutRepository.savePollOption(any(PollOption.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(hangoutRepository.findHangoutById(eventId)).thenReturn(Optional.of(hangout));
            when(hangoutRepository.getAllPollData(eventId)).thenReturn(pollData);

            // When
            pollService.createPoll(eventId, request, userId);

            // Then - verify that PointerUpdateService is called for both groups
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId1), eq(eventId), any(), eq("poll data"));
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId2), eq(eventId), any(), eq("poll data"));

            // Note: Retry behavior is now tested in PointerUpdateServiceTest, not here.
        }

        @Test
        void updatePointersWithPolls_WithConflictOnSecondGroup_ShouldRetryOnlyThatGroup() {
            // Given
            String groupId1 = UUID.randomUUID().toString();
            String groupId2 = UUID.randomUUID().toString();

            CreatePollRequest request = new CreatePollRequest();
            request.setTitle("Test Poll");
            request.setDescription("Description");
            request.setMultipleChoice(false);
            request.setOptions(Arrays.asList("Option A", "Option B"));

            Hangout hangout = new Hangout();
            hangout.setHangoutId(eventId);
            hangout.setAssociatedGroups(Arrays.asList(groupId1, groupId2));
            HangoutDetailData hangoutData = HangoutDetailData.builder().withHangout(hangout).build();

            Poll newPoll = new Poll(eventId, "Test Poll", "Description", false);
            PollOption option1 = new PollOption(eventId, newPoll.getPollId(), "Option A");
            PollOption option2 = new PollOption(eventId, newPoll.getPollId(), "Option B");
            List<BaseItem> pollData = Arrays.asList(newPoll, option1, option2);

            HangoutPointer pointer1 = HangoutPointerTestBuilder.aPointer()
                .forGroup(groupId1)
                .forHangout(eventId)
                .build();
            HangoutPointer pointer2 = HangoutPointerTestBuilder.aPointer()
                .forGroup(groupId2)
                .forHangout(eventId)
                .build();

            when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
            when(authorizationService.canUserEditHangout(userId, hangout)).thenReturn(true);
            when(hangoutRepository.savePoll(any(Poll.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(hangoutRepository.savePollOption(any(PollOption.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(hangoutRepository.findHangoutById(eventId)).thenReturn(Optional.of(hangout));
            when(hangoutRepository.getAllPollData(eventId)).thenReturn(pollData);

            // When
            pollService.createPoll(eventId, request, userId);

            // Then - verify that PointerUpdateService is called for both groups
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId1), eq(eventId), any(), eq("poll data"));
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId2), eq(eventId), any(), eq("poll data"));

            // Note: Retry behavior with conflicts is now tested in PointerUpdateServiceTest.
        }

        @Test
        void updatePointersWithPolls_WithMaxRetriesExceeded_ShouldLogAndContinue() {
            // Given
            String groupId = UUID.randomUUID().toString();

            CreatePollRequest request = new CreatePollRequest();
            request.setTitle("Test Poll");
            request.setDescription("Description");
            request.setMultipleChoice(false);
            request.setOptions(Arrays.asList("Option A", "Option B"));

            Hangout hangout = new Hangout();
            hangout.setHangoutId(eventId);
            hangout.setAssociatedGroups(Arrays.asList(groupId));
            HangoutDetailData hangoutData = HangoutDetailData.builder().withHangout(hangout).build();

            Poll newPoll = new Poll(eventId, "Test Poll", "Description", false);
            PollOption option1 = new PollOption(eventId, newPoll.getPollId(), "Option A");
            PollOption option2 = new PollOption(eventId, newPoll.getPollId(), "Option B");
            List<BaseItem> pollData = Arrays.asList(newPoll, option1, option2);

            HangoutPointer pointer = HangoutPointerTestBuilder.aPointer()
                .forGroup(groupId)
                .forHangout(eventId)
                .build();

            when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
            when(authorizationService.canUserEditHangout(userId, hangout)).thenReturn(true);
            when(hangoutRepository.savePoll(any(Poll.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(hangoutRepository.savePollOption(any(PollOption.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(hangoutRepository.findHangoutById(eventId)).thenReturn(Optional.of(hangout));
            when(hangoutRepository.getAllPollData(eventId)).thenReturn(pollData);

            // When
            pollService.createPoll(eventId, request, userId);

            // Then - verify that PointerUpdateService is called
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId), eq(eventId), any(), eq("poll data"));

            // Note: Max retry behavior is now tested in PointerUpdateServiceTest.
        }
    }
}