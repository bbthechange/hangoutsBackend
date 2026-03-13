package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.CreateTimeSuggestionRequest;
import com.bbthechange.inviter.dto.TimeSuggestionDTO;
import com.bbthechange.inviter.service.TimeSuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for time suggestions on timeless hangouts.
 *
 * All endpoints are nested under:
 *   /groups/{groupId}/hangouts/{hangoutId}/time-suggestions
 */
@RestController
@RequestMapping("/groups/{groupId}/hangouts/{hangoutId}/time-suggestions")
@Validated
@Tag(name = "Time Suggestions", description = "Lightweight time polls for hangouts without a set time")
public class TimeSuggestionController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(TimeSuggestionController.class);

    private static final String UUID_PATTERN = "[0-9a-f-]{36}";

    private final TimeSuggestionService timeSuggestionService;

    @Autowired
    public TimeSuggestionController(TimeSuggestionService timeSuggestionService) {
        this.timeSuggestionService = timeSuggestionService;
    }

    /**
     * POST /groups/{groupId}/hangouts/{hangoutId}/time-suggestions
     * Create a new time suggestion.
     */
    @PostMapping
    @Operation(summary = "Suggest a time for a hangout that has no time set")
    public ResponseEntity<TimeSuggestionDTO> createSuggestion(
            @PathVariable @Pattern(regexp = UUID_PATTERN, message = "Invalid group ID format") String groupId,
            @PathVariable @Pattern(regexp = UUID_PATTERN, message = "Invalid hangout ID format") String hangoutId,
            @Valid @RequestBody CreateTimeSuggestionRequest request,
            HttpServletRequest httpRequest) {

        String userId = extractUserId(httpRequest);
        logger.info("User {} creating time suggestion for hangout {} in group {}", userId, hangoutId, groupId);

        TimeSuggestionDTO dto = timeSuggestionService.createSuggestion(groupId, hangoutId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * POST /groups/{groupId}/hangouts/{hangoutId}/time-suggestions/{id}/support
     * +1 a suggestion.
     */
    @PostMapping("/{suggestionId}/support")
    @Operation(summary = "+1 a time suggestion")
    public ResponseEntity<TimeSuggestionDTO> supportSuggestion(
            @PathVariable @Pattern(regexp = UUID_PATTERN, message = "Invalid group ID format") String groupId,
            @PathVariable @Pattern(regexp = UUID_PATTERN, message = "Invalid hangout ID format") String hangoutId,
            @PathVariable @Pattern(regexp = UUID_PATTERN, message = "Invalid suggestion ID format") String suggestionId,
            HttpServletRequest httpRequest) {

        String userId = extractUserId(httpRequest);
        logger.info("User {} supporting time suggestion {} for hangout {}", userId, suggestionId, hangoutId);

        TimeSuggestionDTO dto = timeSuggestionService.supportSuggestion(groupId, hangoutId, suggestionId, userId);
        return ResponseEntity.ok(dto);
    }

    /**
     * GET /groups/{groupId}/hangouts/{hangoutId}/time-suggestions
     * List all active time suggestions for a hangout.
     */
    @GetMapping
    @Operation(summary = "List active time suggestions for a hangout")
    public ResponseEntity<List<TimeSuggestionDTO>> listSuggestions(
            @PathVariable @Pattern(regexp = UUID_PATTERN, message = "Invalid group ID format") String groupId,
            @PathVariable @Pattern(regexp = UUID_PATTERN, message = "Invalid hangout ID format") String hangoutId,
            HttpServletRequest httpRequest) {

        String userId = extractUserId(httpRequest);
        List<TimeSuggestionDTO> suggestions = timeSuggestionService.listSuggestions(groupId, hangoutId, userId);
        return ResponseEntity.ok(suggestions);
    }
}
