package com.bbthechange.inviter.util;

import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class for transforming denormalized hangout data into nested DTOs.
 * Provides reusable transformation logic for polls, carpool data, and needs ride data.
 */
public class HangoutDataTransformer {

    private HangoutDataTransformer() {
        // Private constructor to prevent instantiation
    }

    /**
     * Transform raw poll data into nested PollWithOptionsDTO objects.
     * Calculates vote counts at runtime and includes user-specific voting status.
     *
     * @param polls List of poll objects
     * @param allOptions List of all poll options across all polls
     * @param allVotes List of all votes across all polls
     * @param requestingUserId User ID to check if they voted on each option
     * @return List of polls with nested options and vote counts
     */
    public static List<PollWithOptionsDTO> transformPollData(
            List<Poll> polls,
            List<PollOption> allOptions,
            List<Vote> allVotes,
            String requestingUserId) {

        // Group options and votes by poll ID
        Map<String, List<PollOption>> optionsByPoll = allOptions.stream()
                .collect(Collectors.groupingBy(PollOption::getPollId));

        Map<String, List<Vote>> votesByPoll = allVotes.stream()
                .collect(Collectors.groupingBy(Vote::getPollId));

        // Build hierarchical DTOs with runtime vote counting
        return polls.stream()
                .map(poll -> {
                    List<PollOption> options = optionsByPoll.getOrDefault(poll.getPollId(), List.of());
                    List<Vote> pollVotes = votesByPoll.getOrDefault(poll.getPollId(), List.of());

                    // Calculate vote counts by option at runtime
                    Map<String, Long> voteCountsByOption = pollVotes.stream()
                            .collect(Collectors.groupingBy(Vote::getOptionId, Collectors.counting()));

                    List<PollOptionDTO> optionDTOs = options.stream()
                            .map(option -> {
                                // Runtime calculation - no denormalized count field needed
                                int voteCount = voteCountsByOption.getOrDefault(option.getOptionId(), 0L).intValue();

                                boolean userVoted = pollVotes.stream()
                                        .anyMatch(vote -> vote.getOptionId().equals(option.getOptionId())
                                                && vote.getUserId().equals(requestingUserId));

                                return new PollOptionDTO(option.getOptionId(), option.getText(),
                                        voteCount, userVoted);
                            })
                            .collect(Collectors.toList());

                    // Total votes = sum of all votes for this poll
                    int totalVotes = pollVotes.size();

                    return new PollWithOptionsDTO(poll, optionDTOs, totalVotes);
                })
                .collect(Collectors.toList());
    }

    /**
     * Transform raw carpool data into nested CarWithRidersDTO objects.
     * Groups riders by their assigned car (driver ID).
     *
     * @param cars List of car objects
     * @param allRiders List of all riders across all cars
     * @return List of cars with nested riders
     */
    public static List<CarWithRidersDTO> transformCarpoolData(
            List<Car> cars,
            List<CarRider> allRiders) {

        // Group riders by driver ID (cars are identified by their driver)
        Map<String, List<CarRider>> ridersByCar = allRiders.stream()
                .collect(Collectors.groupingBy(CarRider::getDriverId));

        // Build hierarchical DTOs
        return cars.stream()
                .map(car -> {
                    List<CarRider> carRiders = ridersByCar.getOrDefault(car.getDriverId(), List.of());
                    return new CarWithRidersDTO(car, carRiders);
                })
                .collect(Collectors.toList());
    }

    /**
     * Transform NeedsRide entities into NeedsRideDTO objects.
     *
     * @param needsRideList List of NeedsRide entities
     * @return List of NeedsRideDTO objects
     */
    public static List<NeedsRideDTO> transformNeedsRideData(List<NeedsRide> needsRideList) {
        return needsRideList.stream()
                .map(NeedsRideDTO::new)
                .collect(Collectors.toList());
    }

    /**
     * Transform attendance data for backward compatibility with older app versions.
     *
     * Older app versions only understand "INTERESTED" status. When a user sets "GOING",
     * old clients won't show them in the participant list. This method adds a synthetic
     * "INTERESTED" entry for each "GOING" entry so old clients still display the user.
     *
     * New clients can deduplicate by userId or display based on the more specific status.
     *
     * @param attendance List of InterestLevel entries
     * @param enabled Whether backward compatibility is enabled (for easy rollback)
     * @return Transformed list with synthetic INTERESTED entries for GOING users
     */
    public static List<InterestLevel> transformAttendanceForBackwardCompatibility(
            List<InterestLevel> attendance, boolean enabled) {

        if (!enabled || attendance == null || attendance.isEmpty()) {
            return attendance;
        }

        List<InterestLevel> result = new ArrayList<>(attendance);

        for (InterestLevel level : attendance) {
            if ("GOING".equals(level.getStatus())) {
                InterestLevel syntheticInterested = new InterestLevel();
                syntheticInterested.setEventId(level.getEventId());
                syntheticInterested.setUserId(level.getUserId());
                syntheticInterested.setUserName(level.getUserName());
                syntheticInterested.setStatus("INTERESTED");
                syntheticInterested.setMainImagePath(level.getMainImagePath());
                syntheticInterested.setNotes(level.getNotes());
                result.add(syntheticInterested);
            }
        }

        return result;
    }
}
