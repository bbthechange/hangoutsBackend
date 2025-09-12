package com.bbthechange.inviter.service;

import com.bbthechange.inviter.config.ExternalParserProperties;
import com.bbthechange.inviter.dto.Address;
import com.bbthechange.inviter.dto.ParsedEventDetailsDto;
import com.bbthechange.inviter.dto.TicketOffer;
import com.bbthechange.inviter.exception.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.net.util.SubnetUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ExternalEventService {

    private static final Logger logger = LoggerFactory.getLogger(ExternalEventService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ExternalParserProperties properties;

    private static final List<String> BLOCKED_CIDR = List.of(
        "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", // Private networks
        "127.0.0.0/8", // Loopback
        "169.254.0.0/16", // Link-local & AWS Metadata
        "224.0.0.0/4", // Multicast
        "0.0.0.0/8" // Invalid addresses
    );

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "text/html", "application/xhtml+xml", "text/plain"
    );

    private static final Set<String> BLOCKED_DOMAINS = Set.of(
        "localhost", "127.0.0.1", "0.0.0.0", "::1"
    );

    public ExternalEventService(@Qualifier("externalRestTemplate") RestTemplate restTemplate, 
                               ObjectMapper objectMapper,
                               ExternalParserProperties properties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public ParsedEventDetailsDto parseUrl(String urlString) {
        logger.info("Parsing URL: {}", urlString);
        
        validateUrlIsSafe(urlString);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                urlString, 
                HttpMethod.GET, 
                null, 
                String.class
            );

            validateResponse(response);
            String htmlContent = response.getBody();
            
            if (htmlContent == null || htmlContent.isEmpty()) {
                throw new NetworkException("Received empty response from URL");
            }

            Document doc = Jsoup.parse(htmlContent);
            return parseSchemaOrgData(doc, urlString);

        } catch (RestClientException e) {
            logger.error("Network error fetching URL: {}", urlString, e);
            throw new NetworkException("Failed to fetch content from URL: " + e.getMessage(), e);
        } catch (SchemaNotFoundException | ContentValidationException | NetworkException e) {
            // Re-throw specific exceptions without wrapping
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error parsing URL: {}", urlString, e);
            throw new EventParseException("Failed to parse event details: " + e.getMessage(), e);
        }
    }

    private void validateUrlIsSafe(String urlString) {
        try {
            URL url = new URL(urlString);
            
            // Validate protocol
            if (!"https".equalsIgnoreCase(url.getProtocol())) {
                throw new UnsafeUrlException("URL must use HTTPS");
            }

            String host = url.getHost();
            if (host == null || host.isEmpty()) {
                throw new UnsafeUrlException("Invalid URL host");
            }

            // Check blocked domains
            if (BLOCKED_DOMAINS.contains(host.toLowerCase())) {
                throw new UnsafeUrlException("URL points to a blocked domain: " + host);
            }

            // Resolve and validate IP address
            InetAddress address;
            try {
                address = InetAddress.getByName(host);
            } catch (UnknownHostException e) {
                throw new UnsafeUrlException("Cannot resolve hostname: " + host);
            }

            String ipAddress = address.getHostAddress();
            logger.debug("Resolved {} to IP: {}", host, ipAddress);

            // Check against blocked CIDR ranges
            for (String cidr : BLOCKED_CIDR) {
                try {
                    if (new SubnetUtils(cidr).getInfo().isInRange(ipAddress)) {
                        throw new UnsafeUrlException("URL points to a blocked IP address range: " + ipAddress);
                    }
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid CIDR range: {}", cidr, e);
                }
            }

        } catch (MalformedURLException e) {
            throw new UnsafeUrlException("Invalid URL format: " + e.getMessage(), e);
        } catch (UnsafeUrlException e) {
            throw e;
        } catch (Exception e) {
            throw new UnsafeUrlException("URL validation failed: " + e.getMessage(), e);
        }
    }

    private void validateResponse(ResponseEntity<String> response) {
        // Check response size
        String contentLength = response.getHeaders().getFirst(HttpHeaders.CONTENT_LENGTH);
        if (contentLength != null) {
            try {
                long size = Long.parseLong(contentLength);
                if (size > properties.getMaxResponseSize().toBytes()) {
                    throw new ContentValidationException("Response too large: " + size + " bytes");
                }
            } catch (NumberFormatException e) {
                logger.warn("Invalid Content-Length header: {}", contentLength);
            }
        }

        // Check content type
        String contentType = response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
        if (contentType != null) {
            String primaryType = contentType.split(";")[0].trim().toLowerCase();
            if (!ALLOWED_CONTENT_TYPES.contains(primaryType)) {
                throw new ContentValidationException("Unsupported content type: " + primaryType);
            }
        }
    }

    private ParsedEventDetailsDto parseSchemaOrgData(Document doc, String sourceUrl) {
        Elements scriptElements = doc.select("script[type=application/ld+json]");
        
        if (scriptElements.isEmpty()) {
            throw new SchemaNotFoundException("No schema.org structured data found on the page");
        }

        // Try each script element until we find event data
        for (Element scriptElement : scriptElements) {
            try {
                String jsonData = scriptElement.data();
                if (jsonData.trim().isEmpty()) {
                    continue;
                }

                Object parsedData = objectMapper.readValue(jsonData, Object.class);
                
                // Handle both single object and array of objects
                if (parsedData instanceof List) {
                    List<?> dataList = (List<?>) parsedData;
                    for (Object item : dataList) {
                        if (item instanceof Map) {
                            ParsedEventDetailsDto result = tryMapToDto((Map<String, Object>) item, sourceUrl);
                            if (result != null) {
                                return result;
                            }
                        }
                    }
                } else if (parsedData instanceof Map) {
                    ParsedEventDetailsDto result = tryMapToDto((Map<String, Object>) parsedData, sourceUrl);
                    if (result != null) {
                        return result;
                    }
                }
            } catch (Exception e) {
                logger.debug("Failed to parse script element as event data: {}", e.getMessage());
                // Continue to next script element
            }
        }

        throw new SchemaNotFoundException("No valid event schema.org data found on the page");
    }

    private ParsedEventDetailsDto tryMapToDto(Map<String, Object> map, String sourceUrl) {
        String type = (String) map.get("@type");
        if (type == null || !type.toLowerCase().contains("event")) {
            return null;
        }

        try {
            return mapToDto(map, sourceUrl);
        } catch (Exception e) {
            logger.debug("Failed to map schema data to DTO: {}", e.getMessage());
            return null;
        }
    }

    private ParsedEventDetailsDto mapToDto(Map<String, Object> map, String sourceUrl) {
        String name = (String) map.get("name");
        String description = (String) map.get("description");
        String imageUrl = extractImageUrl(map.get("image"));
        String eventUrl = (String) map.get("url");
        
        OffsetDateTime startTime = parseDate(map.get("startDate"));
        OffsetDateTime endTime = parseDate(map.get("endDate"));

        Address address = extractAddress(map.get("location"));
        List<TicketOffer> ticketOffers = extractTicketOffers(map.get("offers"));

        return ParsedEventDetailsDto.builder()
            .title(name)
            .description(description)
            .startTime(startTime)
            .endTime(endTime)
            .location(address)
            .imageUrl(imageUrl)
            .url(eventUrl)
            .sourceUrl(sourceUrl)
            .ticketOffers(ticketOffers)
            .build();
    }

    private String extractImageUrl(Object imageObj) {
        if (imageObj instanceof String) {
            return (String) imageObj;
        } else if (imageObj instanceof Map) {
            Map<?, ?> imageMap = (Map<?, ?>) imageObj;
            Object url = imageMap.get("url");
            if (url instanceof String) {
                return (String) url;
            }
        } else if (imageObj instanceof List) {
            List<?> imageList = (List<?>) imageObj;
            if (!imageList.isEmpty()) {
                return extractImageUrl(imageList.get(0));
            }
        }
        return null;
    }

    private Address extractAddress(Object locationObj) {
        if (!(locationObj instanceof Map)) {
            return null;
        }

        Map<String, Object> locationMap = (Map<String, Object>) locationObj;
        String locationName = (String) locationMap.get("name");
        Object addressObj = locationMap.get("address");
        
        if (addressObj instanceof String) {
            // Simple string address
            Address address = new Address((String) addressObj);
            address.setName(locationName);
            return address;
        } else if (addressObj instanceof Map) {
            // Structured address
            Map<String, Object> addressMap = (Map<String, Object>) addressObj;
            Address address = new Address(
                locationName,
                (String) addressMap.get("streetAddress"),
                (String) addressMap.get("addressLocality"),
                (String) addressMap.get("addressRegion"),
                (String) addressMap.get("postalCode"),
                (String) addressMap.get("addressCountry")
            );
            return address;
        }
        
        return null;
    }

    private List<TicketOffer> extractTicketOffers(Object offersObj) {
        List<TicketOffer> ticketOffers = new ArrayList<>();
        
        if (!(offersObj instanceof List)) {
            return ticketOffers;
        }

        List<?> offersList = (List<?>) offersObj;
        for (Object offerObj : offersList) {
            if (!(offerObj instanceof Map)) {
                continue;
            }

            Map<String, Object> offerMap = (Map<String, Object>) offerObj;
            String type = (String) offerMap.get("@type");
            
            // Skip AggregateOffer as it's a summary, focus on individual Offer objects
            if ("AggregateOffer".equals(type)) {
                continue;
            }

            String name = (String) offerMap.get("name");
            String url = (String) offerMap.get("url");
            String priceCurrency = (String) offerMap.get("priceCurrency");
            String availability = (String) offerMap.get("availability");
            
            BigDecimal price = null;
            Object priceObj = offerMap.get("price");
            if (priceObj instanceof Number) {
                price = new BigDecimal(priceObj.toString());
            } else if (priceObj instanceof String) {
                try {
                    price = new BigDecimal((String) priceObj);
                } catch (NumberFormatException e) {
                    logger.debug("Could not parse price: {}", priceObj);
                }
            }

            TicketOffer ticketOffer = TicketOffer.builder()
                .name(name)
                .url(url)
                .price(price)
                .priceCurrency(priceCurrency)
                .availability(availability)
                .build();
            
            ticketOffers.add(ticketOffer);
        }
        
        return ticketOffers;
    }
    
    private OffsetDateTime parseDate(Object dateObj) {
        if (!(dateObj instanceof String)) {
            return null;
        }

        String dateStr = (String) dateObj;
        
        // First try to parse with timezone information
        try {
            // Try OffsetDateTime first (handles +00:00, Z, etc.)
            return OffsetDateTime.parse(dateStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (DateTimeParseException e) {
            // Continue to other formats
        }
        
        try {
            // Try ZonedDateTime and convert to OffsetDateTime
            ZonedDateTime zonedDateTime = ZonedDateTime.parse(dateStr, DateTimeFormatter.ISO_ZONED_DATE_TIME);
            return zonedDateTime.toOffsetDateTime();
        } catch (DateTimeParseException e) {
            // Continue to other formats
        }
        
        // Try custom patterns with timezone
        DateTimeFormatter[] offsetFormatters = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")
        };

        for (DateTimeFormatter formatter : offsetFormatters) {
            try {
                return OffsetDateTime.parse(dateStr, formatter);
            } catch (DateTimeParseException e) {
                // Try next formatter
            }
        }
        
        // Fallback to LocalDateTime patterns (without timezone) and assume UTC
        DateTimeFormatter[] localFormatters = {
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ISO_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        };

        for (DateTimeFormatter formatter : localFormatters) {
            try {
                LocalDateTime localDateTime = LocalDateTime.parse(dateStr, formatter);
                // Assume UTC if no timezone information is provided
                return localDateTime.atOffset(java.time.ZoneOffset.UTC);
            } catch (DateTimeParseException e) {
                // Try next formatter
            }
        }

        logger.warn("Could not parse date: {}", dateStr);
        return null;
    }
}