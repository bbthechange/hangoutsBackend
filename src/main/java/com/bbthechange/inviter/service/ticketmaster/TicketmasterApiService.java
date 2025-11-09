package com.bbthechange.inviter.service.ticketmaster;

import com.bbthechange.inviter.dto.Address;
import com.bbthechange.inviter.dto.ParsedEventDetailsDto;
import com.bbthechange.inviter.exception.EventParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Service for fetching event details from Ticketmaster Discovery API.
 *
 * API Documentation: https://developer.ticketmaster.com/products-and-docs/apis/discovery-api/v2/
 */
@Service
public class TicketmasterApiService {

    private static final Logger logger = LoggerFactory.getLogger(TicketmasterApiService.class);

    private static final String DISCOVERY_API_BASE = "https://app.ticketmaster.com/discovery/v2";
    private static final String EVENTS_SEARCH_ENDPOINT = "/events.json";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ticketmaster.api.key:}")
    private String apiKey;

    public TicketmasterApiService(@Qualifier("externalRestTemplate") RestTemplate restTemplate,
                                   ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Searches for an event using the Discovery API based on parsed URL parameters.
     *
     * @param parsedUrl Parsed Ticketmaster URL with search parameters
     * @return Event details from the first matching event
     * @throws EventParseException if API key is missing, no events found, or API call fails
     */
    public ParsedEventDetailsDto searchEvent(TicketmasterUrlParser.ParsedTicketmasterUrl parsedUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new EventParseException("Ticketmaster API key is not configured");
        }

        if (parsedUrl == null || parsedUrl.getKeyword() == null || parsedUrl.getKeyword().isBlank()) {
            throw new EventParseException("Cannot search Ticketmaster API: no keyword extracted from URL");
        }

        try {
            java.net.URI searchUri = buildSearchUri(parsedUrl);
            logger.info("Searching Ticketmaster API: {}", searchUri);

            ResponseEntity<String> response = restTemplate.getForEntity(searchUri, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new EventParseException("Ticketmaster API returned non-success status: " + response.getStatusCode());
            }

            JsonNode root = objectMapper.readTree(response.getBody());

            // Check for API errors
            if (root.has("errors")) {
                String errorMsg = root.get("errors").get(0).get("detail").asText();
                throw new EventParseException("Ticketmaster API error: " + errorMsg);
            }

            // Extract events from response
            JsonNode embedded = root.get("_embedded");
            if (embedded == null || !embedded.has("events")) {
                throw new EventParseException("No events found matching URL: " + parsedUrl.getOriginalSlug());
            }

            JsonNode events = embedded.get("events");
            if (events.isEmpty()) {
                throw new EventParseException("No events found matching URL: " + parsedUrl.getOriginalSlug());
            }

            // Return first event (closest match)
            JsonNode firstEvent = events.get(0);
            logger.info("Found {} events, using first match: {}",
                events.size(), firstEvent.get("name").asText());

            return mapEventToDto(firstEvent);

        } catch (EventParseException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error calling Ticketmaster API", e);
            throw new EventParseException("Failed to fetch event from Ticketmaster API: " + e.getMessage(), e);
        }
    }

    private java.net.URI buildSearchUri(TicketmasterUrlParser.ParsedTicketmasterUrl parsedUrl) {
        UriComponentsBuilder builder = UriComponentsBuilder
            .fromHttpUrl(DISCOVERY_API_BASE + EVENTS_SEARCH_ENDPOINT)
            .queryParam("apikey", apiKey)
            .queryParam("keyword", parsedUrl.getKeyword())
            .queryParam("size", 5); // Get top 5 matches

        if (parsedUrl.getStateCode() != null) {
            builder.queryParam("stateCode", parsedUrl.getStateCode());
        }

        if (parsedUrl.getCity() != null) {
            builder.queryParam("city", parsedUrl.getCity());
        }

        if (parsedUrl.getEventDate() != null) {
            // Widen date range by ±1 day to account for timezone offsets
            // Events may be on May 12 local time but May 13 UTC (or vice versa)
            String startDateStr = parsedUrl.getEventDate().minusDays(1).toString();
            String endDateStr = parsedUrl.getEventDate().plusDays(1).toString();
            builder.queryParam("startDateTime", startDateStr + "T00:00:00Z");
            builder.queryParam("endDateTime", endDateStr + "T23:59:59Z");
        }

        return builder.build().toUri();
    }

    private ParsedEventDetailsDto mapEventToDto(JsonNode event) {
        String name = event.get("name").asText();
        String eventUrl = event.has("url") ? event.get("url").asText() : null;
        String description = event.has("info") ? event.get("info").asText() : null;

        // Extract dates
        OffsetDateTime startTime = null;
        OffsetDateTime endTime = null;

        if (event.has("dates") && event.get("dates").has("start")) {
            JsonNode startNode = event.get("dates").get("start");

            if (startNode.has("dateTime")) {
                String dateTimeStr = startNode.get("dateTime").asText();
                startTime = parseDateTime(dateTimeStr);
            } else if (startNode.has("localDate")) {
                // Date only, no time
                String localDate = startNode.get("localDate").asText();
                try {
                    startTime = OffsetDateTime.parse(localDate + "T00:00:00Z");
                } catch (DateTimeParseException e) {
                    logger.warn("Could not parse localDate: {}", localDate);
                }
            }
        }

        // Extract images
        String imageUrl = null;
        if (event.has("images") && event.get("images").isArray() && !event.get("images").isEmpty()) {
            JsonNode firstImage = event.get("images").get(0);
            if (firstImage.has("url")) {
                imageUrl = firstImage.get("url").asText();
            }
        }

        // Extract venue/location
        Address address = null;
        if (event.has("_embedded") && event.get("_embedded").has("venues")) {
            JsonNode venues = event.get("_embedded").get("venues");
            if (venues.isArray() && !venues.isEmpty()) {
                JsonNode venue = venues.get(0);
                address = extractAddress(venue);
            }
        }

        return ParsedEventDetailsDto.builder()
            .title(name)
            .description(description)
            .startTime(startTime)
            .endTime(endTime)
            .location(address)
            .imageUrl(imageUrl)
            .url(eventUrl)
            .sourceUrl(eventUrl)
            .build();
    }

    private Address extractAddress(JsonNode venue) {
        String venueName = venue.has("name") ? venue.get("name").asText() : null;

        if (!venue.has("address")) {
            return venueName != null ? new Address(venueName) : null;
        }

        JsonNode addressNode = venue.get("address");
        JsonNode cityNode = venue.has("city") ? venue.get("city") : null;
        JsonNode stateNode = venue.has("state") ? venue.get("state") : null;
        JsonNode countryNode = venue.has("country") ? venue.get("country") : null;

        String street = addressNode.has("line1") ? addressNode.get("line1").asText() : null;
        String city = cityNode != null && cityNode.has("name") ? cityNode.get("name").asText() : null;
        String state = stateNode != null && stateNode.has("stateCode") ? stateNode.get("stateCode").asText() : null;
        String postalCode = venue.has("postalCode") ? venue.get("postalCode").asText() : null;
        String country = countryNode != null && countryNode.has("countryCode") ? countryNode.get("countryCode").asText() : null;

        return new Address(venueName, street, city, state, postalCode, country);
    }

    private OffsetDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isBlank()) {
            return null;
        }

        try {
            return OffsetDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (DateTimeParseException e) {
            logger.warn("Could not parse dateTime: {}", dateTimeStr);
            return null;
        }
    }
}
