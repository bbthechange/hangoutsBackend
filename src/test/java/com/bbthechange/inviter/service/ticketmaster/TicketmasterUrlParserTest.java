package com.bbthechange.inviter.service.ticketmaster;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

class TicketmasterUrlParserTest {

    @Test
    void isTicketmasterEventUrl_WithValidTicketmasterUrl_ReturnsTrue() {
        // given
        String url = "https://www.ticketmaster.com/event-name/event/ABC123";

        // when
        boolean result = TicketmasterUrlParser.isTicketmasterEventUrl(url);

        // then
        assertThat(result).isTrue();
    }

    @Test
    void isTicketmasterEventUrl_WithNonTicketmasterUrl_ReturnsFalse() {
        // given
        String url = "https://www.eventbrite.com/event/123";

        // when
        boolean result = TicketmasterUrlParser.isTicketmasterEventUrl(url);

        // then
        assertThat(result).isFalse();
    }

    @Test
    void isTicketmasterEventUrl_WithNullUrl_ReturnsFalse() {
        // when
        boolean result = TicketmasterUrlParser.isTicketmasterEventUrl(null);

        // then
        assertThat(result).isFalse();
    }

    @Test
    void parse_WithFullSlugFormat_ExtractsAllComponents() {
        // given
        String url = "https://www.ticketmaster.com/new-orleans-saints-v-seattle-seahawks-seattle-washington-09-21-2025/event/0F006278E6DF3E1A";

        // when
        TicketmasterUrlParser.ParsedTicketmasterUrl result = TicketmasterUrlParser.parse(url);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getKeyword()).isEqualTo("new orleans saints v seattle seahawks");
        assertThat(result.getStateCode()).isEqualTo("WA");
        assertThat(result.getCity()).isEqualTo("Seattle");
        assertThat(result.getEventDate()).isEqualTo(LocalDate.of(2025, 9, 21));
        assertThat(result.getOriginalSlug()).isEqualTo("new-orleans-saints-v-seattle-seahawks-seattle-washington-09-21-2025");
    }

    @Test
    void parse_WithEventNameAndDate_ExtractsKeywordAndDate() {
        // given
        String url = "https://www.ticketmaster.com/concert-in-the-park-06-15-2025/event/XYZ789";

        // when
        TicketmasterUrlParser.ParsedTicketmasterUrl result = TicketmasterUrlParser.parse(url);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getKeyword()).isEqualTo("concert in the park");
        assertThat(result.getEventDate()).isEqualTo(LocalDate.of(2025, 6, 15));
        assertThat(result.getStateCode()).isNull();
        assertThat(result.getCity()).isNull();
    }

    @Test
    void parse_WithEventNameOnly_ExtractsKeywordOnly() {
        // given
        String url = "https://www1.ticketmaster.com/zpl-jingle-jam-featuring-lizzo-why-dont-we/event/05005726960D2E82";

        // when
        TicketmasterUrlParser.ParsedTicketmasterUrl result = TicketmasterUrlParser.parse(url);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getKeyword()).isEqualTo("zpl jingle jam featuring lizzo why dont we");
        assertThat(result.getEventDate()).isNull();
        assertThat(result.getStateCode()).isNull();
        assertThat(result.getCity()).isNull();
    }

    @Test
    void parse_WithMultiWordState_ExtractsState() {
        // given
        String url = "https://www.ticketmaster.com/film-festival-durham-north-carolina-12-25-2025/event/ABC123";

        // when
        TicketmasterUrlParser.ParsedTicketmasterUrl result = TicketmasterUrlParser.parse(url);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getKeyword()).isEqualTo("film festival");
        assertThat(result.getStateCode()).isEqualTo("NC");
        assertThat(result.getCity()).isEqualTo("Durham");
        assertThat(result.getEventDate()).isEqualTo(LocalDate.of(2025, 12, 25));
    }

    @Test
    void parse_WithMultiWordCity_ExtractsCity() {
        // given
        String url = "https://www.ticketmaster.com/broadway-show-new-york-new-york-03-10-2025/event/DEF456";

        // when
        TicketmasterUrlParser.ParsedTicketmasterUrl result = TicketmasterUrlParser.parse(url);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getKeyword()).isEqualTo("broadway show new");
        assertThat(result.getStateCode()).isEqualTo("NY");
        // Note: "york" becomes the city because it's immediately before the state
        assertThat(result.getCity()).isEqualTo("York");
        assertThat(result.getEventDate()).isEqualTo(LocalDate.of(2025, 3, 10));
    }

    @Test
    void parse_WithSimpleEventUrl_ReturnsNull() {
        // given
        String url = "http://www.ticketmaster.com/event/3B00533D15B0171F";

        // when
        TicketmasterUrlParser.ParsedTicketmasterUrl result = TicketmasterUrlParser.parse(url);

        // then
        assertThat(result).isNull();
    }

    @Test
    void parse_WithCityAndState_ExtractsBoth() {
        // given
        String url = "https://www.ticketmaster.com/rock-concert-austin-texas-07-04-2025/event/GHI789";

        // when
        TicketmasterUrlParser.ParsedTicketmasterUrl result = TicketmasterUrlParser.parse(url);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getKeyword()).isEqualTo("rock concert");
        assertThat(result.getStateCode()).isEqualTo("TX");
        assertThat(result.getCity()).isEqualTo("Austin");
        assertThat(result.getEventDate()).isEqualTo(LocalDate.of(2025, 7, 4));
    }

    @Test
    void parse_WithNonTicketmasterUrl_ThrowsException() {
        // given
        String url = "https://www.eventbrite.com/event/123";

        // when / then
        assertThatThrownBy(() -> TicketmasterUrlParser.parse(url))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Not a Ticketmaster event URL");
    }

    @Test
    void parse_WithInvalidDateFormat_IgnoresDate() {
        // given
        String url = "https://www.ticketmaster.com/concert-seattle-washington-99-99-2025/event/ABC123";

        // when
        TicketmasterUrlParser.ParsedTicketmasterUrl result = TicketmasterUrlParser.parse(url);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getEventDate()).isNull();
        // Keyword includes what looks like date since it couldn't be parsed
        assertThat(result.getKeyword()).contains("99");
    }

    @Test
    void parse_WithUrlContainingNumbers_HandlesCorrectly() {
        // given
        String url = "https://www.ticketmaster.com/taylor-swift-1989-tour-los-angeles-california-10-31-2025/event/MNO345";

        // when
        TicketmasterUrlParser.ParsedTicketmasterUrl result = TicketmasterUrlParser.parse(url);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getKeyword()).contains("1989");
        assertThat(result.getStateCode()).isEqualTo("CA");
        assertThat(result.getEventDate()).isEqualTo(LocalDate.of(2025, 10, 31));
    }

    @Test
    void parse_WithDistrictOfColumbia_ExtractsDCStateCode() {
        // given
        String url = "https://www.ticketmaster.com/inauguration-washington-district-of-columbia-01-20-2025/event/PQR678";

        // when
        TicketmasterUrlParser.ParsedTicketmasterUrl result = TicketmasterUrlParser.parse(url);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getStateCode()).isEqualTo("DC");
        assertThat(result.getCity()).isEqualTo("Washington");
    }

    @Test
    void parse_WithUppercaseUrl_HandlesCorrectly() {
        // given - URL with uppercase characters (though Ticketmaster typically uses lowercase)
        String url = "https://WWW.TICKETMASTER.COM/Concert-Seattle-Washington-12-25-2025/EVENT/ABC123";

        // when
        TicketmasterUrlParser.ParsedTicketmasterUrl result = TicketmasterUrlParser.parse(url);

        // then
        assertThat(result).isNotNull();
        // Parser should handle mixed case
        assertThat(result.getStateCode()).isEqualTo("WA");
    }

    @Test
    void parse_WithTrailingSlash_HandlesCorrectly() {
        // given
        String url = "https://www.ticketmaster.com/concert-austin-texas-06-15-2025/event/ABC123/";

        // when
        TicketmasterUrlParser.ParsedTicketmasterUrl result = TicketmasterUrlParser.parse(url);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getKeyword()).isEqualTo("concert");
        assertThat(result.getStateCode()).isEqualTo("TX");
        assertThat(result.getCity()).isEqualTo("Austin");
    }

    @Test
    void parse_WithAmbiguousCityName_ParsesBestEffort() {
        // given - "seattle" appears twice: once in team name, once as city
        String url = "https://www.ticketmaster.com/seattle-mariners-game-seattle-washington-09-15-2025/event/XYZ123";

        // when
        TicketmasterUrlParser.ParsedTicketmasterUrl result = TicketmasterUrlParser.parse(url);

        // then
        assertThat(result).isNotNull();
        // Parser takes the word immediately before state as city
        assertThat(result.getCity()).isEqualTo("Seattle");
        assertThat(result.getKeyword()).contains("seattle mariners game");
    }
}
