package com.bbthechange.inviter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/hangouts")
@Tag(name = "Time Options", description = "Fuzzy time granularity options for hangouts")
public class TimeOptionsController {

    @GetMapping("/time-options")
    @Operation(summary = "Get fuzzy time options", 
               description = "Returns a list of available fuzzy time granularities for creating hangouts")
    public ResponseEntity<List<String>> getTimeOptions() {
        List<String> timeOptions = Arrays.asList(
            "exact", 
            "morning", 
            "afternoon", 
            "evening", 
            "night", 
            "day", 
            "weekend"
        );
        return new ResponseEntity<>(timeOptions, HttpStatus.OK);
    }
}