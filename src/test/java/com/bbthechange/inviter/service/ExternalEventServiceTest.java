package com.bbthechange.inviter.service;

import com.bbthechange.inviter.config.ExternalParserProperties;
import com.bbthechange.inviter.dto.ParsedEventDetailsDto;
import com.bbthechange.inviter.exception.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalEventServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ExternalParserProperties properties;

    private ObjectMapper objectMapper;
    private ExternalEventService externalEventService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        
        // Mock properties with default values using lenient stubbing
        lenient().when(properties.getMaxResponseSize()).thenReturn(DataSize.ofMegabytes(2));
        lenient().when(properties.getConnectionTimeout()).thenReturn(Duration.ofSeconds(5));
        lenient().when(properties.getReadTimeout()).thenReturn(Duration.ofSeconds(10));
        lenient().when(properties.getMaxRedirects()).thenReturn(3);
        
        externalEventService = new ExternalEventService(restTemplate, objectMapper, properties);
    }

    @Test
    void parseUrl_WithValidEventbriteUrl_ReturnsEventDetails() {
        // Given
        String url = "https://eventbrite.com/event/123";
        String htmlContent = """
            <html>
            <script type="application/ld+json">
            {
                "@type": "Event",
                "name": "Test Concert",
                "description": "Amazing show",
                "url": "https://eventbrite.com/events/clean-url",
                "startDate": "2024-12-25T20:00:00",
                "endDate": "2024-12-25T23:00:00",
                "location": {
                    "name": "The Venue",
                    "address": {
                        "streetAddress": "123 Main St",
                        "addressLocality": "San Francisco",
                        "addressRegion": "CA",
                        "postalCode": "94102"
                    }
                },
                "offers": [
                    {
                        "@type": "Offer",
                        "name": "General Admission",
                        "price": 25.00,
                        "priceCurrency": "USD",
                        "url": "https://eventbrite.com/buy",
                        "availability": "InStock"
                    }
                ]
            }
            </script>
            </html>
            """;

        ResponseEntity<String> response = new ResponseEntity<>(htmlContent, HttpStatus.OK);
        when(restTemplate.exchange(eq(url), any(), any(), eq(String.class))).thenReturn(response);

        // When
        ParsedEventDetailsDto result = externalEventService.parseUrl(url);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Test Concert");
        assertThat(result.getDescription()).isEqualTo("Amazing show");
        assertThat(result.getUrl()).isEqualTo("https://eventbrite.com/events/clean-url");
        assertThat(result.getStartTime()).isNotNull();
        assertThat(result.getLocation()).isNotNull();
        assertThat(result.getLocation().getName()).isEqualTo("The Venue");
        assertThat(result.getLocation().getStreetAddress()).isEqualTo("123 Main St");
        assertThat(result.getTicketOffers()).hasSize(1);
        assertThat(result.getTicketOffers().get(0).getName()).isEqualTo("General Admission");
        assertThat(result.getTicketOffers().get(0).getPrice()).isEqualByComparingTo(new java.math.BigDecimal("25.00"));
        assertThat(result.getSourceUrl()).isEqualTo(url);
    }

    @Test
    void parseUrl_WithHttpUrl_ThrowsUnsafeUrlException() {
        // Given
        String url = "http://eventbrite.com/event/123";

        // When & Then
        assertThatThrownBy(() -> externalEventService.parseUrl(url))
            .isInstanceOf(UnsafeUrlException.class)
            .hasMessageContaining("must use HTTPS");
    }

    @Test
    void parseUrl_WithLocalhostUrl_ThrowsUnsafeUrlException() {
        // Given
        String url = "https://localhost:8080/event";

        // When & Then
        assertThatThrownBy(() -> externalEventService.parseUrl(url))
            .isInstanceOf(UnsafeUrlException.class)
            .hasMessageContaining("blocked domain");
    }

    @Test
    void parseUrl_WithPrivateIpUrl_ThrowsUnsafeUrlException() {
        // Given - This will fail DNS resolution in practice, but test the concept
        String url = "https://192.168.1.1/event";

        // When & Then
        assertThatThrownBy(() -> externalEventService.parseUrl(url))
            .isInstanceOf(UnsafeUrlException.class);
    }

    @Test
    void parseUrl_WithNoSchemaOrg_ThrowsSchemaNotFoundException() {
        // Given
        String url = "https://example.com/event";
        String htmlContent = "<html><body>No schema.org data here</body></html>";

        ResponseEntity<String> response = new ResponseEntity<>(htmlContent, HttpStatus.OK);
        when(restTemplate.exchange(eq(url), any(), any(), eq(String.class))).thenReturn(response);

        // When & Then
        assertThatThrownBy(() -> externalEventService.parseUrl(url))
            .isInstanceOf(SchemaNotFoundException.class)
            .hasMessageContaining("No schema.org structured data found");
    }

    @Test
    void parseUrl_WithInvalidSchemaOrg_ThrowsSchemaNotFoundException() {
        // Given
        String url = "https://example.com/event";
        String htmlContent = """
            <html>
            <script type="application/ld+json">
            {
                "@type": "Person",
                "name": "John Doe"
            }
            </script>
            </html>
            """;

        ResponseEntity<String> response = new ResponseEntity<>(htmlContent, HttpStatus.OK);
        when(restTemplate.exchange(eq(url), any(), any(), eq(String.class))).thenReturn(response);

        // When & Then
        assertThatThrownBy(() -> externalEventService.parseUrl(url))
            .isInstanceOf(SchemaNotFoundException.class)
            .hasMessageContaining("No valid event schema.org data found");
    }

    @Test
    void parseUrl_WithNetworkError_ThrowsNetworkException() {
        // Given
        String url = "https://example.com/event";
        when(restTemplate.exchange(eq(url), any(), any(), eq(String.class)))
            .thenThrow(new RestClientException("Connection timeout"));

        // When & Then
        assertThatThrownBy(() -> externalEventService.parseUrl(url))
            .isInstanceOf(NetworkException.class)
            .hasMessageContaining("Failed to fetch content from URL");
    }

    @Test
    void parseUrl_WithTooLargeResponse_ThrowsContentValidationException() {
        // Given
        String url = "https://example.com/event";
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_LENGTH, "3000000"); // 3MB > 2MB limit
        
        ResponseEntity<String> response = new ResponseEntity<>("content", headers, HttpStatus.OK);
        when(restTemplate.exchange(eq(url), any(), any(), eq(String.class))).thenReturn(response);

        // When & Then
        assertThatThrownBy(() -> externalEventService.parseUrl(url))
            .isInstanceOf(ContentValidationException.class)
            .hasMessageContaining("Response too large");
    }

    @Test
    void parseUrl_WithUnsupportedContentType_ThrowsContentValidationException() {
        // Given
        String url = "https://example.com/event";
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, "application/pdf");
        
        ResponseEntity<String> response = new ResponseEntity<>("content", headers, HttpStatus.OK);
        when(restTemplate.exchange(eq(url), any(), any(), eq(String.class))).thenReturn(response);

        // When & Then
        assertThatThrownBy(() -> externalEventService.parseUrl(url))
            .isInstanceOf(ContentValidationException.class)
            .hasMessageContaining("Unsupported content type");
    }

    @Test
    void parseUrl_WithArrayOfSchemaObjects_ParsesFirstEvent() {
        // Given
        String url = "https://example.com/events";
        String htmlContent = """
            <html>
            <script type="application/ld+json">
            [
                {
                    "@type": "Organization",
                    "name": "Event Company"
                },
                {
                    "@type": "Event",
                    "name": "First Event",
                    "startDate": "2024-12-25T20:00:00"
                }
            ]
            </script>
            </html>
            """;

        ResponseEntity<String> response = new ResponseEntity<>(htmlContent, HttpStatus.OK);
        when(restTemplate.exchange(eq(url), any(), any(), eq(String.class))).thenReturn(response);

        // When
        ParsedEventDetailsDto result = externalEventService.parseUrl(url);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("First Event");
    }

    @Test
    void parseUrl_WithStringAddress_ParsesCorrectly() {
        // Given
        String url = "https://example.com/event";
        String htmlContent = """
            <html>
            <script type="application/ld+json">
            {
                "@type": "Event",
                "name": "Street Address Event",
                "location": {
                    "address": "123 Simple Street, San Francisco, CA 94102"
                }
            }
            </script>
            </html>
            """;

        ResponseEntity<String> response = new ResponseEntity<>(htmlContent, HttpStatus.OK);
        when(restTemplate.exchange(eq(url), any(), any(), eq(String.class))).thenReturn(response);

        // When
        ParsedEventDetailsDto result = externalEventService.parseUrl(url);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getLocation()).isNotNull();
        assertThat(result.getLocation().getStreetAddress()).isEqualTo("123 Simple Street, San Francisco, CA 94102");
    }

    @Test
    void parseUrl_WithEmptyResponse_ThrowsNetworkException() {
        // Given
        String url = "https://example.com/event";
        ResponseEntity<String> response = new ResponseEntity<>("", HttpStatus.OK);
        when(restTemplate.exchange(eq(url), any(), any(), eq(String.class))).thenReturn(response);

        // When & Then
        assertThatThrownBy(() -> externalEventService.parseUrl(url))
            .isInstanceOf(NetworkException.class)
            .hasMessageContaining("empty response");
    }

    @Test
    void parseUrl_WithMultipleTicketOffers_ParsesAllOffers() {
        // Given
        String url = "https://eventbrite.com/event/123";
        String htmlContent = """
            <html>
            <script type="application/ld+json">
            {
                "@type": "Event",
                "name": "Multi-Tier Event",
                "offers": [
                    {
                        "@type": "AggregateOffer",
                        "lowPrice": 25.87,
                        "highPrice": 31.94
                    },
                    {
                        "@type": "Offer",
                        "name": "Early Bird GA",
                        "price": 25.87,
                        "priceCurrency": "USD",
                        "availability": "SoldOut"
                    },
                    {
                        "@type": "Offer",
                        "name": "Regular GA",
                        "price": 31.94,
                        "priceCurrency": "USD",
                        "availability": "InStock"
                    }
                ]
            }
            </script>
            </html>
            """;

        ResponseEntity<String> response = new ResponseEntity<>(htmlContent, HttpStatus.OK);
        when(restTemplate.exchange(eq(url), any(), any(), eq(String.class))).thenReturn(response);

        // When
        ParsedEventDetailsDto result = externalEventService.parseUrl(url);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTicketOffers()).hasSize(2); // AggregateOffer should be skipped
        assertThat(result.getTicketOffers().get(0).getName()).isEqualTo("Early Bird GA");
        assertThat(result.getTicketOffers().get(0).getAvailability()).isEqualTo("SoldOut");
        assertThat(result.getTicketOffers().get(1).getName()).isEqualTo("Regular GA");
        assertThat(result.getTicketOffers().get(1).getAvailability()).isEqualTo("InStock");
    }

    @Test
    void parseUrl_WithTimezoneInfo_PreservesTimezone() {
        // Given
        String url = "https://eventbrite.com/event/123";
        String htmlContent = """
            <html>
            <script type="application/ld+json">
            {
                "@type": "Event",
                "name": "Timezone Test Event",
                "startDate": "2024-12-25T20:00:00-05:00",
                "endDate": "2024-12-25T23:00:00-05:00"
            }
            </script>
            </html>
            """;

        ResponseEntity<String> response = new ResponseEntity<>(htmlContent, HttpStatus.OK);
        when(restTemplate.exchange(eq(url), any(), any(), eq(String.class))).thenReturn(response);

        // When
        ParsedEventDetailsDto result = externalEventService.parseUrl(url);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStartTime()).isNotNull();
        assertThat(result.getEndTime()).isNotNull();
        assertThat(result.getStartTime().getOffset().toString()).isEqualTo("-05:00");
        assertThat(result.getEndTime().getOffset().toString()).isEqualTo("-05:00");
        assertThat(result.getStartTime().getHour()).isEqualTo(20);
        assertThat(result.getEndTime().getHour()).isEqualTo(23);
    }

    @Test
    void parseUrl_WithUtcTimezone_PreservesUtc() {
        // Given
        String url = "https://eventbrite.com/event/123";
        String htmlContent = """
            <html>
            <script type="application/ld+json">
            {
                "@type": "Event",
                "name": "UTC Event",
                "startDate": "2024-12-25T20:00:00Z"
            }
            </script>
            </html>
            """;

        ResponseEntity<String> response = new ResponseEntity<>(htmlContent, HttpStatus.OK);
        when(restTemplate.exchange(eq(url), any(), any(), eq(String.class))).thenReturn(response);

        // When
        ParsedEventDetailsDto result = externalEventService.parseUrl(url);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStartTime()).isNotNull();
        assertThat(result.getStartTime().getOffset().toString()).isEqualTo("Z");
        assertThat(result.getStartTime().getHour()).isEqualTo(20);
    }

    @Test
    void parseUrl_WithNoTimezone_DefaultsToUtc() {
        // Given
        String url = "https://eventbrite.com/event/123";
        String htmlContent = """
            <html>
            <script type="application/ld+json">
            {
                "@type": "Event",
                "name": "Local Time Event",
                "startDate": "2024-12-25T20:00:00"
            }
            </script>
            </html>
            """;

        ResponseEntity<String> response = new ResponseEntity<>(htmlContent, HttpStatus.OK);
        when(restTemplate.exchange(eq(url), any(), any(), eq(String.class))).thenReturn(response);

        // When
        ParsedEventDetailsDto result = externalEventService.parseUrl(url);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStartTime()).isNotNull();
        assertThat(result.getStartTime().getOffset().toString()).isEqualTo("Z"); // Should default to UTC
        assertThat(result.getStartTime().getHour()).isEqualTo(20);
    }

    @Test
    void parseUrl_WithNoOffers_ReturnsEmptyTicketList() {
        // Given
        String url = "https://example.com/event";
        String htmlContent = """
            <html>
            <script type="application/ld+json">
            {
                "@type": "Event",
                "name": "Free Event"
            }
            </script>
            </html>
            """;

        ResponseEntity<String> response = new ResponseEntity<>(htmlContent, HttpStatus.OK);
        when(restTemplate.exchange(eq(url), any(), any(), eq(String.class))).thenReturn(response);

        // When
        ParsedEventDetailsDto result = externalEventService.parseUrl(url);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTicketOffers()).isEmpty();
    }
}