package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.ParseUrlRequest;
import com.bbthechange.inviter.dto.ParsedEventDetailsDto;
import com.bbthechange.inviter.service.ExternalEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/external")
@Tag(name = "External Event Parser", description = "Parse event details from external URLs")
public class ExternalController {

    private static final Logger logger = LoggerFactory.getLogger(ExternalController.class);

    private final ExternalEventService externalEventService;

    public ExternalController(ExternalEventService externalEventService) {
        this.externalEventService = externalEventService;
    }

    @PostMapping("/parse")
    @Operation(summary = "Parse event details from URL", 
               description = "Extracts event information from schema.org structured data on external websites")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully parsed event details"),
        @ApiResponse(responseCode = "400", description = "Invalid or unsafe URL"),
        @ApiResponse(responseCode = "404", description = "No schema.org event data found"),
        @ApiResponse(responseCode = "422", description = "Unable to parse event data"),
        @ApiResponse(responseCode = "503", description = "Network error accessing URL")
    })
    public ResponseEntity<ParsedEventDetailsDto> parseEventDetails(@Valid @RequestBody ParseUrlRequest request) {
        logger.info("Received parse request for URL: {}", request.getUrl());
        
        ParsedEventDetailsDto eventDetails = externalEventService.parseUrl(request.getUrl());
        
        logger.info("Successfully parsed event: {}", eventDetails.getTitle());
        return ResponseEntity.ok(eventDetails);
    }
}