package com.bbthechange.inviter.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternalApiKeyFilterTest {

    @Mock
    private SsmClient ssmClient;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private InternalApiKeyFilter filter;

    private static final String VALID_API_KEY = "test-api-key-12345";
    private static final String API_KEY_HEADER = "X-Api-Key";

    @BeforeEach
    void setUp() {
        filter = new InternalApiKeyFilter(ssmClient);
        ReflectionTestUtils.setField(filter, "apiKeyParameterName", "/inviter/scheduler/internal-api-key");
    }

    // ==================== shouldNotFilter Tests ====================

    @Nested
    class ShouldNotFilterTests {

        @Test
        void shouldNotFilter_NonInternalPath_ReturnsTrue() {
            // Given: Request to a non-internal path
            when(request.getRequestURI()).thenReturn("/api/hangouts");

            // When
            boolean result = filter.shouldNotFilter(request);

            // Then: Should skip filtering (return true)
            assertThat(result).isTrue();
        }

        @Test
        void shouldNotFilter_InternalPath_ReturnsFalse() {
            // Given: Request to an internal path
            when(request.getRequestURI()).thenReturn("/internal/reminders/hangouts");

            // When
            boolean result = filter.shouldNotFilter(request);

            // Then: Should NOT skip filtering (return false)
            assertThat(result).isFalse();
        }

        @Test
        void shouldNotFilter_RootPath_ReturnsTrue() {
            // Given: Request to root path
            when(request.getRequestURI()).thenReturn("/");

            // When
            boolean result = filter.shouldNotFilter(request);

            // Then: Should skip filtering
            assertThat(result).isTrue();
        }

        @Test
        void shouldNotFilter_InternalExactPath_ReturnsFalse() {
            // Given: Request to exact /internal/ path
            when(request.getRequestURI()).thenReturn("/internal/");

            // When
            boolean result = filter.shouldNotFilter(request);

            // Then: Should NOT skip filtering
            assertThat(result).isFalse();
        }
    }

    // ==================== doFilterInternal Tests ====================

    @Nested
    class DoFilterInternalTests {

        private StringWriter responseWriter;

        @BeforeEach
        void setUpResponse() throws Exception {
            responseWriter = new StringWriter();
            lenient().when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
        }

        @Test
        void doFilterInternal_MissingApiKeyHeader_Returns401() throws Exception {
            // Given: No API key header
            when(request.getHeader(API_KEY_HEADER)).thenReturn(null);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then: 401 Unauthorized
            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            verify(response).setContentType("application/json");
            assertThat(responseWriter.toString()).contains("Missing API key");
            verify(filterChain, never()).doFilter(request, response);
        }

        @Test
        void doFilterInternal_BlankApiKeyHeader_Returns401() throws Exception {
            // Given: Blank API key header
            when(request.getHeader(API_KEY_HEADER)).thenReturn("   ");

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then: 401 Unauthorized
            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            verify(response).setContentType("application/json");
            assertThat(responseWriter.toString()).contains("Missing API key");
            verify(filterChain, never()).doFilter(request, response);
        }

        @Test
        void doFilterInternal_SsmClientNull_Returns500() throws Exception {
            // Given: Filter with null SSM client
            InternalApiKeyFilter filterWithoutSsm = new InternalApiKeyFilter(null);
            when(request.getHeader(API_KEY_HEADER)).thenReturn(VALID_API_KEY);

            // When
            filterWithoutSsm.doFilterInternal(request, response, filterChain);

            // Then: 500 Internal Server Error
            verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            verify(response).setContentType("application/json");
            assertThat(responseWriter.toString()).contains("Internal configuration error");
            verify(filterChain, never()).doFilter(request, response);
        }

        @Test
        void doFilterInternal_InvalidApiKey_Returns401() throws Exception {
            // Given: Valid API key header but doesn't match expected
            when(request.getHeader(API_KEY_HEADER)).thenReturn("wrong-api-key");
            setupSsmToReturnApiKey(VALID_API_KEY);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then: 401 Unauthorized
            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            verify(response).setContentType("application/json");
            assertThat(responseWriter.toString()).contains("Invalid API key");
            verify(filterChain, never()).doFilter(request, response);
        }

        @Test
        void doFilterInternal_ValidApiKey_ContinuesFilterChain() throws Exception {
            // Given: Valid API key that matches
            when(request.getHeader(API_KEY_HEADER)).thenReturn(VALID_API_KEY);
            setupSsmToReturnApiKey(VALID_API_KEY);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then: Filter chain continues
            verify(filterChain).doFilter(request, response);
            verify(response, never()).setStatus(anyInt());
        }

        @Test
        void doFilterInternal_SsmError_Returns500() throws Exception {
            // Given: SSM throws exception
            when(request.getHeader(API_KEY_HEADER)).thenReturn(VALID_API_KEY);
            when(ssmClient.getParameter(any(GetParameterRequest.class)))
                .thenThrow(new RuntimeException("SSM connection failed"));

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then: 500 Internal Server Error
            verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            verify(response).setContentType("application/json");
            assertThat(responseWriter.toString()).contains("Internal configuration error");
            verify(filterChain, never()).doFilter(request, response);
        }
    }

    // ==================== Caching Tests ====================

    @Nested
    class CachingTests {

        private StringWriter responseWriter;

        @BeforeEach
        void setUpResponse() throws Exception {
            responseWriter = new StringWriter();
            lenient().when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
        }

        @Test
        void getApiKey_UsesCachedValueWithinTTL() throws Exception {
            // Given: First call populates cache
            when(request.getHeader(API_KEY_HEADER)).thenReturn(VALID_API_KEY);
            setupSsmToReturnApiKey(VALID_API_KEY);

            // When: First call
            filter.doFilterInternal(request, response, filterChain);

            // When: Second call within TTL
            filter.doFilterInternal(request, response, filterChain);

            // Then: SSM only called once (cached)
            verify(ssmClient, times(1)).getParameter(any(GetParameterRequest.class));
            verify(filterChain, times(2)).doFilter(request, response);
        }

        @Test
        void getApiKey_RefreshesCacheAfterTTL() throws Exception {
            // Given: Setup with zero cache TTL for testing
            when(request.getHeader(API_KEY_HEADER)).thenReturn(VALID_API_KEY);
            setupSsmToReturnApiKey(VALID_API_KEY);

            // First call populates cache
            filter.doFilterInternal(request, response, filterChain);

            // Force cache expiry by setting cacheExpiry to past
            ReflectionTestUtils.setField(filter, "cacheExpiry", 0L);

            // When: Second call after cache expiry
            filter.doFilterInternal(request, response, filterChain);

            // Then: SSM called twice (cache refreshed)
            verify(ssmClient, times(2)).getParameter(any(GetParameterRequest.class));
            verify(filterChain, times(2)).doFilter(request, response);
        }
    }

    // ==================== Helper Methods ====================

    private void setupSsmToReturnApiKey(String apiKey) {
        Parameter parameter = Parameter.builder()
            .value(apiKey)
            .build();
        GetParameterResponse parameterResponse = GetParameterResponse.builder()
            .parameter(parameter)
            .build();
        when(ssmClient.getParameter(any(GetParameterRequest.class)))
            .thenReturn(parameterResponse);
    }
}
