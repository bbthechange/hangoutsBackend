package com.bbthechange.inviter.dto;

import lombok.Data;

@Data
public class DeviceRegistrationRequest {
    private String token;
    private String platform;
}