package com.bbthechange.inviter.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO containing separate lists of user and group places.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlacesResponse {

    private List<PlaceDto> userPlaces;
    private List<PlaceDto> groupPlaces;
}
