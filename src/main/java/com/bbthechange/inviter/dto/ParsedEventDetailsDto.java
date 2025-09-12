package com.bbthechange.inviter.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedEventDetailsDto {
    private String title;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Address location;
    private String imageUrl;
    private String url;
    private String sourceUrl;
    private List<TicketOffer> ticketOffers;
}