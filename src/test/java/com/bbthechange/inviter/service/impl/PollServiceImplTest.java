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
import com.bbthechange.inviter.service.UserService;
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

    @Mock
    private com.bbthechange.inviter.service.AttributeSuggestionService attributeSuggestionService;

    @Mock
    private UserService userService;

    @Mock
    private com.bbthechange.inviter.service.FuzzyTimeService fuzzyTimeService;

    @Mock
    private com.bbthechange.inviter.config.TimePollConfig timePollConfig;

    @Mock
    private com.bbthechange.inviter.service.TimePollService timePollService;

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
        request.setOptionsFromStrings(Arrays.asList("Option A", "Option B", "Option C"));

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

        Poll parentPoll = new Poll(eventId, "Existing", null, false);
        parentPoll.setPollId(pollId);

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(hangoutRepository.getSpecificPollData(eventId, pollId)).thenReturn(java.util.List.of(parentPoll));
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
        request.setOptionsFromStrings(Arrays.asList("Option A", "Option B", "Option C"));

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
        request.setOptionsFromStrings(Arrays.asList("Option A", "Option B"));

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
        when(hangoutRepository.getSpecificPollData(eventId, pollId)).thenReturn(java.util.List.of(poll));
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
            request.setOptionsFromStrings(Arrays.asList("Option A", "Option B"));

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
            request.setOptionsFromStrings(Arrays.asList("Option A", "Option B"));

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
            request.setOptionsFromStrings(Arrays.asList("Option A", "Option B"));

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

    // ========================================================================
    // TIME poll substrate tests (Slice 1)
    // ========================================================================

    @org.junit.jupiter.api.Nested
    class TimePollTests {
        private Hangout hangout;
        private HangoutDetailData hangoutData;

        @BeforeEach
        void setupTimePollHangout() {
            hangout = new Hangout();
            hangout.setHangoutId(eventId);
            hangoutData = HangoutDetailData.builder().withHangout(hangout).build();
            org.mockito.Mockito.lenient()
                .when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
            org.mockito.Mockito.lenient()
                .when(authorizationService.canUserEditHangout(userId, hangout)).thenReturn(true);
        }

        private CreatePollRequest timePollRequest(TimeInfo... timeInputs) {
            CreatePollRequest req = new CreatePollRequest();
            req.setTitle("Vote on a time");
            req.setAttributeType("TIME");
            req.setMultipleChoice(true);
            java.util.List<PollOptionInput> inputs = new java.util.ArrayList<>();
            for (TimeInfo ti : timeInputs) {
                inputs.add(new PollOptionInput(null, ti));
            }
            req.setOptions(inputs);
            return req;
        }

        private TimeInfo exactTime(String start, String end) {
            return new TimeInfo(null, null, start, end);
        }

        private TimeInfo fuzzyTime(String granularity, String periodStart) {
            return new TimeInfo(granularity, periodStart, null, null);
        }

        @Test
        void createPoll_TimeAttribute_ValidatesAndGeneratesText() {
            // Given: fresh hangout (no active TIME poll)
            when(hangoutRepository.getAllPollData(eventId)).thenReturn(java.util.List.of());
            when(hangoutRepository.findHangoutById(eventId)).thenReturn(Optional.of(hangout));
            when(hangoutRepository.savePoll(any(Poll.class))).thenAnswer(i -> i.getArgument(0));
            when(hangoutRepository.savePollOption(any(PollOption.class))).thenAnswer(i -> i.getArgument(0));

            CreatePollRequest req = timePollRequest(
                fuzzyTime("evening", "2026-05-02T00:00:00-07:00"));

            // When
            Poll result = pollService.createPoll(eventId, req, userId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getAttributeType()).isEqualTo("TIME");
            org.mockito.ArgumentCaptor<PollOption> captor = org.mockito.ArgumentCaptor.forClass(PollOption.class);
            verify(hangoutRepository).savePollOption(captor.capture());
            PollOption saved = captor.getValue();
            assertThat(saved.getTimeInput()).isNotNull();
            assertThat(saved.getText()).isNotBlank();
            assertThat(saved.getText()).doesNotContain("null");
        }

        @Test
        void createPoll_TimeAttribute_WithLegacyStringOptions_Rejects() {
            CreatePollRequest req = new CreatePollRequest();
            req.setTitle("Vote on a time");
            req.setAttributeType("TIME");
            req.setOptionsFromStrings(java.util.List.of("anything"));

            assertThatThrownBy(() -> pollService.createPoll(eventId, req, userId))
                .isInstanceOf(com.bbthechange.inviter.exception.ValidationException.class)
                .hasMessageContaining("updated app version");
        }

        @Test
        void createPoll_TimeAttribute_WhenActiveTimePollExists_Rejects() {
            Poll existingActive = new Poll(eventId, "other", null, false);
            existingActive.setAttributeType("TIME");
            existingActive.setActive(true);
            when(hangoutRepository.getAllPollData(eventId))
                .thenReturn(java.util.List.of(existingActive));

            CreatePollRequest req = timePollRequest(
                fuzzyTime("evening", "2026-05-02T00:00:00-07:00"));

            assertThatThrownBy(() -> pollService.createPoll(eventId, req, userId))
                .isInstanceOf(com.bbthechange.inviter.exception.ValidationException.class)
                .hasMessageContaining("active time poll");
        }

        @Test
        void addPollOption_OnTimePoll_WithoutTimeInput_Rejects() {
            Poll parent = new Poll(eventId, "t", null, true);
            parent.setAttributeType("TIME");
            parent.setPollId(pollId);
            when(hangoutRepository.getSpecificPollData(eventId, pollId))
                .thenReturn(java.util.List.of(parent));

            AddPollOptionRequest req = new AddPollOptionRequest("just text");
            assertThatThrownBy(() -> pollService.addPollOption(eventId, pollId, req, userId))
                .isInstanceOf(com.bbthechange.inviter.exception.ValidationException.class)
                .hasMessageContaining("updated app version");
        }

        @Test
        void addPollOption_OnTimePoll_DedupesEquivalentTimeInput() {
            Poll parent = new Poll(eventId, "t", null, true);
            parent.setAttributeType("TIME");
            parent.setPollId(pollId);

            PollOption existing = new PollOption(eventId, pollId, "Sat evening");
            TimeInfo existingTime = fuzzyTime("evening", "2026-05-02T00:00:00-07:00");
            existing.setTimeInput(existingTime);

            when(hangoutRepository.getSpecificPollData(eventId, pollId))
                .thenReturn(java.util.List.of(parent, existing));

            AddPollOptionRequest req = new AddPollOptionRequest();
            req.setTimeInput(fuzzyTime("evening", "2026-05-02T00:00:00-07:00"));

            assertThatThrownBy(() -> pollService.addPollOption(eventId, pollId, req, userId))
                .isInstanceOf(com.bbthechange.inviter.exception.ValidationException.class)
                .hasMessageContaining("Duplicate");
        }

        @Test
        void addPollOption_OnTimePoll_AcceptsNewEquivalent_WhenDifferent() {
            Poll parent = new Poll(eventId, "t", null, true);
            parent.setAttributeType("TIME");
            parent.setPollId(pollId);

            PollOption existing = new PollOption(eventId, pollId, "Sat evening");
            existing.setTimeInput(fuzzyTime("evening", "2026-05-02T00:00:00-07:00"));

            when(hangoutRepository.getSpecificPollData(eventId, pollId))
                .thenReturn(java.util.List.of(parent, existing));
            when(hangoutRepository.savePollOption(any(PollOption.class))).thenAnswer(i -> i.getArgument(0));
            when(hangoutRepository.findHangoutById(eventId)).thenReturn(Optional.of(hangout));

            AddPollOptionRequest req = new AddPollOptionRequest();
            // Different day — not a duplicate
            req.setTimeInput(fuzzyTime("evening", "2026-05-03T00:00:00-07:00"));

            PollOption result = pollService.addPollOption(eventId, pollId, req, userId);
            assertThat(result).isNotNull();
            assertThat(result.getTimeInput()).isNotNull();
        }

        @Test
        void voteOnPoll_SameOptionTwice_IsIdempotent() {
            VoteRequest request = new VoteRequest();
            request.setOptionId(optionId);
            request.setVoteType("YES");

            Hangout h = new Hangout();
            HangoutDetailData hd = HangoutDetailData.builder().withHangout(h).build();

            Poll poll = new Poll(eventId, "t", null, true);
            poll.setPollId(pollId);
            Vote existing = new Vote(eventId, pollId, optionId, userId, "YES");

            when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hd);
            when(authorizationService.canUserViewHangout(userId, h)).thenReturn(true);
            when(hangoutRepository.getSpecificPollData(eventId, pollId))
                .thenReturn(java.util.List.of(poll, existing));

            Vote result = pollService.voteOnPoll(eventId, pollId, request, userId);

            assertThat(result).isSameAs(existing);
            verify(hangoutRepository, never()).saveVote(any());
            verify(hangoutRepository, never()).deleteVote(any(), any(), any(), any());
        }
    }

    @Nested
    class GetPollDetailEmbeddedVotesGate {

        @org.junit.jupiter.api.AfterEach
        void clearRequestContext() {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        }

        private void bindClientInfoToRequest(com.bbthechange.inviter.config.ClientInfo clientInfo) {
            org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest();
            if (clientInfo != null) {
                request.setAttribute(com.bbthechange.inviter.config.ClientInfo.REQUEST_ATTRIBUTE, clientInfo);
            }
            org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(request));
        }

        private List<BaseItem> seedPollWithOneVote() {
            Hangout hangout = new Hangout();
            HangoutDetailData hangoutData = HangoutDetailData.builder().withHangout(hangout).build();
            when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
            when(authorizationService.canUserViewHangout(userId, hangout)).thenReturn(true);

            Poll poll = new Poll(eventId, "Test Poll", "Description", false);
            poll.setPollId(pollId);
            PollOption option = new PollOption(eventId, pollId, "Lasagna");
            option.setOptionId(optionId);
            String voterId = UUID.randomUUID().toString();
            Vote vote = new Vote(eventId, pollId, optionId, voterId, "YES");

            List<BaseItem> data = Arrays.asList(poll, option, vote);
            when(hangoutRepository.getSpecificPollData(eventId, pollId)).thenReturn(data);
            return data;
        }

        @Test
        void iosVersionInRange_StripsEmbeddedVotesAndSkipsDisplayNameLookup() {
            bindClientInfoToRequest(new com.bbthechange.inviter.config.ClientInfo(
                "2.2.0", null, "ios", null, null, "ios"));
            seedPollWithOneVote();

            PollDetailDTO result = pollService.getPollDetail(eventId, pollId, userId);

            assertThat(result.getOptions()).hasSize(1);
            PollOptionDetailDTO optDto = result.getOptions().get(0);
            assertThat(optDto.getVoteCount()).isEqualTo(1);
            assertThat(optDto.getVotes()).isEmpty();

            // Skipping the votes loop also skips the per-vote displayName enrichment.
            verify(userService, never()).getUserSummary(any(UUID.class));
        }

        @Test
        void iosOutOfRange_PopulatesEmbeddedVotesAndEnrichesDisplayName() {
            bindClientInfoToRequest(new com.bbthechange.inviter.config.ClientInfo(
                "2.3.0", null, "ios", null, null, "ios"));
            List<BaseItem> data = seedPollWithOneVote();
            Vote vote = (Vote) data.get(2);

            UserSummaryDTO summary = new UserSummaryDTO();
            summary.setDisplayName("Alice");
            when(userService.getUserSummary(UUID.fromString(vote.getUserId())))
                .thenReturn(Optional.of(summary));

            PollDetailDTO result = pollService.getPollDetail(eventId, pollId, userId);

            PollOptionDetailDTO optDto = result.getOptions().get(0);
            assertThat(optDto.getVotes()).hasSize(1);
            assertThat(optDto.getVotes().get(0).getDisplayName()).isEqualTo("Alice");
            verify(userService).getUserSummary(UUID.fromString(vote.getUserId()));
        }

        @Test
        void androidInVersionRange_DoesNotGate() {
            bindClientInfoToRequest(new com.bbthechange.inviter.config.ClientInfo(
                "2.2.0", null, "android", null, null, "android"));
            seedPollWithOneVote();
            when(userService.getUserSummary(any(UUID.class))).thenReturn(Optional.empty());

            PollDetailDTO result = pollService.getPollDetail(eventId, pollId, userId);

            assertThat(result.getOptions().get(0).getVotes()).hasSize(1);
        }

        @Test
        void noRequestContext_DoesNotGate() {
            // currentClientInfo() returns null when there's no bound request — fail-open.
            seedPollWithOneVote();
            when(userService.getUserSummary(any(UUID.class))).thenReturn(Optional.empty());

            PollDetailDTO result = pollService.getPollDetail(eventId, pollId, userId);

            assertThat(result.getOptions().get(0).getVotes()).hasSize(1);
        }
    }
}