package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.service.PollService;
import com.bbthechange.inviter.service.JwtService;
import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.model.Poll;
import com.bbthechange.inviter.model.PollOption;
import com.bbthechange.inviter.model.Vote;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PollController.class, excludeAutoConfiguration = {
    SecurityAutoConfiguration.class,
    SecurityFilterAutoConfiguration.class,
    UserDetailsServiceAutoConfiguration.class
})
@TestPropertySource(locations = "classpath:application-test.properties")
@ActiveProfiles("test")
class PollControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PollService pollService;
    
    @MockitoBean 
    private JwtService jwtService;

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
        when(pollService.createPoll(eq(hangoutId), any(CreatePollRequest.class), eq(userId)))
                .thenReturn(mockPoll);

        // When & Then
        mockMvc.perform(post("/hangouts/{hangoutId}/polls", hangoutId)
                .header("Authorization", "Bearer " + validJWT)
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Test Poll"))
                .andExpect(jsonPath("$.description").value("Test Description"));

        verify(pollService).createPoll(eq(hangoutId), any(CreatePollRequest.class), eq(userId));
    }

    @Test
    void getHangoutPolls_WithValidHangoutId_ReturnsListOfPolls() throws Exception {
        // Given
        PollWithOptionsDTO poll1 = new PollWithOptionsDTO();
        PollWithOptionsDTO poll2 = new PollWithOptionsDTO();
        List<PollWithOptionsDTO> polls = Arrays.asList(poll1, poll2);

        when(pollService.getEventPolls(eq(hangoutId), eq(userId))).thenReturn(polls);

        // When & Then
        mockMvc.perform(get("/hangouts/{hangoutId}/polls", hangoutId)
                .header("Authorization", "Bearer " + validJWT)
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        verify(pollService).getEventPolls(eq(hangoutId), eq(userId));
    }

    @Test
    void getPoll_WithValidIds_ReturnsSpecificPoll() throws Exception {
        // Given
        PollDetailDTO pollDetail = new PollDetailDTO();
        pollDetail.setPollId(pollId);
        pollDetail.setTitle("Test Poll");
        pollDetail.setDescription("Test Description");

        when(pollService.getPollDetail(eq(hangoutId), eq(pollId), eq(userId))).thenReturn(pollDetail);

        // When & Then
        mockMvc.perform(get("/hangouts/{hangoutId}/polls/{pollId}", hangoutId, pollId)
                .header("Authorization", "Bearer " + validJWT)
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pollId").value(pollId))
                .andExpect(jsonPath("$.title").value("Test Poll"));

        verify(pollService).getPollDetail(eq(hangoutId), eq(pollId), eq(userId));
    }

    @Test
    void voteOnPoll_WithValidRequest_ReturnsVote() throws Exception {
        // Given
        String optionId = UUID.randomUUID().toString();
        VoteRequest request = new VoteRequest();
        request.setOptionId(optionId);

        Vote mockVote = new Vote(hangoutId, pollId, optionId, userId, "YES");
        when(pollService.voteOnPoll(eq(hangoutId), eq(pollId), any(VoteRequest.class), eq(userId)))
                .thenReturn(mockVote);

        // When & Then
        mockMvc.perform(post("/hangouts/{hangoutId}/polls/{pollId}/vote", hangoutId, pollId)
                .header("Authorization", "Bearer " + validJWT)
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voteType").value("YES"));

        verify(pollService).voteOnPoll(eq(hangoutId), eq(pollId), any(VoteRequest.class), eq(userId));
    }

    @Test
    void removeVote_WithValidIds_ReturnsNoContent() throws Exception {
        // Given
        String optionId = "option-123";
        doNothing().when(pollService).removeVote(eq(hangoutId), eq(pollId), eq(optionId), eq(userId));

        // When & Then
        mockMvc.perform(delete("/hangouts/{hangoutId}/polls/{pollId}/vote", hangoutId, pollId)
                .header("Authorization", "Bearer " + validJWT)
                .requestAttr("userId", userId)
                .param("optionId", optionId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(pollService).removeVote(eq(hangoutId), eq(pollId), eq(optionId), eq(userId));
    }

    @Test
    void deletePoll_WithValidIds_ReturnsNoContent() throws Exception {
        // Given
        doNothing().when(pollService).deletePoll(eq(hangoutId), eq(pollId), eq(userId));

        // When & Then
        mockMvc.perform(delete("/hangouts/{hangoutId}/polls/{pollId}", hangoutId, pollId)
                .header("Authorization", "Bearer " + validJWT)
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(pollService).deletePoll(eq(hangoutId), eq(pollId), eq(userId));
    }

    @Test
    void addPollOption_WithValidRequest_ReturnsCreatedOption() throws Exception {
        // Given
        AddPollOptionRequest request = new AddPollOptionRequest();
        request.setText("New Option");

        PollOption mockOption = new PollOption(hangoutId, pollId, "New Option");
        when(pollService.addPollOption(eq(hangoutId), eq(pollId), any(AddPollOptionRequest.class), eq(userId)))
                .thenReturn(mockOption);

        // When & Then
        mockMvc.perform(post("/hangouts/{hangoutId}/polls/{pollId}/options", hangoutId, pollId)
                .header("Authorization", "Bearer " + validJWT)
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.text").value("New Option"));

        verify(pollService).addPollOption(eq(hangoutId), eq(pollId), any(AddPollOptionRequest.class), eq(userId));
    }

    @Test
    void deletePollOption_WithValidIds_ReturnsNoContent() throws Exception {
        // Given
        String optionId = UUID.randomUUID().toString();
        doNothing().when(pollService).deletePollOption(eq(hangoutId), eq(pollId), eq(optionId), eq(userId));

        // When & Then
        mockMvc.perform(delete("/hangouts/{hangoutId}/polls/{pollId}/options/{optionId}", hangoutId, pollId, optionId)
                .header("Authorization", "Bearer " + validJWT)
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(pollService).deletePollOption(eq(hangoutId), eq(pollId), eq(optionId), eq(userId));
    }
}

