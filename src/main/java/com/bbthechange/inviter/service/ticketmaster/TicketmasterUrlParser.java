package com.bbthechange.inviter.service.ticketmaster;

import lombok.Builder;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Ticketmaster event URLs to extract search parameters.
 *
 * WARNING: This parser uses best-effort heuristics and has known limitations:
 *
 * 1. Simple URLs (/event/ID) - No extractable information
 * 2. Ambiguous parsing - Cannot reliably distinguish city from event name
 * 3. Multi-word locations - Difficult to parse (e.g., "new-york", "salt-lake-city")
 * 4. Search accuracy - Multiple events might match extracted keywords
 *
 * The parser prioritizes recall over precision - it's better to search with
 * broader keywords than to fail entirely.
 */
public class TicketmasterUrlParser {

    private static final Logger logger = LoggerFactory.getLogger(TicketmasterUrlParser.class);

    // Pattern: MM-DD-YYYY at end of slug
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{2}-\\d{2}-\\d{4})$");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM-dd-yyyy");

    // Map of state names (kebab-case) to two-letter codes
    private static final Map<String, String> STATE_NAME_TO_CODE = new HashMap<>();

    static {
        // US States
        STATE_NAME_TO_CODE.put("alabama", "AL");
        STATE_NAME_TO_CODE.put("alaska", "AK");
        STATE_NAME_TO_CODE.put("arizona", "AZ");
        STATE_NAME_TO_CODE.put("arkansas", "AR");
        STATE_NAME_TO_CODE.put("california", "CA");
        STATE_NAME_TO_CODE.put("colorado", "CO");
        STATE_NAME_TO_CODE.put("connecticut", "CT");
        STATE_NAME_TO_CODE.put("delaware", "DE");
        STATE_NAME_TO_CODE.put("florida", "FL");
        STATE_NAME_TO_CODE.put("georgia", "GA");
        STATE_NAME_TO_CODE.put("hawaii", "HI");
        STATE_NAME_TO_CODE.put("idaho", "ID");
        STATE_NAME_TO_CODE.put("illinois", "IL");
        STATE_NAME_TO_CODE.put("indiana", "IN");
        STATE_NAME_TO_CODE.put("iowa", "IA");
        STATE_NAME_TO_CODE.put("kansas", "KS");
        STATE_NAME_TO_CODE.put("kentucky", "KY");
        STATE_NAME_TO_CODE.put("louisiana", "LA");
        STATE_NAME_TO_CODE.put("maine", "ME");
        STATE_NAME_TO_CODE.put("maryland", "MD");
        STATE_NAME_TO_CODE.put("massachusetts", "MA");
        STATE_NAME_TO_CODE.put("michigan", "MI");
        STATE_NAME_TO_CODE.put("minnesota", "MN");
        STATE_NAME_TO_CODE.put("mississippi", "MS");
        STATE_NAME_TO_CODE.put("missouri", "MO");
        STATE_NAME_TO_CODE.put("montana", "MT");
        STATE_NAME_TO_CODE.put("nebraska", "NE");
        STATE_NAME_TO_CODE.put("nevada", "NV");
        STATE_NAME_TO_CODE.put("new-hampshire", "NH");
        STATE_NAME_TO_CODE.put("new-jersey", "NJ");
        STATE_NAME_TO_CODE.put("new-mexico", "NM");
        STATE_NAME_TO_CODE.put("new-york", "NY");
        STATE_NAME_TO_CODE.put("north-carolina", "NC");
        STATE_NAME_TO_CODE.put("north-dakota", "ND");
        STATE_NAME_TO_CODE.put("ohio", "OH");
        STATE_NAME_TO_CODE.put("oklahoma", "OK");
        STATE_NAME_TO_CODE.put("oregon", "OR");
        STATE_NAME_TO_CODE.put("pennsylvania", "PA");
        STATE_NAME_TO_CODE.put("rhode-island", "RI");
        STATE_NAME_TO_CODE.put("south-carolina", "SC");
        STATE_NAME_TO_CODE.put("south-dakota", "SD");
        STATE_NAME_TO_CODE.put("tennessee", "TN");
        STATE_NAME_TO_CODE.put("texas", "TX");
        STATE_NAME_TO_CODE.put("utah", "UT");
        STATE_NAME_TO_CODE.put("vermont", "VT");
        STATE_NAME_TO_CODE.put("virginia", "VA");
        STATE_NAME_TO_CODE.put("washington", "WA");
        STATE_NAME_TO_CODE.put("west-virginia", "WV");
        STATE_NAME_TO_CODE.put("wisconsin", "WI");
        STATE_NAME_TO_CODE.put("wyoming", "WY");
        STATE_NAME_TO_CODE.put("district-of-columbia", "DC");
    }

    @Data
    @Builder
    public static class ParsedTicketmasterUrl {
        private String keyword;           // Event name for search
        private String stateCode;        // Two-letter state code (e.g., "WA")
        private String city;             // City name
        private LocalDate eventDate;     // Event date if found
        private String originalSlug;     // Original URL slug for debugging
    }

    /**
     * Checks if a URL is a Ticketmaster event URL
     */
    public static boolean isTicketmasterEventUrl(String url) {
        if (url == null) {
            return false;
        }
        String lowerUrl = url.toLowerCase();
        return lowerUrl.contains("ticketmaster.com") && lowerUrl.contains("/event/");
    }

    /**
     * Parses a Ticketmaster event URL to extract search parameters.
     *
     * @param url Full Ticketmaster event URL
     * @return Parsed search parameters, or null if URL has no slug
     * @throws IllegalArgumentException if URL is not a valid Ticketmaster event URL
     */
    public static ParsedTicketmasterUrl parse(String url) {
        if (!isTicketmasterEventUrl(url)) {
            throw new IllegalArgumentException("Not a Ticketmaster event URL: " + url);
        }

        // Extract slug (everything between domain and /event/) - case insensitive
        String lowerUrl = url.toLowerCase();
        int eventIndex = lowerUrl.indexOf("/event/");
        int domainEnd = lowerUrl.indexOf("//") + 2;
        int pathStart = lowerUrl.indexOf('/', domainEnd);

        if (pathStart == -1 || pathStart >= eventIndex) {
            // Simple URL format: /event/ID with no slug
            logger.warn("Ticketmaster URL has no slug to parse: {}", url);
            return null;
        }

        String slug = url.substring(pathStart + 1, eventIndex);
        logger.debug("Extracted slug: {}", slug);

        return parseSlug(slug);
    }

    /**
     * Parses the URL slug to extract event details.
     * Uses heuristics - not guaranteed to be accurate.
     */
    private static ParsedTicketmasterUrl parseSlug(String slug) {
        ParsedTicketmasterUrl.ParsedTicketmasterUrlBuilder builder = ParsedTicketmasterUrl.builder()
            .originalSlug(slug);

        String remainingSlug = slug;

        // Step 1: Extract date from end (most reliable)
        Matcher dateMatcher = DATE_PATTERN.matcher(remainingSlug);
        if (dateMatcher.find()) {
            String dateStr = dateMatcher.group(1);
            try {
                LocalDate eventDate = LocalDate.parse(dateStr, DATE_FORMATTER);
                builder.eventDate(eventDate);
                // Remove date from slug
                remainingSlug = remainingSlug.substring(0, dateMatcher.start()).replaceAll("-$", "");
                logger.debug("Extracted date: {}", eventDate);
            } catch (DateTimeParseException e) {
                logger.warn("Failed to parse date from slug: {}", dateStr);
            }
        }

        // Step 2: Look for state name at end (after date removal)
        String[] parts = remainingSlug.split("-");
        String stateCode = null;
        int stateEndIndex = parts.length;

        // Check last 1-3 parts for state name (handles multi-word states)
        for (int i = Math.max(0, parts.length - 3); i < parts.length; i++) {
            StringBuilder potentialState = new StringBuilder();
            for (int j = i; j < parts.length; j++) {
                if (potentialState.length() > 0) {
                    potentialState.append("-");
                }
                potentialState.append(parts[j]);

                String stateKey = potentialState.toString().toLowerCase();
                if (STATE_NAME_TO_CODE.containsKey(stateKey)) {
                    stateCode = STATE_NAME_TO_CODE.get(stateKey);
                    stateEndIndex = i;
                    logger.debug("Extracted state: {} -> {}", stateKey, stateCode);
                    break;
                }
            }
            if (stateCode != null) {
                break;
            }
        }

        builder.stateCode(stateCode);

        // Step 3: Extract city (word before state, if state was found)
        if (stateCode != null && stateEndIndex > 0) {
            // City is the part immediately before the state
            // Handle multi-word cities by taking only the last word before state
            String city = parts[stateEndIndex - 1];
            builder.city(capitalizeWords(city));
            logger.debug("Extracted city: {}", city);
            stateEndIndex--; // Exclude city from keyword
        }

        // Step 4: Everything remaining is the event name keyword
        StringBuilder keyword = new StringBuilder();
        for (int i = 0; i < stateEndIndex; i++) {
            if (keyword.length() > 0) {
                keyword.append(" ");
            }
            keyword.append(parts[i]);
        }

        String keywordStr = keyword.toString().trim();
        if (keywordStr.isEmpty()) {
            // Fallback: use entire original slug as keyword
            keywordStr = slug.replace("-", " ");
            logger.warn("Could not parse slug structure, using full slug as keyword: {}", keywordStr);
        }

        builder.keyword(keywordStr);
        logger.debug("Extracted keyword: {}", keywordStr);

        return builder.build();
    }

    private static String capitalizeWords(String str) {
        String[] words = str.split("-");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (result.length() > 0) {
                result.append(" ");
            }
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                      .append(word.substring(1).toLowerCase());
            }
        }
        return result.toString();
    }
}
