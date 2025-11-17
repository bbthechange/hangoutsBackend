package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.GroupDTO;
import com.bbthechange.inviter.dto.GroupPreviewDTO;
import com.bbthechange.inviter.dto.JoinGroupRequest;
import com.bbthechange.inviter.exception.ResourceNotFoundException;
import com.bbthechange.inviter.model.Group;
import com.bbthechange.inviter.model.GroupRole;
import com.bbthechange.inviter.service.GroupFeedService;
import com.bbthechange.inviter.service.GroupService;
import com.bbthechange.inviter.service.JwtService;
import com.bbthechange.inviter.service.RateLimitingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for GroupController invite code endpoints.
 * Uses @WebMvcTest to only load web layer for faster tests.
 */
@WebMvcTest(controllers = GroupController.class, excludeAutoConfiguration = {
    SecurityAutoConfiguration.class,
    SecurityFilterAutoConfiguration.class,
    UserDetailsServiceAutoConfiguration.class
})
class GroupControllerInviteCodeTest {

    private static final String GROUP_ID = "12345678-1234-1234-1234-123456789012";
    private static final String USER_ID = "87654321-4321-4321-4321-210987654321";
    private static final String INVITE_CODE = "abc123xy";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GroupService groupService;

    @MockitoBean
    private GroupFeedService groupFeedService;

    @MockitoBean
    private RateLimitingService rateLimitingService;

    @MockitoBean
    private JwtService jwtService;

    @Nested
    class GetGroupPreviewByInviteCodeTests {

        @Test
        void getGroupPreviewByInviteCode_WhenRateLimitAllows_ReturnsPreview() throws Exception {
            // Given
            GroupPreviewDTO preview = createPublicGroupPreview();
            when(rateLimitingService.isInvitePreviewAllowed(anyString(), eq(INVITE_CODE))).thenReturn(true);
            when(groupService.getGroupPreviewByInviteCode(INVITE_CODE)).thenReturn(preview);

            // When/Then
            mockMvc.perform(get("/groups/invite/" + INVITE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPrivate").value(false))
                .andExpect(jsonPath("$.groupName").value("Test Group"))
                .andExpect(jsonPath("$.mainImagePath").value("images/group.jpg"));

            verify(groupService).getGroupPreviewByInviteCode(INVITE_CODE);
        }

        @Test
        void getGroupPreviewByInviteCode_WhenRateLimitExceeded_Returns429() throws Exception {
            // Given
            when(rateLimitingService.isInvitePreviewAllowed(anyString(), eq(INVITE_CODE))).thenReturn(false);

            // When/Then
            mockMvc.perform(get("/groups/invite/" + INVITE_CODE))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Rate limit exceeded"))
                .andExpect(jsonPath("$.message").value("Too many requests. Please try again later."));

            verify(groupService, never()).getGroupPreviewByInviteCode(anyString());
        }

        @Test
        void getGroupPreviewByInviteCode_ExtractsIpFromXForwardedFor() throws Exception {
            // Given
            GroupPreviewDTO preview = createPublicGroupPreview();
            when(rateLimitingService.isInvitePreviewAllowed("1.2.3.4", INVITE_CODE)).thenReturn(true);
            when(groupService.getGroupPreviewByInviteCode(INVITE_CODE)).thenReturn(preview);

            // When/Then
            mockMvc.perform(get("/groups/invite/" + INVITE_CODE)
                    .header("X-Forwarded-For", "1.2.3.4, proxy1, proxy2"))
                .andExpect(status().isOk());

            verify(rateLimitingService).isInvitePreviewAllowed("1.2.3.4", INVITE_CODE);
        }

        @Test
        void getGroupPreviewByInviteCode_ExtractsIpFromRemoteAddrWhenNoXForwardedFor() throws Exception {
            // Given
            GroupPreviewDTO preview = createPublicGroupPreview();
            when(rateLimitingService.isInvitePreviewAllowed(anyString(), eq(INVITE_CODE))).thenReturn(true);
            when(groupService.getGroupPreviewByInviteCode(INVITE_CODE)).thenReturn(preview);

            // When/Then - MockMvc sets RemoteAddr automatically
            mockMvc.perform(get("/groups/invite/" + INVITE_CODE))
                .andExpect(status().isOk());

            verify(rateLimitingService).isInvitePreviewAllowed(anyString(), eq(INVITE_CODE));
        }

        @Test
        void getGroupPreviewByInviteCode_HandlesEmptyXForwardedFor() throws Exception {
            // Given
            GroupPreviewDTO preview = createPublicGroupPreview();
            when(rateLimitingService.isInvitePreviewAllowed(anyString(), eq(INVITE_CODE))).thenReturn(true);
            when(groupService.getGroupPreviewByInviteCode(INVITE_CODE)).thenReturn(preview);

            // When/Then
            mockMvc.perform(get("/groups/invite/" + INVITE_CODE)
                    .header("X-Forwarded-For", ""))
                .andExpect(status().isOk());

            verify(rateLimitingService).isInvitePreviewAllowed(anyString(), eq(INVITE_CODE));
        }

        @Test
        void getGroupPreviewByInviteCode_WhenInvalidCode_Returns404() throws Exception {
            // Given
            when(rateLimitingService.isInvitePreviewAllowed(anyString(), eq("invalid"))).thenReturn(true);
            when(groupService.getGroupPreviewByInviteCode("invalid"))
                .thenThrow(new ResourceNotFoundException("Invalid invite code"));

            // When/Then
            mockMvc.perform(get("/groups/invite/invalid"))
                .andExpect(status().isNotFound());
        }

        @Test
        void getGroupPreviewByInviteCode_ForPrivateGroup_ReturnsMinimalInfo() throws Exception {
            // Given
            GroupPreviewDTO preview = createPrivateGroupPreview();
            when(rateLimitingService.isInvitePreviewAllowed(anyString(), eq(INVITE_CODE))).thenReturn(true);
            when(groupService.getGroupPreviewByInviteCode(INVITE_CODE)).thenReturn(preview);

            // When/Then
            mockMvc.perform(get("/groups/invite/" + INVITE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPrivate").value(true))
                .andExpect(jsonPath("$.groupName").doesNotExist())
                .andExpect(jsonPath("$.mainImagePath").doesNotExist());
        }
    }

    @Nested
    class JoinGroupByInviteCodeTests {

        @Test
        void joinGroupByInviteCode_WithValidCode_ReturnsGroupDTO() throws Exception {
            // Given
            JoinGroupRequest request = new JoinGroupRequest(INVITE_CODE);
            GroupDTO groupDTO = createGroupDTO();
            when(groupService.joinGroupByInviteCode(INVITE_CODE, USER_ID)).thenReturn(groupDTO);

            // When/Then
            mockMvc.perform(post("/groups/invite/join")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").value(GROUP_ID))
                .andExpect(jsonPath("$.groupName").value("Test Group"))
                .andExpect(jsonPath("$.userRole").value("MEMBER"));

            verify(groupService).joinGroupByInviteCode(INVITE_CODE, USER_ID);
        }

        @Test
        void joinGroupByInviteCode_WithInvalidCode_Returns404() throws Exception {
            // Given
            JoinGroupRequest request = new JoinGroupRequest("invalid");
            when(groupService.joinGroupByInviteCode("invalid", USER_ID))
                .thenThrow(new ResourceNotFoundException("Invalid invite code"));

            // When/Then
            mockMvc.perform(post("/groups/invite/join")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound());
        }

        @Test
        void joinGroupByInviteCode_WithExpiredCode_Returns404() throws Exception {
            // Given
            JoinGroupRequest request = new JoinGroupRequest(INVITE_CODE);
            when(groupService.joinGroupByInviteCode(INVITE_CODE, USER_ID))
                .thenThrow(new ResourceNotFoundException("This invite code is no longer valid"));

            // When/Then
            mockMvc.perform(post("/groups/invite/join")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound());
        }

        @Test
        void joinGroupByInviteCode_WhenAlreadyMember_ReturnsGroupDTO() throws Exception {
            // Given
            JoinGroupRequest request = new JoinGroupRequest(INVITE_CODE);
            GroupDTO groupDTO = createGroupDTO();
            when(groupService.joinGroupByInviteCode(INVITE_CODE, USER_ID)).thenReturn(groupDTO);

            // When/Then
            mockMvc.perform(post("/groups/invite/join")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").value(GROUP_ID));
        }
    }

    // Helper methods
    private GroupPreviewDTO createPublicGroupPreview() {
        Group group = new Group("Test Group", true);
        group.setMainImagePath("images/group.jpg");
        return new GroupPreviewDTO(group);
    }

    private GroupPreviewDTO createPrivateGroupPreview() {
        Group group = new Group("Test Group", false);
        return new GroupPreviewDTO(group);
    }

    private GroupDTO createGroupDTO() {
        return new GroupDTO(
            GROUP_ID,
            "Test Group",
            GroupRole.MEMBER.toString(),
            Instant.now(),
            "images/group.jpg",
            "images/background.jpg",
            "images/user.jpg"
        );
    }
}
