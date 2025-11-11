package com.bbthechange.inviter.service.ticketmaster;

import com.bbthechange.inviter.dto.ParsedEventDetailsDto;
import com.bbthechange.inviter.exception.EventParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketmasterApiServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private ObjectMapper objectMapper = new ObjectMapper();

    private TicketmasterApiService service;

    // Test data constants
    private static final String VALID_API_RESPONSE = """
        {
          "_embedded": {
            "events": [
              {
                "name": "Florence + The Machine",
                "url": "https://www.ticketmaster.com/florence-the-machine-tickets/artist/1031116",
                "info": "An amazing concert experience",
                "dates": {
                  "start": {
                    "localDate": "2026-05-12",
                    "dateTime": "2026-05-13T02:30:00Z"
                  }
                },
                "images": [
                  {"url": "https://example.com/image.jpg"}
                ],
                "_embedded": {
                  "venues": [
                    {
                      "name": "Climate Pledge Arena",
                      "address": {"line1": "334 1st Avenue N"},
                      "city": {"name": "Seattle"},
                      "state": {"stateCode": "WA"},
                      "postalCode": "98109",
                      "country": {"countryCode": "US"}
                    }
                  ]
                }
              }
            ]
          }
        }
        """;

    private static final String API_ERROR_RESPONSE = """
        {
          "errors": [
            {
              "code": "DIS1004",
              "detail": "Invalid API key"
            }
          ]
        }
        """;

    private static final String EMPTY_EVENTS_RESPONSE = """
        {
          "_embedded": {
            "events": []
          }
        }
        """;

    private static final String NO_EMBEDDED_RESPONSE = """
        {
          "page": {"size": 5, "totalElements": 0}
        }
        """;

    private static final String MINIMAL_EVENT_RESPONSE = """
        {
          "_embedded": {
            "events": [
              {
                "name": "Test Event",
                "dates": {
                  "start": {
                    "localDate": "2026-05-12",
                    "dateTime": "2026-05-13T02:30:00Z"
                  }
                }
              }
            ]
          }
        }
        """;

    private static final String MULTIPLE_EVENTS_RESPONSE = """
        {
          "_embedded": {
            "events": [
              {
                "name": "Florence + The Machine - Seattle",
                "dates": {
                  "start": {
                    "dateTime": "2026-05-13T02:30:00Z"
                  }
                }
              },
              {
                "name": "Florence + The Machine - Portland",
                "dates": {
                  "start": {
                    "dateTime": "2026-05-14T02:30:00Z"
                  }
                }
              },
              {
                "name": "Florence + The Machine - Vancouver",
                "dates": {
                  "start": {
                    "dateTime": "2026-05-15T02:30:00Z"
                  }
                }
              }
            ]
          }
        }
        """;

    @BeforeEach
    void setUp() {
        service = new TicketmasterApiService(restTemplate, objectMapper, "test-api-key");
    }

    @Test
    void searchEvent_WithValidParsedUrl_ReturnsEventDetails() {
        // Given
        TicketmasterUrlParser.ParsedTicketmasterUrl parsedUrl = TicketmasterUrlParser.ParsedTicketmasterUrl.builder()
            .keyword("florence machine")
            .stateCode("WA")
            .city("Seattle")
            .eventDate(LocalDate.of(2026, 5, 12))
            .originalSlug("florence-machine-seattle-washington-05-12-2026")
            .build();

        ResponseEntity<String> response = new ResponseEntity<>(VALID_API_RESPONSE, HttpStatus.OK);
        when(restTemplate.getForEntity(any(URI.class), eq(String.class))).thenReturn(response);

        // When
        ParsedEventDetailsDto result = service.searchEvent(parsedUrl);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Florence + The Machine");
        assertThat(result.getUrl()).isEqualTo("https://www.ticketmaster.com/florence-the-machine-tickets/artist/1031116");
        assertThat(result.getDescription()).isEqualTo("An amazing concert experience");
        assertThat(result.getStartTime()).isEqualTo(OffsetDateTime.parse("2026-05-13T02:30:00Z"));
        assertThat(result.getImageUrl()).isEqualTo("https://example.com/image.jpg");
        assertThat(result.getLocation()).isNotNull();
        assertThat(result.getLocation().getName()).isEqualTo("Climate Pledge Arena");
        assertThat(result.getLocation().getStreetAddress()).isEqualTo("334 1st Avenue N");
        assertThat(result.getLocation().getCity()).isEqualTo("Seattle");
        assertThat(result.getLocation().getState()).isEqualTo("WA");
        assertThat(result.getLocation().getPostalCode()).isEqualTo("98109");
        assertThat(result.getLocation().getCountry()).isEqualTo("US");

        verify(restTemplate).getForEntity(any(URI.class), eq(String.class));
    }

    @Test
    void searchEvent_WithMissingApiKey_ThrowsException() {
        // Given
        TicketmasterApiService serviceWithoutKey = new TicketmasterApiService(restTemplate, objectMapper, "");
        TicketmasterUrlParser.ParsedTicketmasterUrl parsedUrl = TicketmasterUrlParser.ParsedTicketmasterUrl.builder()
            .keyword("test event")
            .build();

        // When/Then
        assertThatThrownBy(() -> serviceWithoutKey.searchEvent(parsedUrl))
            .isInstanceOf(EventParseException.class)
            .hasMessageContaining("API key is not configured");

        verify(restTemplate, never()).getForEntity(any(URI.class), eq(String.class));
    }

    @Test
    void searchEvent_WithNullKeyword_ThrowsException() {
        // Given
        TicketmasterUrlParser.ParsedTicketmasterUrl parsedUrl = TicketmasterUrlParser.ParsedTicketmasterUrl.builder()
            .keyword(null)
            .build();

        // When/Then
        assertThatThrownBy(() -> service.searchEvent(parsedUrl))
            .isInstanceOf(EventParseException.class)
            .hasMessageContaining("no keyword extracted from URL");

        verify(restTemplate, never()).getForEntity(any(URI.class), eq(String.class));
    }

    @Test
    void searchEvent_WithBlankKeyword_ThrowsException() {
        // Given
        TicketmasterUrlParser.ParsedTicketmasterUrl parsedUrl = TicketmasterUrlParser.ParsedTicketmasterUrl.builder()
            .keyword("")
            .build();

        // When/Then
        assertThatThrownBy(() -> service.searchEvent(parsedUrl))
            .isInstanceOf(EventParseException.class)
            .hasMessageContaining("no keyword extracted from URL");

        verify(restTemplate, never()).getForEntity(any(URI.class), eq(String.class));
    }

    @Test
    void searchEvent_WhenApiReturnsError_ThrowsException() {
        // Given
        TicketmasterUrlParser.ParsedTicketmasterUrl parsedUrl = TicketmasterUrlParser.ParsedTicketmasterUrl.builder()
            .keyword("test event")
            .build();

        ResponseEntity<String> response = new ResponseEntity<>(API_ERROR_RESPONSE, HttpStatus.OK);
        when(restTemplate.getForEntity(any(URI.class), eq(String.class))).thenReturn(response);

        // When/Then
        assertThatThrownBy(() -> service.searchEvent(parsedUrl))
            .isInstanceOf(EventParseException.class)
            .hasMessageContaining("Invalid API key");

        verify(restTemplate).getForEntity(any(URI.class), eq(String.class));
    }

    @Test
    void searchEvent_WhenNoEventsFound_ThrowsException() {
        // Given
        TicketmasterUrlParser.ParsedTicketmasterUrl parsedUrl = TicketmasterUrlParser.ParsedTicketmasterUrl.builder()
            .keyword("nonexistent event")
            .originalSlug("nonexistent-event")
            .build();

        ResponseEntity<String> response = new ResponseEntity<>(EMPTY_EVENTS_RESPONSE, HttpStatus.OK);
        when(restTemplate.getForEntity(any(URI.class), eq(String.class))).thenReturn(response);

        // When/Then
        assertThatThrownBy(() -> service.searchEvent(parsedUrl))
            .isInstanceOf(EventParseException.class)
            .hasMessageContaining("No events found matching URL");

        verify(restTemplate).getForEntity(any(URI.class), eq(String.class));
    }

    @Test
    void searchEvent_WhenNoEmbeddedField_ThrowsException() {
        // Given
        TicketmasterUrlParser.ParsedTicketmasterUrl parsedUrl = TicketmasterUrlParser.ParsedTicketmasterUrl.builder()
            .keyword("test event")
            .originalSlug("test-event")
            .build();

        ResponseEntity<String> response = new ResponseEntity<>(NO_EMBEDDED_RESPONSE, HttpStatus.OK);
        when(restTemplate.getForEntity(any(URI.class), eq(String.class))).thenReturn(response);

        // When/Then
        assertThatThrownBy(() -> service.searchEvent(parsedUrl))
            .isInstanceOf(EventParseException.class)
            .hasMessageContaining("No events found matching URL");

        verify(restTemplate).getForEntity(any(URI.class), eq(String.class));
    }

    @Test
    void searchEvent_WithDateFilter_WidensDateRange() {
        // Given
        TicketmasterUrlParser.ParsedTicketmasterUrl parsedUrl = TicketmasterUrlParser.ParsedTicketmasterUrl.builder()
            .keyword("test event")
            .eventDate(LocalDate.of(2026, 5, 12))
            .build();

        ResponseEntity<String> response = new ResponseEntity<>(VALID_API_RESPONSE, HttpStatus.OK);
        when(restTemplate.getForEntity(any(URI.class), eq(String.class))).thenReturn(response);

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);

        // When
        service.searchEvent(parsedUrl);

        // Then
        verify(restTemplate).getForEntity(uriCaptor.capture(), eq(String.class));
        URI capturedUri = uriCaptor.getValue();
        String uriString = capturedUri.toString();

        assertThat(uriString).contains("startDateTime=2026-05-11T00:00:00Z");
        assertThat(uriString).contains("endDateTime=2026-05-13T23:59:59Z");
    }

    @Test
    void searchEvent_WithStateCodeFilter_IncludesInUri() {
        // Given
        TicketmasterUrlParser.ParsedTicketmasterUrl parsedUrl = TicketmasterUrlParser.ParsedTicketmasterUrl.builder()
            .keyword("test event")
            .stateCode("WA")
            .build();

        ResponseEntity<String> response = new ResponseEntity<>(VALID_API_RESPONSE, HttpStatus.OK);
        when(restTemplate.getForEntity(any(URI.class), eq(String.class))).thenReturn(response);

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);

        // When
        service.searchEvent(parsedUrl);

        // Then
        verify(restTemplate).getForEntity(uriCaptor.capture(), eq(String.class));
        URI capturedUri = uriCaptor.getValue();
        assertThat(capturedUri.toString()).contains("stateCode=WA");
    }

    @Test
    void searchEvent_WithCityFilter_IncludesInUri() {
        // Given
        TicketmasterUrlParser.ParsedTicketmasterUrl parsedUrl = TicketmasterUrlParser.ParsedTicketmasterUrl.builder()
            .keyword("test event")
            .city("Seattle")
            .build();

        ResponseEntity<String> response = new ResponseEntity<>(VALID_API_RESPONSE, HttpStatus.OK);
        when(restTemplate.getForEntity(any(URI.class), eq(String.class))).thenReturn(response);

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);

        // When
        service.searchEvent(parsedUrl);

        // Then
        verify(restTemplate).getForEntity(uriCaptor.capture(), eq(String.class));
        URI capturedUri = uriCaptor.getValue();
        assertThat(capturedUri.toString()).contains("city=Seattle");
    }

    @Test
    void searchEvent_WithoutOptionalFilters_OnlyIncludesKeyword() {
        // Given
        TicketmasterUrlParser.ParsedTicketmasterUrl parsedUrl = TicketmasterUrlParser.ParsedTicketmasterUrl.builder()
            .keyword("test event")
            .build();

        ResponseEntity<String> response = new ResponseEntity<>(VALID_API_RESPONSE, HttpStatus.OK);
        when(restTemplate.getForEntity(any(URI.class), eq(String.class))).thenReturn(response);

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);

        // When
        service.searchEvent(parsedUrl);

        // Then
        verify(restTemplate).getForEntity(uriCaptor.capture(), eq(String.class));
        URI capturedUri = uriCaptor.getValue();
        String uriString = capturedUri.toString();

        assertThat(uriString).contains("keyword=test%20event");
        assertThat(uriString).contains("size=5");
        assertThat(uriString).doesNotContain("stateCode");
        assertThat(uriString).doesNotContain("city");
        assertThat(uriString).doesNotContain("startDateTime");
        assertThat(uriString).doesNotContain("endDateTime");
    }

    @Test
    void mapEventToDto_WithCompleteEventData_MapsAllFields() {
        // Given
        TicketmasterUrlParser.ParsedTicketmasterUrl parsedUrl = TicketmasterUrlParser.ParsedTicketmasterUrl.builder()
            .keyword("florence machine")
            .build();

        ResponseEntity<String> response = new ResponseEntity<>(VALID_API_RESPONSE, HttpStatus.OK);
        when(restTemplate.getForEntity(any(URI.class), eq(String.class))).thenReturn(response);

        // When
        ParsedEventDetailsDto result = service.searchEvent(parsedUrl);

        // Then
        assertThat(result.getTitle()).isEqualTo("Florence + The Machine");
        assertThat(result.getUrl()).isEqualTo("https://www.ticketmaster.com/florence-the-machine-tickets/artist/1031116");
        assertThat(result.getDescription()).isEqualTo("An amazing concert experience");
        assertThat(result.getStartTime()).isEqualTo(OffsetDateTime.parse("2026-05-13T02:30:00Z"));
        assertThat(result.getImageUrl()).isEqualTo("https://example.com/image.jpg");
        assertThat(result.getLocation().getName()).isEqualTo("Climate Pledge Arena");
        assertThat(result.getLocation().getStreetAddress()).isEqualTo("334 1st Avenue N");
        assertThat(result.getLocation().getCity()).isEqualTo("Seattle");
        assertThat(result.getLocation().getState()).isEqualTo("WA");
        assertThat(result.getLocation().getPostalCode()).isEqualTo("98109");
        assertThat(result.getLocation().getCountry()).isEqualTo("US");
    }

    @Test
    void mapEventToDto_WithMissingOptionalFields_HandlesGracefully() {
        // Given
        TicketmasterUrlParser.ParsedTicketmasterUrl parsedUrl = TicketmasterUrlParser.ParsedTicketmasterUrl.builder()
            .keyword("test event")
            .build();

        ResponseEntity<String> response = new ResponseEntity<>(MINIMAL_EVENT_RESPONSE, HttpStatus.OK);
        when(restTemplate.getForEntity(any(URI.class), eq(String.class))).thenReturn(response);

        // When
        ParsedEventDetailsDto result = service.searchEvent(parsedUrl);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Test Event");
        assertThat(result.getStartTime()).isEqualTo(OffsetDateTime.parse("2026-05-13T02:30:00Z"));
        assertThat(result.getDescription()).isNull();
        assertThat(result.getImageUrl()).isNull();
        assertThat(result.getLocation()).isNull();
    }

    @Test
    void searchEvent_WithMultipleEvents_ReturnsFirstMatch() {
        // Given
        TicketmasterUrlParser.ParsedTicketmasterUrl parsedUrl = TicketmasterUrlParser.ParsedTicketmasterUrl.builder()
            .keyword("florence machine")
            .build();

        ResponseEntity<String> response = new ResponseEntity<>(MULTIPLE_EVENTS_RESPONSE, HttpStatus.OK);
        when(restTemplate.getForEntity(any(URI.class), eq(String.class))).thenReturn(response);

        // When
        ParsedEventDetailsDto result = service.searchEvent(parsedUrl);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Florence + The Machine - Seattle");
        assertThat(result.getStartTime()).isEqualTo(OffsetDateTime.parse("2026-05-13T02:30:00Z"));

        verify(restTemplate).getForEntity(any(URI.class), eq(String.class));
    }
}
