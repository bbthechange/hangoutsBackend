package com.bbthechange.inviter.dto;

import lombok.Data;
import lombok.AllArgsConstructor;

@Data
@AllArgsConstructor
public class PredefinedImageResponse {
    private String key;
    private String path;
    private String displayName;
}