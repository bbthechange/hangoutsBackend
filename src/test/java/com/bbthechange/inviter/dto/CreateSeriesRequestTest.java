package com.bbthechange.inviter.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for CreateSeriesRequest validation.
 * Tests JSR-303 validation annotations for required fields.
 */
class CreateSeriesRequestTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void validation_WithValidData_PassesValidation() {
        // Given
        CreateHangoutRequest validNewMemberRequest = createValidCreateHangoutRequest();
        CreateSeriesRequest request = new CreateSeriesRequest("12345678-1234-1234-1234-123456789012", validNewMemberRequest);

        // When
        Set<ConstraintViolation<CreateSeriesRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isEmpty();
    }

    @Test
    void validation_WithBlankInitialHangoutId_FailsValidation() {
        // Given
        CreateHangoutRequest validNewMemberRequest = createValidCreateHangoutRequest();
        CreateSeriesRequest request = new CreateSeriesRequest("", validNewMemberRequest);

        // When
        Set<ConstraintViolation<CreateSeriesRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).hasSize(1);
        ConstraintViolation<CreateSeriesRequest> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("initialHangoutId");
        assertThat(violation.getMessage()).isEqualTo("Initial hangout ID is required");
    }

    @Test
    void validation_WithNullInitialHangoutId_FailsValidation() {
        // Given
        CreateHangoutRequest validNewMemberRequest = createValidCreateHangoutRequest();
        CreateSeriesRequest request = new CreateSeriesRequest(null, validNewMemberRequest);

        // When
        Set<ConstraintViolation<CreateSeriesRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).hasSize(1);
        ConstraintViolation<CreateSeriesRequest> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("initialHangoutId");
        assertThat(violation.getMessage()).isEqualTo("Initial hangout ID is required");
    }

    @Test
    void validation_WithNullNewMemberRequest_FailsValidation() {
        // Given
        CreateSeriesRequest request = new CreateSeriesRequest("12345678-1234-1234-1234-123456789012", null);

        // When
        Set<ConstraintViolation<CreateSeriesRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).hasSize(1);
        ConstraintViolation<CreateSeriesRequest> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("newMemberRequest");
        assertThat(violation.getMessage()).isEqualTo("New member request is required");
    }

    // Helper method to create a valid CreateHangoutRequest
    private CreateHangoutRequest createValidCreateHangoutRequest() {
        CreateHangoutRequest request = new CreateHangoutRequest();
        request.setTitle("Test Hangout");
        request.setDescription("Test Description");
        // Set other required fields as needed for validation
        return request;
    }
}