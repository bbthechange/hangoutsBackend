package com.bbthechange.inviter.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RefreshTokenPair {
    private String accessToken;
    private String refreshToken;
}