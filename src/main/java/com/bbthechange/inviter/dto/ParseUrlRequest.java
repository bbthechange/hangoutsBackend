package com.bbthechange.inviter.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

@Data
public class ParseUrlRequest {
    @NotBlank(message = "URL is required")
    @URL(protocol = "https", message = "URL must use HTTPS")
    private String url;
}