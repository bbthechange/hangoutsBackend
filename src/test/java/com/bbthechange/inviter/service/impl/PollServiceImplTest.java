package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.exception.EventNotFoundException;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.service.AuthorizationService;
import com.bbthechange.inviter.service.PollService;
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

@ExtendWith(MockitoExtension.class)
class PollServiceImplTest {

    @Mock
    private HangoutRepository hangoutRepository;

    @Mock
    private AuthorizationService authorizationService;

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
        HangoutDetailData hangoutData = new HangoutDetailData(hangout, null, null, null, null, null, null);

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
        HangoutDetailData hangoutData = new HangoutDetailData(hangout, null, null,null, null, null, null);

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
        HangoutDetailData hangoutData = new HangoutDetailData(hangout, null, null, null, null, null, null);

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
        HangoutDetailData hangoutData = new HangoutDetailData(hangout, null, null, null, null, null, null);

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
        HangoutDetailData hangoutData = new HangoutDetailData(hangout, null, null, null, null, null, null);

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
        HangoutDetailData hangoutData = new HangoutDetailData(hangout, null, null, null, null, null, null);

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
        HangoutDetailData hangoutData = new HangoutDetailData(hangout, null, null, null, null, null, null);

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
        HangoutDetailData hangoutData = new HangoutDetailData(hangout, null, null, null, null, null, null);

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
        HangoutDetailData hangoutData = new HangoutDetailData(hangout, null, null, null, null, null, null);

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
    void removeVote_WithValidRequest_DeletesVote() {
        // Given
        Hangout hangout = new Hangout();
        // Hangout ID is handled differently - using eventId as string in tests
        HangoutDetailData hangoutData = new HangoutDetailData(hangout, null, null, null, null, null, null);

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
}