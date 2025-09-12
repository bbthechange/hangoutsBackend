# Implementation Plan: External URL Event Parsing

This document outlines the step-by-step plan to implement a feature that parses event details from a given URL. The feature will be exposed via a secure API endpoint, `POST /api/external/parse`.

---

### **Step 1: Add Dependencies to `build.gradle`**

Two new libraries are required: `Jsoup` for HTML parsing and `Apache Commons Net` for security checks.

**File to modify:** `build.gradle`

**Action:** Add the following lines inside the `dependencies { ... }` block:

```groovy
// For parsing schema.org data from HTML
implementation 'org.jsoup:jsoup:1.15.3'

// For security checks (IP address validation against CIDR blocks)
implementation 'commons-net:commons-net:3.9.0'
```

---

### **Step 2: Create DTOs for the API Endpoint**

We need one DTO for the request and another for the response.

**File to create:** `src/main/java/com/bbthechange/inviter/dto/ParseUrlRequest.java`

```java
package com.bbthechange.inviter.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

@Data
public class ParseUrlRequest {
    @NotBlank
    @URL(protocol = "https", message = "URL must use https")
    private String url;
}
```

**File to create:** `src/main/java/com/bbthechange/inviter/dto/ParsedEventDetailsDto.java`

```java
package com.bbthechange.inviter.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedEventDetailsDto {
    private String title;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Address location;
    private String imageUrl;
    private String sourceUrl;
}
```

---

### **Step 3: Configure the Secure HTTP Client**

A dedicated `RestTemplate` with strict timeouts is essential to prevent Denial of Service attacks.

**File to create:** `src/main/java/com/bbthechange/inviter/config/HttpClientConfig.java`

```java
package com.bbthechange.inviter.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import java.time.Duration;

@Configuration
public class HttpClientConfig {

    @Bean("externalRestTemplate")
    public RestTemplate externalRestTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5)) // 5-second connection timeout
            .setReadTimeout(Duration.ofSeconds(5))    // 5-second read timeout
            .build();
    }
}
```

---

### **Step 4: Implement the Core Logic Service**

This service will contain the URL validation, content fetching, and parsing logic. We will also create a custom exception for cleaner error handling.

**File to create:** `src/main/java/com/bbthechange/inviter/exception/EventParseException.java`

```java
package com.bbthechange.inviter.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.UNPROCESSABLE_ENTITY)
public class EventParseException extends RuntimeException {
    public EventParseException(String message) {
        super(message);
    }

    public EventParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

**File to create:** `src/main/java/com/bbthechange/inviter/service/ExternalEventService.java`

```java
package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.Address;
import com.bbthechange.inviter.dto.ParsedEventDetailsDto;
import com.bbthechange.inviter.exception.EventParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.net.util.SubnetUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class ExternalEventService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final List<String> BLOCKED_CIDR = List.of(
        "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", // Private networks
        "127.0.0.0/8", // Loopback
        "169.254.0.0/16" // Link-local & AWS Metadata
    );

    public ExternalEventService(@Qualifier("externalRestTemplate") RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public ParsedEventDetailsDto parseUrl(String urlString) {
        validateUrlIsSafe(urlString);

        try {
            String htmlContent = restTemplate.getForObject(urlString, String.class);
            if (htmlContent == null) {
                throw new EventParseException("Failed to fetch content from URL.");
            }

            Document doc = Jsoup.parse(htmlContent);
            Element scriptElement = doc.select("script[type=application/ld+json]").first();

            if (scriptElement == null) {
                throw new EventParseException("Could not find schema.org event data on the page.");
            }

            String jsonData = scriptElement.data();
            Map<String, Object> eventMap = objectMapper.readValue(jsonData, Map.class);

            return mapToDto(eventMap, urlString);

        } catch (Exception e) {
            throw new EventParseException("Failed to parse event details: " + e.getMessage(), e);
        }
    }

    private void validateUrlIsSafe(String urlString) {
        try {
            URL url = new URL(urlString);
            if (!"https".equalsIgnoreCase(url.getProtocol())) {
                throw new SecurityException("URL must use https.");
            }

            InetAddress address = InetAddress.getByName(url.getHost());
            String ipAddress = address.getHostAddress();

            for (String cidr : BLOCKED_CIDR) {
                if (new SubnetUtils(cidr).getInfo().isInRange(ipAddress)) {
                    throw new SecurityException("URL points to a blocked internal or reserved IP address.");
                }
            }
        } catch (Exception e) {
            throw new SecurityException("URL validation failed: " + e.getMessage(), e);
        }
    }

    private ParsedEventDetailsDto mapToDto(Map<String, Object> map, String sourceUrl) {
        String name = (String) map.get("name");
        String description = (String) map.get("description");
        String imageUrl = (String) map.get("image");
        
        LocalDateTime startTime = parseDate(map.get("startDate"));
        LocalDateTime endTime = parseDate(map.get("endDate"));

        Address address = null;
        if (map.get("location") instanceof Map) {
            Map<String, Object> locationMap = (Map<String, Object>) map.get("location");
            if (locationMap.get("address") instanceof Map) {
                 Map<String, Object> addressMap = (Map<String, Object>) locationMap.get("address");
                 address = Address.builder()
                    .street((String) addressMap.get("streetAddress"))
                    .city((String) addressMap.get("addressLocality"))
                    .state((String) addressMap.get("addressRegion"))
                    .zipCode((String) addressMap.get("postalCode"))
                    .build();
            }
        }

        return ParsedEventDetailsDto.builder()
            .title(name)
            .description(description)
            .startTime(startTime)
            .endTime(endTime)
            .location(address)
            .imageUrl(imageUrl)
            .sourceUrl(sourceUrl)
            .build();
    }
    
    private LocalDateTime parseDate(Object dateObj) {
        if (dateObj instanceof String) {
            try {
                return LocalDateTime.parse((String) dateObj, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            } catch (Exception e) {
                 try {
                    return LocalDateTime.parse((String) dateObj, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                 } catch (Exception e2) { return null; }
            }
        }
        return null;
    }
}
```

---

### **Step 5: Create the Controller**

This controller exposes the functionality via the new `/api/external/parse` endpoint.

**File to create:** `src/main/java/com/bbthechange/inviter/controller/ExternalController.java`

```java
package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.ParseUrlRequest;
import com.bbthechange.inviter.dto.ParsedEventDetailsDto;
import com.bbthechange.inviter.exception.EventParseException;
import com.bbthechange.inviter.service.ExternalEventService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/external")
public class ExternalController {

    private final ExternalEventService externalEventService;

    public ExternalController(ExternalEventService externalEventService) {
        this.externalEventService = externalEventService;
    }

    @PostMapping("/parse")
    public ResponseEntity<ParsedEventDetailsDto> parseEventDetails(@Valid @RequestBody ParseUrlRequest request) {
        try {
            ParsedEventDetailsDto eventDetails = externalEventService.parseUrl(request.getUrl());
            return ResponseEntity.ok(eventDetails);
        } catch (SecurityException e) {
            throw new EventParseException("URL is not valid or is blocked for security reasons.");
        }
    }
}
```

---

### **Step 6: Secure the Endpoint**

The endpoint must be protected to ensure only authenticated users can use it.

**File to modify:** `src/main/java/com/bbthechange/inviter/config/SecurityConfig.java`

**Action:** No change is required. The existing `.anyRequest().authenticated()` rule in the `SecurityFilterChain` already covers our new endpoint (`/api/external/parse`), ensuring it is protected by default. This is the desired behavior.
