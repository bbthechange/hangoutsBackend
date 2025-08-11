package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.service.PollService;
import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.model.Poll;
import com.bbthechange.inviter.model.PollOption;
import com.bbthechange.inviter.model.Vote;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PollController.class)
class PollControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PollService pollService;

    private String hangoutId;
    private String pollId;
    private String userId;
    private String validJWT;

    @BeforeEach
    void setUp() {
        hangoutId = UUID.randomUUID().toString();
        pollId = UUID.randomUUID().toString();
        userId = UUID.randomUUID().toString();
        validJWT = "valid.jwt.token";
    }

    @Test
    void createPoll_WithValidRequest_ReturnsCreatedPoll() throws Exception {
        // Given
        CreatePollRequest request = new CreatePollRequest();
        request.setTitle("Test Poll");
        request.setDescription("Test Description");
        request.setMultipleChoice(false);
        request.setOptions(Arrays.asList("Option 1", "Option 2"));

        Poll mockPoll = new Poll(hangoutId, "Test Poll", "Test Description", false);
        when(pollService.createPoll(eq(hangoutId), any(CreatePollRequest.class), anyString()))
                .thenReturn(mockPoll);

        // When & Then
        mockMvc.perform(post("/hangouts/{hangoutId}/polls", hangoutId)
                .header("Authorization", "Bearer " + validJWT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Test Poll"))
                .andExpect(jsonPath("$.description").value("Test Description"));

        verify(pollService).createPoll(eq(hangoutId), any(CreatePollRequest.class), anyString());
    }

    @Test
    void getHangoutPolls_WithValidHangoutId_ReturnsListOfPolls() throws Exception {
        // Given
        PollWithOptionsDTO poll1 = new PollWithOptionsDTO();
        PollWithOptionsDTO poll2 = new PollWithOptionsDTO();
        List<PollWithOptionsDTO> polls = Arrays.asList(poll1, poll2);

        when(pollService.getEventPolls(eq(hangoutId), anyString())).thenReturn(polls);

        // When & Then
        mockMvc.perform(get("/hangouts/{hangoutId}/polls", hangoutId)
                .header("Authorization", "Bearer " + validJWT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        verify(pollService).getEventPolls(eq(hangoutId), anyString());
    }

    @Test
    void getPollDetail_WithValidIds_ReturnsPollDetail() throws Exception {
        // Given
        PollDetailDTO pollDetail = new PollDetailDTO();
        when(pollService.getPollDetail(eq(hangoutId), eq(pollId), anyString()))
                .thenReturn(pollDetail);

        // When & Then
        mockMvc.perform(get("/hangouts/{hangoutId}/polls/{pollId}", hangoutId, pollId)
                .header("Authorization", "Bearer " + validJWT))
                .andExpect(status().isOk());

        verify(pollService).getPollDetail(eq(hangoutId), eq(pollId), anyString());
    }

    @Test
    void voteOnPoll_WithValidRequest_ReturnsVote() throws Exception {
        // Given
        String optionId = UUID.randomUUID().toString();
        VoteRequest request = new VoteRequest();
        request.setOptionId(optionId);
        request.setVoteType("YES");

        Vote mockVote = new Vote(hangoutId, pollId, optionId, userId, "YES");
        when(pollService.voteOnPoll(eq(hangoutId), eq(pollId), any(VoteRequest.class), anyString()))
                .thenReturn(mockVote);

        // When & Then
        mockMvc.perform(post("/hangouts/{hangoutId}/polls/{pollId}/vote", hangoutId, pollId)
                .header("Authorization", "Bearer " + validJWT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voteType").value("YES"));

        verify(pollService).voteOnPoll(eq(hangoutId), eq(pollId), any(VoteRequest.class), anyString());
    }

    @Test
    void addPollOption_AsHost_ReturnsCreatedOption() throws Exception {
        // Given
        String optionId = UUID.randomUUID().toString();
        AddPollOptionRequest request = new AddPollOptionRequest();
        request.setText("New Option");

        PollOption mockOption = new PollOption(hangoutId, pollId, "New Option");
        when(pollService.addPollOption(eq(hangoutId), eq(pollId), any(AddPollOptionRequest.class), anyString()))
                .thenReturn(mockOption);

        // When & Then
        mockMvc.perform(post("/hangouts/{hangoutId}/polls/{pollId}/options", hangoutId, pollId)
                .header("Authorization", "Bearer " + validJWT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.text").value("New Option"));

        verify(pollService).addPollOption(eq(hangoutId), eq(pollId), any(AddPollOptionRequest.class), anyString());
    }

    @Test
    void deletePollOption_AsHost_ReturnsNoContent() throws Exception {
        // Given
        String optionId = UUID.randomUUID().toString();
        doNothing().when(pollService).deletePollOption(eq(hangoutId), eq(pollId), eq(optionId), anyString());

        // When & Then
        mockMvc.perform(delete("/hangouts/{hangoutId}/polls/{pollId}/options/{optionId}", 
                hangoutId, pollId, optionId)
                .header("Authorization", "Bearer " + validJWT))
                .andExpect(status().isNoContent());

        verify(pollService).deletePollOption(eq(hangoutId), eq(pollId), eq(optionId), anyString());
    }

    @Test
    void removeVote_WithValidRequest_ReturnsNoContent() throws Exception {
        // Given
        doNothing().when(pollService).removeVote(eq(hangoutId), eq(pollId), isNull(), anyString());

        // When & Then
        mockMvc.perform(delete("/hangouts/{hangoutId}/polls/{pollId}/vote", hangoutId, pollId)
                .header("Authorization", "Bearer " + validJWT))
                .andExpect(status().isNoContent());

        verify(pollService).removeVote(eq(hangoutId), eq(pollId), isNull(), anyString());
    }

    @Test
    void deletePoll_AsHost_ReturnsNoContent() throws Exception {
        // Given
        doNothing().when(pollService).deletePoll(eq(hangoutId), eq(pollId), anyString());

        // When & Then
        mockMvc.perform(delete("/hangouts/{hangoutId}/polls/{pollId}", hangoutId, pollId)
                .header("Authorization", "Bearer " + validJWT))
                .andExpect(status().isNoContent());

        verify(pollService).deletePoll(eq(hangoutId), eq(pollId), anyString());
    }
}