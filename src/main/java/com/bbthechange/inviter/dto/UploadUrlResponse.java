package com.bbthechange.inviter.dto;

import lombok.Data;
import lombok.AllArgsConstructor;

@Data
@AllArgsConstructor
public class UploadUrlResponse {
    private String uploadUrl;
    private String key;
}