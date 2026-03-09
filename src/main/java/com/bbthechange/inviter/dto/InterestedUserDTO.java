package com.bbthechange.inviter.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterestedUserDTO {
    private String userId;
    private String displayName;
    private String profileImagePath;
}
