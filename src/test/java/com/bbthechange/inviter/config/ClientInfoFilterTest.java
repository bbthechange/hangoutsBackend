package com.bbthechange.inviter.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ClientInfoFilterTest {

    private ClientInfoFilter filter;

    @Mock
    private FilterChain filterChain;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new ClientInfoFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Nested
    class RequestAttributeHandling {

        @Test
        void setsClientInfoAttribute() throws ServletException, IOException {
            // Given
            request.addHeader("X-App-Version", "1.2.3");
            request.addHeader("X-Client-Type", "ios");

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            ClientInfo clientInfo = (ClientInfo) request.getAttribute(ClientInfo.REQUEST_ATTRIBUTE);
            assertThat(clientInfo).isNotNull();
            assertThat(clientInfo.appVersion()).isEqualTo("1.2.3");
            assertThat(clientInfo.clientType()).isEqualTo("ios");
        }

        @Test
        void continuesFilterChain() throws ServletException, IOException {
            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(request, response);
        }

        @Test
        void setsAttributeEvenWithNoHeaders() throws ServletException, IOException {
            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            ClientInfo clientInfo = (ClientInfo) request.getAttribute(ClientInfo.REQUEST_ATTRIBUTE);
            assertThat(clientInfo).isNotNull();
            assertThat(clientInfo.appVersion()).isNull();
        }
    }

    @Nested
    class MdcHandling {

        @Test
        void addsMdcContextDuringRequest() throws ServletException, IOException {
            // Given
            request.addHeader("X-App-Version", "1.2.3");
            request.addHeader("X-Build-Number", "45");
            request.addHeader("X-Client-Type", "ios");

            // Capture MDC values during filter chain execution
            final String[] capturedVersion = new String[1];
            final String[] capturedBuild = new String[1];
            final String[] capturedClientType = new String[1];
            final String[] capturedPlatform = new String[1];

            FilterChain capturingChain = (req, res) -> {
                capturedVersion[0] = MDC.get("appVersion");
                capturedBuild[0] = MDC.get("buildNumber");
                capturedClientType[0] = MDC.get("clientType");
                capturedPlatform[0] = MDC.get("platform");
            };

            // When
            filter.doFilterInternal(request, response, capturingChain);

            // Then
            assertThat(capturedVersion[0]).isEqualTo("1.2.3");
            assertThat(capturedBuild[0]).isEqualTo("45");
            assertThat(capturedClientType[0]).isEqualTo("ios");
            assertThat(capturedPlatform[0]).isEqualTo("ios");
        }

        @Test
        void clearsMdcAfterRequest() throws ServletException, IOException {
            // Given
            request.addHeader("X-App-Version", "1.2.3");
            request.addHeader("X-Client-Type", "ios");

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then - MDC should be cleared after filter completes
            assertThat(MDC.get("appVersion")).isNull();
            assertThat(MDC.get("buildNumber")).isNull();
            assertThat(MDC.get("clientType")).isNull();
            assertThat(MDC.get("platform")).isNull();
        }

        @Test
        void clearsMdcEvenOnException() throws ServletException, IOException {
            // Given
            request.addHeader("X-App-Version", "1.2.3");
            FilterChain throwingChain = (req, res) -> {
                throw new RuntimeException("Test exception");
            };

            // When
            try {
                filter.doFilterInternal(request, response, throwingChain);
            } catch (RuntimeException e) {
                // Expected
            }

            // Then - MDC should still be cleared
            assertThat(MDC.get("appVersion")).isNull();
            assertThat(MDC.get("clientType")).isNull();
        }

        @Test
        void skipsNullMdcValues() throws ServletException, IOException {
            // Given - no headers set

            // Capture MDC values during filter chain execution
            final boolean[] mdcChecked = {false};

            FilterChain capturingChain = (req, res) -> {
                // MDC.get returns null for keys that weren't set
                // We verify that null values aren't added to MDC
                mdcChecked[0] = true;
                assertThat(MDC.get("appVersion")).isNull();
                assertThat(MDC.get("buildNumber")).isNull();
            };

            // When
            filter.doFilterInternal(request, response, capturingChain);

            // Then
            assertThat(mdcChecked[0]).isTrue();
        }
    }
}
