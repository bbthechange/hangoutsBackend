package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.IdeaListMember;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IdeaDTOTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    private static final String GROUP_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String LIST_ID  = "550e8400-e29b-41d4-a716-446655440001";
    private static final String USER_ID  = "550e8400-e29b-41d4-a716-446655440002";

    private IdeaListMember buildEnrichedMember() {
        IdeaListMember member = new IdeaListMember(GROUP_ID, LIST_ID, "Sushi Nakazawa", null, "Great omakase", USER_ID);
        member.setGooglePlaceId("ChIJN1t_tDeuEmsRUsoyG83frY4");
        member.setApplePlaceId("I63LYKU7G9BCPA");
        member.setAddress("23 Commerce St, New York, NY 10014");
        member.setLatitude(40.7295);
        member.setLongitude(-74.0028);
        member.setPlaceCategory("restaurant");
        member.setCachedPhotoUrl("places/sushi-nakazawa_40.7295_-74.0028/photo.jpg");
        member.setCachedRating(4.6);
        member.setCachedPriceLevel(4);
        member.setCachedHoursJson("[\"Monday: 5:00 – 10:00 PM\"]");
        member.setPhoneNumber("+12125240500");
        member.setWebsiteUrl("https://sushinakazawa.com");
        member.setMenuUrl(null);
        member.setLastEnrichedAt(Instant.parse("2026-03-16T12:00:00Z"));
        member.setEnrichmentStatus("ENRICHED");
        return member;
    }

    @Nested
    class SerializationTests {

        @Test
        void ideaDTO_withEnrichedData_serializesAllPlaceFields() throws Exception {
            IdeaListMember member = buildEnrichedMember();
            IdeaDTO dto = new IdeaDTO(member);

            String json = objectMapper.writeValueAsString(dto);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(json, Map.class);

            assertThat(map).containsKey("googlePlaceId");
            assertThat(map.get("googlePlaceId")).isEqualTo("ChIJN1t_tDeuEmsRUsoyG83frY4");
            assertThat(map.get("applePlaceId")).isEqualTo("I63LYKU7G9BCPA");
            assertThat(map.get("address")).isEqualTo("23 Commerce St, New York, NY 10014");
            assertThat(map.get("latitude")).isEqualTo(40.7295);
            assertThat(map.get("longitude")).isEqualTo(-74.0028);
            assertThat(map.get("placeCategory")).isEqualTo("restaurant");
            assertThat(map.get("cachedPhotoUrl")).isEqualTo("places/sushi-nakazawa_40.7295_-74.0028/photo.jpg");
            assertThat(map.get("cachedRating")).isEqualTo(4.6);
            assertThat(map.get("cachedPriceLevel")).isEqualTo(4);
            assertThat(map.get("cachedHoursJson")).isEqualTo("[\"Monday: 5:00 – 10:00 PM\"]");
            assertThat(map.get("phoneNumber")).isEqualTo("+12125240500");
            assertThat(map.get("websiteUrl")).isEqualTo("https://sushinakazawa.com");
            assertThat(map.get("enrichmentStatus")).isEqualTo("ENRICHED");
        }

        @Test
        void ideaDTO_nonPlaceIdea_nullFieldsOmitted() throws Exception {
            IdeaListMember member = new IdeaListMember(GROUP_ID, LIST_ID, "Watch The Matrix", null, "Classic sci-fi", USER_ID);
            IdeaDTO dto = new IdeaDTO(member);

            String json = objectMapper.writeValueAsString(dto);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(json, Map.class);

            assertThat(map).doesNotContainKey("googlePlaceId");
            assertThat(map).doesNotContainKey("applePlaceId");
            assertThat(map).doesNotContainKey("address");
            assertThat(map).doesNotContainKey("latitude");
            assertThat(map).doesNotContainKey("longitude");
            assertThat(map).doesNotContainKey("placeCategory");
            assertThat(map).doesNotContainKey("cachedPhotoUrl");
            assertThat(map).doesNotContainKey("cachedRating");
            assertThat(map).doesNotContainKey("cachedPriceLevel");
            assertThat(map).doesNotContainKey("cachedHoursJson");
            assertThat(map).doesNotContainKey("phoneNumber");
            assertThat(map).doesNotContainKey("websiteUrl");
            assertThat(map).doesNotContainKey("menuUrl");
            assertThat(map).doesNotContainKey("enrichmentStatus");
            assertThat(map).doesNotContainKey("lastEnrichedAt");
        }

        @Test
        void ideaDTO_lastEnrichedAt_isISO8601() throws Exception {
            IdeaListMember member = buildEnrichedMember();
            member.setLastEnrichedAt(Instant.parse("2026-03-16T12:00:00Z"));
            IdeaDTO dto = new IdeaDTO(member);

            assertThat(dto.getLastEnrichedAt()).isEqualTo("2026-03-16T12:00:00Z");

            String json = objectMapper.writeValueAsString(dto);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(json, Map.class);

            String lastEnrichedAt = (String) map.get("lastEnrichedAt");
            assertThat(lastEnrichedAt).isNotNull();
            // Must be ISO 8601 string, not a number
            assertThat(lastEnrichedAt).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");
        }

        @Test
        void ideaDTO_pendingStatus_serializedCorrectly() throws Exception {
            IdeaListMember member = new IdeaListMember(GROUP_ID, LIST_ID, "New Restaurant", null, null, USER_ID);
            member.setAddress("100 Broadway");
            member.setLatitude(40.7128);
            member.setLongitude(-74.0060);
            member.setEnrichmentStatus("PENDING");
            IdeaDTO dto = new IdeaDTO(member);

            String json = objectMapper.writeValueAsString(dto);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(json, Map.class);

            assertThat(map.get("enrichmentStatus")).isEqualTo("PENDING");
        }

        @Test
        void ideaDTO_failedStatus_serializedCorrectly() throws Exception {
            IdeaListMember member = new IdeaListMember(GROUP_ID, LIST_ID, "Some Place", null, null, USER_ID);
            member.setAddress("200 Main St");
            member.setLatitude(37.7749);
            member.setLongitude(-122.4194);
            member.setEnrichmentStatus("FAILED");
            IdeaDTO dto = new IdeaDTO(member);

            String json = objectMapper.writeValueAsString(dto);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(json, Map.class);

            assertThat(map.get("enrichmentStatus")).isEqualTo("FAILED");
        }

        @Test
        void ideaDTO_partialPlaceData_serializesOnlyNonNullFields() throws Exception {
            // Place idea with only address + coords — no enrichment data fields set
            IdeaListMember member = new IdeaListMember(GROUP_ID, LIST_ID, "Home", null, null, USER_ID);
            member.setAddress("123 Oak Ave");
            member.setLatitude(34.0522);
            member.setLongitude(-118.2437);
            member.setPlaceCategory("home");
            member.setEnrichmentStatus("PENDING");
            IdeaDTO dto = new IdeaDTO(member);

            String json = objectMapper.writeValueAsString(dto);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(json, Map.class);

            // Place identity fields present
            assertThat(map.get("address")).isEqualTo("123 Oak Ave");
            assertThat(map.get("latitude")).isEqualTo(34.0522);
            assertThat(map.get("longitude")).isEqualTo(-118.2437);
            assertThat(map.get("placeCategory")).isEqualTo("home");
            assertThat(map.get("enrichmentStatus")).isEqualTo("PENDING");
            // Enrichment data fields absent (not yet enriched)
            assertThat(map).doesNotContainKey("cachedPhotoUrl");
            assertThat(map).doesNotContainKey("cachedRating");
            assertThat(map).doesNotContainKey("cachedPriceLevel");
            assertThat(map).doesNotContainKey("cachedHoursJson");
            assertThat(map).doesNotContainKey("phoneNumber");
            assertThat(map).doesNotContainKey("websiteUrl");
            assertThat(map).doesNotContainKey("menuUrl");
            assertThat(map).doesNotContainKey("lastEnrichedAt");
        }

        @Test
        void ideaDTO_cachedHoursJson_remainsStringNotReparsed() throws Exception {
            // cachedHoursJson must serialize as a JSON string value, not be re-parsed into an array
            String hoursJson = "[\"Monday: 9:00 AM – 10:00 PM\", \"Tuesday: 9:00 AM – 10:00 PM\"]";
            IdeaListMember member = buildEnrichedMember();
            member.setCachedHoursJson(hoursJson);
            IdeaDTO dto = new IdeaDTO(member);

            String json = objectMapper.writeValueAsString(dto);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(json, Map.class);

            // Must be a String, not a List — clients parse it themselves
            Object value = map.get("cachedHoursJson");
            assertThat(value).isInstanceOf(String.class);
            assertThat((String) value).isEqualTo(hoursJson);
        }

        @Test
        void ideaDTO_permanentlyFailedStatus_serializedCorrectly() throws Exception {
            IdeaListMember member = new IdeaListMember(GROUP_ID, LIST_ID, "Some Place", null, null, USER_ID);
            member.setAddress("123 Main St");
            member.setLatitude(37.7749);
            member.setLongitude(-122.4194);
            member.setEnrichmentStatus("PERMANENTLY_FAILED");
            IdeaDTO dto = new IdeaDTO(member);

            assertThat(dto.getEnrichmentStatus()).isEqualTo("PERMANENTLY_FAILED");

            String json = objectMapper.writeValueAsString(dto);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(json, Map.class);

            assertThat(map.get("enrichmentStatus")).isEqualTo("PERMANENTLY_FAILED");
        }
    }
}
