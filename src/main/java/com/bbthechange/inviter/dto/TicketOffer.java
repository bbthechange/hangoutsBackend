package com.bbthechange.inviter.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketOffer {
    private String name;
    private String url;
    private BigDecimal price;
    private String priceCurrency;
    private String availability;
}