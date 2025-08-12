package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.*;
import lombok.Data;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class HangoutDetailDTO {
    private Hangout hangout;
    private List<HangoutAttributeDTO> attributes;
    private List<PollWithOptionsDTO> polls;
    private List<Car> cars;
    private List<Vote> votes;
    private List<InterestLevel> attendance;
    private List<CarRider> carRiders;
    private List<NeedsRideDTO> needsRide;
}