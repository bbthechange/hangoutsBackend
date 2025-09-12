package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.Address;
import com.bbthechange.inviter.dto.ParseUrlRequest;
import com.bbthechange.inviter.dto.ParsedEventDetailsDto;
import com.bbthechange.inviter.exception.SchemaNotFoundException;
import com.bbthechange.inviter.exception.UnsafeUrlException;
import com.bbthechange.inviter.service.ExternalEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalControllerTest {

    @Mock
    private ExternalEventService externalEventService;

    @InjectMocks
    private ExternalController externalController;

    @Test
    void parseEventDetails_WithValidUrl_ReturnsEventDetails() {
        // Given
        String url = "https://eventbrite.com/event/123";
        Address address = new Address("Venue Name", "123 Main St", "San Francisco", "CA", "94102", "USA");
        ParsedEventDetailsDto expectedDto = ParsedEventDetailsDto.builder()
            .title("Test Concert")
            .description("Amazing show")
            .startTime(LocalDateTime.of(2024, 12, 25, 20, 0))
            .location(address)
            .url("https://clean.url/event")
            .sourceUrl(url)
            .ticketOffers(List.of())
            .build();

        when(externalEventService.parseUrl(url)).thenReturn(expectedDto);

        // When
        ParseUrlRequest request = new ParseUrlRequest();
        request.setUrl(url);
        ResponseEntity<ParsedEventDetailsDto> response = externalController.parseEventDetails(request);

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Test Concert");
        assertThat(response.getBody().getDescription()).isEqualTo("Amazing show");
        assertThat(response.getBody().getUrl()).isEqualTo("https://clean.url/event");
        assertThat(response.getBody().getSourceUrl()).isEqualTo(url);
        assertThat(response.getBody().getTicketOffers()).isEmpty();
    }

    @Test
    void parseEventDetails_WithUnsafeUrl_ThrowsUnsafeUrlException() {
        // Given
        String url = "https://localhost:8080/event";
        ParseUrlRequest request = new ParseUrlRequest();
        request.setUrl(url);
        
        when(externalEventService.parseUrl(url)).thenThrow(new UnsafeUrlException("URL is blocked"));

        // When & Then
        assertThatThrownBy(() -> externalController.parseEventDetails(request))
            .isInstanceOf(UnsafeUrlException.class)
            .hasMessageContaining("URL is blocked");
    }

    @Test
    void parseEventDetails_WithNoSchemaFound_ThrowsSchemaNotFoundException() {
        // Given
        String url = "https://example.com/event";
        ParseUrlRequest request = new ParseUrlRequest();
        request.setUrl(url);
        
        when(externalEventService.parseUrl(url)).thenThrow(new SchemaNotFoundException("No schema found"));

        // When & Then
        assertThatThrownBy(() -> externalController.parseEventDetails(request))
            .isInstanceOf(SchemaNotFoundException.class)
            .hasMessageContaining("No schema found");
    }
}