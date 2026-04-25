package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.config.ClientInfo;
import com.bbthechange.inviter.config.TimePollConfig;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.service.FuzzyTimeService;
import com.bbthechange.inviter.service.PollService;
import com.bbthechange.inviter.service.AuthorizationService;
import com.bbthechange.inviter.service.GroupTimestampService;
import com.bbthechange.inviter.service.TimePollService;
import com.bbthechange.inviter.service.UserService;
import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.exception.*;
import com.bbthechange.inviter.util.InviterKeyFactory;
import com.bbthechange.inviter.util.TimePollOptionTextGenerator;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of PollService for poll management within events.
 */
@Service
public class PollServiceImpl implements PollService {

    private static final Logger logger = LoggerFactory.getLogger(PollServiceImpl.class);

    private static final String TIME_OPTION_REQUIRES_UPDATE_MSG =
        "Adding time options requires an updated app version.";

    private final HangoutRepository hangoutRepository;
    private final GroupRepository groupRepository;
    private final AuthorizationService authorizationService;
    private final PointerUpdateService pointerUpdateService;
    private final GroupTimestampService groupTimestampService;
    private final com.bbthechange.inviter.service.AttributeSuggestionService attributeSuggestionService;
    private final UserService userService;
    private final FuzzyTimeService fuzzyTimeService;
    private final TimePollConfig timePollConfig;
    private final TimePollService timePollService;

    @Autowired
    public PollServiceImpl(HangoutRepository hangoutRepository, GroupRepository groupRepository,
                          AuthorizationService authorizationService,
                          PointerUpdateService pointerUpdateService,
                          GroupTimestampService groupTimestampService,
                          @org.springframework.context.annotation.Lazy
                          com.bbthechange.inviter.service.AttributeSuggestionService attributeSuggestionService,
                          UserService userService,
                          FuzzyTimeService fuzzyTimeService,
                          TimePollConfig timePollConfig,
                          TimePollService timePollService) {
        this.hangoutRepository = hangoutRepository;
        this.groupRepository = groupRepository;
        this.authorizationService = authorizationService;
        this.pointerUpdateService = pointerUpdateService;
        this.groupTimestampService = groupTimestampService;
        this.attributeSuggestionService = attributeSuggestionService;
        this.userService = userService;
        this.fuzzyTimeService = fuzzyTimeService;
        this.timePollConfig = timePollConfig;
        this.timePollService = timePollService;
    }

    @Override
    public Poll createPoll(String eventId, CreatePollRequest request, String userId) {
        logger.info("Creating poll '{}' for event {} by user {}", request.getTitle(), eventId, userId);

        // Get event and verify user can edit it
        HangoutDetailData eventData = hangoutRepository.getHangoutDetailData(eventId);
        if (eventData.getHangout() == null) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }

        Hangout hangout = eventData.getHangout();
        if (!authorizationService.canUserEditHangout(userId, hangout)) {
            throw new UnauthorizedException("User cannot edit hangout");
        }

        // Create poll
        Poll poll = new Poll(eventId, request.getTitle(), request.getDescription(), request.isMultipleChoice());

        // If this is a suggestion poll, validate, set attributeType, and force single-choice
        String attrType = request.getAttributeType();
        if (attrType != null) {
            if (!"LOCATION".equals(attrType) && !"DESCRIPTION".equals(attrType) && !"TIME".equals(attrType)) {
                throw new ValidationException(
                    "Invalid attributeType: " + attrType + ". Must be LOCATION, DESCRIPTION, or TIME.");
            }
            poll.setAttributeType(attrType);
            poll.setMultipleChoice(false);
        }

        boolean isTimePoll = "TIME".equals(attrType);
        List<PollOptionInput> inputs = request.getOptions();

        if (isTimePoll) {
            // Single-active-TIME-poll invariant. Query-before-save. Slice 2 will add a sentinel
            // marker + EventBridge coordination to close the remaining race window; within Slice
            // 1 the check is best-effort but sufficient for UI flows where a single client
            // creates one TIME poll at a time.
            if (hasActiveTimePoll(eventId)) {
                throw new ValidationException("Hangout already has an active time poll");
            }

            // TIME polls must carry at least one option with a valid timeInput.
            if (inputs == null || inputs.isEmpty()) {
                throw new ValidationException(TIME_OPTION_REQUIRES_UPDATE_MSG);
            }
            for (PollOptionInput input : inputs) {
                if (input == null || input.getTimeInput() == null) {
                    throw new ValidationException(TIME_OPTION_REQUIRES_UPDATE_MSG);
                }
                // Validate timeInput structure (throws ValidationException on bad shape).
                fuzzyTimeService.convert(input.getTimeInput());
            }
        } else {
            // LOCATION / DESCRIPTION / plain polls: text-only options (legacy shape).
            // Reject any timeInput-carrying option to avoid silent data on non-TIME polls.
            if (inputs != null) {
                for (PollOptionInput input : inputs) {
                    if (input != null && input.getTimeInput() != null && input.getText() == null) {
                        throw new ValidationException(
                            "timeInput is only valid on TIME polls");
                    }
                }
            }

            // Supersede existing active suggestion polls of the same type (with pointer update)
            if (attrType != null) {
                attributeSuggestionService.supersedeSuggestionPolls(eventId, attrType);
            }
        }

        Poll savedPoll = hangoutRepository.savePoll(poll);

        // Create poll options
        if (inputs != null && !inputs.isEmpty()) {
            for (PollOptionInput input : inputs) {
                PollOption option = buildPollOption(eventId, poll.getPollId(), input, isTimePoll);
                option.setCreatedBy(userId);
                hangoutRepository.savePollOption(option);
            }
        }

        // Update pointer records with new poll data
        updatePointersWithPolls(eventId);

        // Kick off the TIME-poll EventBridge schedules (24h + 48h).
        if (isTimePoll) {
            timePollService.onPollCreated(savedPoll);
        }

        int optionCount = (inputs != null) ? inputs.size() : 0;
        logger.info("Successfully created poll {} for event {} with {} options", savedPoll.getPollId(), eventId, optionCount);

        return savedPoll;
    }

    private PollOption buildPollOption(String eventId, String pollId, PollOptionInput input, boolean isTimePoll) {
        String text;
        if (isTimePoll) {
            text = TimePollOptionTextGenerator.generate(input.getTimeInput());
        } else {
            text = input.getText();
            if (text == null || text.trim().isEmpty()) {
                throw new ValidationException("Option text is required");
            }
            text = text.trim();
        }
        PollOption option = new PollOption(eventId, pollId, text);
        if (isTimePoll) {
            option.setTimeInput(input.getTimeInput());
        }
        return option;
    }

    private boolean hasActiveTimePoll(String eventId) {
        List<BaseItem> pollData = hangoutRepository.getAllPollData(eventId);
        return pollData.stream()
            .filter(item -> InviterKeyFactory.isPollItem(item.getSk()))
            .map(item -> (Poll) item)
            .anyMatch(p -> p.isActive() && "TIME".equals(p.getAttributeType()) && p.getPromotedAt() == null);
    }

    @Override
    public List<PollWithOptionsDTO> getEventPolls(String eventId, String userId) {
        logger.debug("Getting polls for event {} for user {}", eventId, userId);

        // Get hangout and verify user can view it
        HangoutDetailData hangoutData = hangoutRepository.getHangoutDetailData(eventId);
        if (hangoutData.getHangout() == null) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }

        Hangout hangout = hangoutData.getHangout();
        if (!authorizationService.canUserViewHangout(userId, hangout)) {
            throw new UnauthorizedException("Cannot view event polls");
        }

        // Get all poll data in one query using item collection pattern
        List<BaseItem> allPollData = hangoutRepository.getAllPollData(eventId);

        return transformToPollWithOptionsDTO(allPollData, userId);
    }

    @Override
    public PollDetailDTO getPollDetail(String eventId, String pollId, String userId) {
        logger.debug("Getting poll detail for poll {} in event {} for user {}", pollId, eventId, userId);

        // Get hangout and verify user can view it
        HangoutDetailData hangoutData = hangoutRepository.getHangoutDetailData(eventId);
        if (hangoutData.getHangout() == null) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }

        Hangout hangout = hangoutData.getHangout();
        if (!authorizationService.canUserViewHangout(userId, hangout)) {
            throw new UnauthorizedException("Cannot view event polls");
        }

        // Get specific poll data in one query
        List<BaseItem> specificPollData = hangoutRepository.getSpecificPollData(eventId, pollId);

        return transformToPollDetailDTO(specificPollData, userId);
    }

    @Override
    public Vote voteOnPoll(String eventId, String pollId, VoteRequest request, String userId) {
        logger.info("User {} voting on poll {} option {}", userId, pollId, request.getOptionId());

        // Get hangout and verify user can view it
        HangoutDetailData hangoutData = hangoutRepository.getHangoutDetailData(eventId);
        if (hangoutData.getHangout() == null) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }

        Hangout hangout = hangoutData.getHangout();
        if (!authorizationService.canUserViewHangout(userId, hangout)) {
            throw new UnauthorizedException("Cannot vote on polls in this event");
        }

        // Get poll data to check settings and existing votes
        List<BaseItem> pollData = hangoutRepository.getSpecificPollData(eventId, pollId);
        Poll poll = pollData.stream()
            .filter(item -> InviterKeyFactory.isPollItem(item.getSk()))
            .map(item -> (Poll) item)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Poll not found"));

        String optionId = request.getOptionId();

        // Get user's existing votes for this poll
        List<Vote> existingVotes = pollData.stream()
            .filter(item -> InviterKeyFactory.isVoteItem(item.getSk()))
            .map(item -> (Vote) item)
            .filter(vote -> vote.getUserId().equals(userId))
            .collect(Collectors.toList());

        // Idempotent re-vote: if the user has already voted for this exact option, return the
        // existing vote unchanged. Applies to both single- and multiple-choice polls.
        for (Vote existing : existingVotes) {
            if (existing.getOptionId().equals(optionId)) {
                logger.info("User {} re-voted for option {} on poll {} — returning existing vote",
                    userId, optionId, pollId);
                return existing;
            }
        }

        if (!poll.isMultipleChoice() && !existingVotes.isEmpty()) {
            // SINGLE CHOICE: replace existing vote (different option than the one requested,
            // since idempotent case handled above).
            Vote existingVote = existingVotes.get(0);
            hangoutRepository.deleteVote(eventId, pollId, userId, existingVote.getOptionId());
        }

        // Create the new vote
        Vote newVote = new Vote(eventId, pollId, optionId, userId, request.getVoteType());
        Vote savedVote = hangoutRepository.saveVote(newVote);

        // Update pointer records with new vote data
        updatePointersWithPolls(eventId);

        return savedVote;
    }

    @Override
    public void removeVote(String eventId, String pollId, String optionId, String userId) {
        logger.info("User {} removing vote from poll {}", userId, pollId);

        // Get hangout and verify user can view it
        HangoutDetailData hangoutData = hangoutRepository.getHangoutDetailData(eventId);
        if (hangoutData.getHangout() == null) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }

        Hangout hangout = hangoutData.getHangout();
        if (!authorizationService.canUserViewHangout(userId, hangout)) {
            throw new UnauthorizedException("Cannot modify votes in this event");
        }

        // Get poll data to find user's existing votes
        List<BaseItem> pollData = hangoutRepository.getSpecificPollData(eventId, pollId);

        // Find user's existing votes for this poll
        List<Vote> existingVotes = pollData.stream()
            .filter(item -> InviterKeyFactory.isVoteItem(item.getSk()))
            .map(item -> (Vote) item)
            .filter(vote -> vote.getUserId().equals(userId))
            .collect(Collectors.toList());

        if (existingVotes.isEmpty()) {
            logger.warn("User {} has no votes to remove from poll {}", userId, pollId);
            return; // No votes to remove
        }

        // If optionId is specified, remove only that vote, otherwise remove all user votes
        if (optionId != null) {
            hangoutRepository.deleteVote(eventId, pollId, userId, optionId);
        } else {
            // Remove all votes for this user on this poll (for single-choice polls)
            for (Vote vote : existingVotes) {
                hangoutRepository.deleteVote(eventId, pollId, userId, vote.getOptionId());
            }
        }

        // Update pointer records with updated vote data
        updatePointersWithPolls(eventId);

        logger.info("Successfully removed vote(s) for user {} from poll {}", userId, pollId);
    }

    @Override
    public void deletePoll(String eventId, String pollId, String userId) {
        logger.info("User {} deleting poll {} from event {}", userId, pollId, eventId);

        // Get hangout and verify user can edit it
        HangoutDetailData hangoutData = hangoutRepository.getHangoutDetailData(eventId);
        if (hangoutData.getHangout() == null) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }

        Hangout hangout = hangoutData.getHangout();
        if (!authorizationService.canUserEditHangout(userId, hangout)) {
            throw new UnauthorizedException("Only event hosts can delete polls");
        }

        // Cancel any outstanding TIME-poll adoption schedules before the row is gone.
        timePollService.onPollDeleted(pollId);

        hangoutRepository.deletePoll(eventId, pollId);

        // Update pointer records with updated poll data (poll now removed)
        updatePointersWithPolls(eventId);

        logger.info("Successfully deleted poll {} from event {}", pollId, eventId);
    }

    @Override
    public PollOption addPollOption(String eventId, String pollId, AddPollOptionRequest request, String userId) {
        logger.info("User {} adding option to poll {} in event {}", userId, pollId, eventId);

        // Get event and verify user can edit it
        HangoutDetailData eventData = hangoutRepository.getHangoutDetailData(eventId);
        if (eventData.getHangout() == null) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }

        Hangout hangout = eventData.getHangout();
        if (!authorizationService.canUserEditHangout(userId, hangout)) {
            throw new UnauthorizedException("User cannot edit hangout");
        }

        // Load the parent poll to determine whether this is a TIME option.
        List<BaseItem> pollData = hangoutRepository.getSpecificPollData(eventId, pollId);
        Poll parentPoll = pollData.stream()
            .filter(item -> InviterKeyFactory.isPollItem(item.getSk()))
            .map(item -> (Poll) item)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Poll not found"));

        boolean isTimePoll = "TIME".equals(parentPoll.getAttributeType());
        PollOption option;

        if (isTimePoll) {
            TimeInfo timeInput = request.getTimeInput();
            if (timeInput == null) {
                throw new ValidationException(TIME_OPTION_REQUIRES_UPDATE_MSG);
            }
            fuzzyTimeService.convert(timeInput); // validates shape

            // Dedupe guard: reject if an existing option on this poll has an equivalent timeInput.
            List<PollOption> existingOptions = pollData.stream()
                .filter(item -> InviterKeyFactory.isPollOption(item.getSk()))
                .map(item -> (PollOption) item)
                .collect(Collectors.toList());
            for (PollOption existing : existingOptions) {
                if (isEquivalentTimeInput(existing.getTimeInput(), timeInput)) {
                    throw new ValidationException("Duplicate time option");
                }
            }

            option = new PollOption(eventId, pollId, TimePollOptionTextGenerator.generate(timeInput));
            option.setTimeInput(timeInput);
        } else {
            String text = request.getText();
            if (text == null || text.trim().isEmpty()) {
                throw new ValidationException("Option text is required");
            }
            option = new PollOption(eventId, pollId, text.trim());
        }

        option.setCreatedBy(userId);
        PollOption savedOption = hangoutRepository.savePollOption(option);

        // Update pointer records with new option data
        updatePointersWithPolls(eventId);

        // Slide the 48h adoption schedule forward if <24h runway remains on the poll.
        if (isTimePoll) {
            timePollService.onOptionAdded(parentPoll);
        }

        logger.info("Successfully added option {} to poll {}", option.getOptionId(), pollId);
        return savedOption;
    }

    /**
     * Two {@link TimeInfo} values are equivalent if they pick the same fuzzy bucket+start OR
     * the same exact start/end. Used to block duplicate TIME options that would break the
     * fast-path adoption rule (see TIME_POLL_MIGRATION_PLAN.md item 9).
     */
    private boolean isEquivalentTimeInput(TimeInfo a, TimeInfo b) {
        if (a == null || b == null) return false;
        if (a.getPeriodGranularity() != null && b.getPeriodGranularity() != null) {
            return Objects.equals(a.getPeriodGranularity(), b.getPeriodGranularity())
                && Objects.equals(a.getPeriodStart(), b.getPeriodStart());
        }
        if (a.getStartTime() != null && b.getStartTime() != null) {
            return Objects.equals(a.getStartTime(), b.getStartTime())
                && Objects.equals(a.getEndTime(), b.getEndTime());
        }
        return false;
    }

    @Override
    public void deletePollOption(String eventId, String pollId, String optionId, String userId) {
        logger.info("User {} deleting option {} from poll {} in event {}", userId, optionId, pollId, eventId);

        // Authorization check - only hosts can delete options
        HangoutDetailData hangoutData = hangoutRepository.getHangoutDetailData(eventId);
        if (hangoutData.getHangout() == null) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }

        Hangout hangout = hangoutData.getHangout();
        if (!authorizationService.canUserEditHangout(userId, hangout)) {
            throw new UnauthorizedException("Only event hosts can delete poll options");
        }

        // Verify poll and option exist
        List<BaseItem> pollData = hangoutRepository.getSpecificPollData(eventId, pollId);

        Poll poll = pollData.stream()
            .filter(item -> InviterKeyFactory.isPollItem(item.getSk()))
            .map(item -> (Poll) item)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Poll not found"));

        boolean optionExists = pollData.stream()
            .filter(item -> InviterKeyFactory.isPollOption(item.getSk()))
            .map(item -> (PollOption) item)
            .anyMatch(option -> option.getOptionId().equals(optionId));

        if (!optionExists) {
            throw new IllegalArgumentException("Poll option not found");
        }

        // Use transaction to delete option and all its votes
        hangoutRepository.deletePollOptionTransaction(eventId, pollId, optionId);

        // Update pointer records with updated option data (option now removed)
        updatePointersWithPolls(eventId);

        logger.info("Successfully deleted option {} and its votes from poll {}", optionId, pollId);
    }

    // ============================================================================
    // POINTER SYNCHRONIZATION
    // ============================================================================

    /**
     * Update all pointer records with the current poll data from the canonical hangout.
     * This method should be called after any poll/option/vote create/update/delete operation.
     *
     * Uses optimistic locking with retry to handle concurrent pointer updates.
     */
    private void updatePointersWithPolls(String hangoutId) {
        // Get hangout to find associated groups
        Optional<Hangout> hangoutOpt = hangoutRepository.findHangoutById(hangoutId);
        if (hangoutOpt.isEmpty()) {
            logger.warn("Cannot update pointers for non-existent hangout: {}", hangoutId);
            return;
        }

        Hangout hangout = hangoutOpt.get();
        List<String> associatedGroups = hangout.getAssociatedGroups();

        if (associatedGroups == null || associatedGroups.isEmpty()) {
            logger.debug("No associated groups for hangout {}, skipping pointer update", hangoutId);
            return;
        }

        // Get current poll data from canonical record (polls, options, votes)
        List<BaseItem> allPollData = hangoutRepository.getAllPollData(hangoutId);

        // Separate into typed lists
        List<Poll> polls = allPollData.stream()
            .filter(item -> InviterKeyFactory.isPollItem(item.getSk()))
            .map(item -> (Poll) item)
            .collect(Collectors.toList());

        List<PollOption> pollOptions = allPollData.stream()
            .filter(item -> InviterKeyFactory.isPollOption(item.getSk()))
            .map(item -> (PollOption) item)
            .collect(Collectors.toList());

        List<Vote> votes = allPollData.stream()
            .filter(item -> InviterKeyFactory.isVoteItem(item.getSk()))
            .map(item -> (Vote) item)
            .collect(Collectors.toList());

        // Update each group's pointer with optimistic locking retry
        for (String groupId : associatedGroups) {
            pointerUpdateService.updatePointerWithRetry(groupId, hangoutId, pointer -> {
                pointer.setPolls(new ArrayList<>(polls));
                pointer.setPollOptions(new ArrayList<>(pollOptions));
                pointer.setVotes(new ArrayList<>(votes));
            }, "poll data");
        }

        // Update group timestamps for ETag invalidation
        groupTimestampService.updateGroupTimestamps(associatedGroups);
    }

    // ============================================================================
    // DATA TRANSFORMATION METHODS (Runtime Vote Count Calculation)
    // ============================================================================

    private List<PollWithOptionsDTO> transformToPollWithOptionsDTO(List<BaseItem> pollData, String userId) {
        // Separate polls, options, and votes
        List<Poll> polls = pollData.stream()
            .filter(item -> InviterKeyFactory.isPollItem(item.getSk()))
            .map(item -> (Poll) item)
            .collect(Collectors.toList());

        Map<String, List<PollOption>> optionsByPoll = pollData.stream()
            .filter(item -> InviterKeyFactory.isPollOption(item.getSk()))
            .map(item -> (PollOption) item)
            .collect(Collectors.groupingBy(PollOption::getPollId));

        Map<String, List<Vote>> votesByPoll = pollData.stream()
            .filter(item -> InviterKeyFactory.isVoteItem(item.getSk()))
            .map(item -> (Vote) item)
            .collect(Collectors.groupingBy(Vote::getPollId));

        ClientInfo clientInfo = currentClientInfo();

        // Build hierarchical DTOs
        return polls.stream()
            .map(poll -> {
                List<PollOption> options = optionsByPoll.getOrDefault(poll.getPollId(), List.of());
                List<Vote> allVotes = votesByPoll.getOrDefault(poll.getPollId(), List.of());

                // Calculate vote counts by option at runtime
                Map<String, Long> voteCountsByOption = allVotes.stream()
                    .collect(Collectors.groupingBy(Vote::getOptionId, Collectors.counting()));

                List<PollOptionDTO> optionDTOs = options.stream()
                    .map(option -> {
                        // Runtime calculation - no denormalized count field needed
                        int voteCount = voteCountsByOption.getOrDefault(option.getOptionId(), 0L).intValue();

                        boolean userVoted = allVotes.stream()
                            .anyMatch(vote -> vote.getOptionId().equals(option.getOptionId())
                                           && vote.getUserId().equals(userId));

                        PollOptionDTO dto = new PollOptionDTO(option.getOptionId(), option.getText(),
                                               voteCount, userVoted,
                                               option.getCreatedBy(), option.getStructuredValue());
                        dto.setTimeInput(option.getTimeInput());
                        return dto;
                    })
                    .collect(Collectors.toList());

                // Total votes = sum of all votes for this poll
                int totalVotes = allVotes.size();

                PollWithOptionsDTO dto = new PollWithOptionsDTO(poll, optionDTOs, totalVotes);
                dto.setViewable(computeViewable(poll, clientInfo));
                dto.setCanAddOptions(computeCanAddOptions(poll, clientInfo));
                return dto;
            })
            .collect(Collectors.toList());
    }

    /**
     * {@code viewable} is currently a pass-through stub that always returns {@code true}.
     * Future iterations will gate unknown attributeTypes behind a client version.
     */
    private boolean computeViewable(Poll poll, ClientInfo clientInfo) {
        return true;
    }

    /**
     * {@code canAddOptions} is a client-capability gate (NOT an authorization decision).
     * Non-TIME polls always return true. TIME polls return true when either the ClientInfo is
     * missing (defensive default per plan item N14) or the app version is at least
     * {@link TimePollConfig#getMinTimeSuggestionVersion()}.
     */
    private boolean computeCanAddOptions(Poll poll, ClientInfo clientInfo) {
        if (!"TIME".equals(poll.getAttributeType())) {
            return true;
        }
        if (clientInfo == null) {
            return true; // default-open when request attribute is missing
        }
        if (!timePollConfig.isMinVersionKnown()) {
            // Config hasn't been pinned to a real version yet — don't silently gate.
            // See TimePollConfig for the flag requirement (plan item N10).
            return true;
        }
        return clientInfo.isVersionAtLeast(timePollConfig.getMinTimeSuggestionVersion());
    }

    private ClientInfo currentClientInfo() {
        try {
            ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            HttpServletRequest req = attrs.getRequest();
            return ClientInfo.fromRequestAttribute(req);
        } catch (Exception e) {
            return null;
        }
    }

    private PollDetailDTO transformToPollDetailDTO(List<BaseItem> pollData, String userId) {
        // Find the poll
        Poll poll = pollData.stream()
            .filter(item -> InviterKeyFactory.isPollItem(item.getSk()))
            .map(item -> (Poll) item)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Poll not found"));

        // Group options and votes
        List<PollOption> options = pollData.stream()
            .filter(item -> InviterKeyFactory.isPollOption(item.getSk()))
            .map(item -> (PollOption) item)
            .collect(Collectors.toList());

        Map<String, List<Vote>> votesByOption = pollData.stream()
            .filter(item -> InviterKeyFactory.isVoteItem(item.getSk()))
            .map(item -> (Vote) item)
            .collect(Collectors.groupingBy(Vote::getOptionId));

        // iOS 2.2.x has a strict Vote decoder that rejects the abbreviated VoteDTO shape.
        // Strip embedded option-vote arrays (and skip displayName enrichment) for that range.
        ClientInfo clientInfo = currentClientInfo();
        boolean includeEmbeddedVotes =
            !(clientInfo != null && clientInfo.isIosVersionInRange("2.2.0", "2.3.0"));

        // Build detailed option DTOs with vote details
        List<PollOptionDetailDTO> optionDTOs = options.stream()
            .map(option -> {
                List<Vote> optionVotes = votesByOption.getOrDefault(option.getOptionId(), List.of());
                boolean userVoted = optionVotes.stream()
                    .anyMatch(vote -> vote.getUserId().equals(userId));

                List<VoteDTO> voteDTOs = includeEmbeddedVotes
                    ? optionVotes.stream()
                        .map(vote -> {
                            VoteDTO dto = new VoteDTO(vote);
                            userService.getUserSummary(UUID.fromString(vote.getUserId()))
                                .ifPresent(u -> dto.setDisplayName(u.getDisplayName()));
                            return dto;
                        })
                        .collect(Collectors.toList())
                    : List.of();

                // Runtime calculation - count the actual votes
                int voteCount = optionVotes.size();

                return new PollOptionDetailDTO(option.getOptionId(), option.getText(),
                                             voteCount, userVoted, voteDTOs);
            })
            .collect(Collectors.toList());

        // Total votes = total count of all vote records
        int totalVotes = votesByOption.values().stream().mapToInt(List::size).sum();

        return new PollDetailDTO(poll, optionDTOs, totalVotes);
    }
}
