package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.exception.ForbiddenException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for BaseController exception handlers.
 */
class BaseControllerTest {

    private final BaseController controller = new BaseController() {};

    @Test
    @DisplayName("handleForbidden returns 403 with FORBIDDEN code")
    void handleForbidden_ReturnsForbiddenStatusAndCode() {
        ForbiddenException ex = new ForbiddenException("User is not a member of this group");

        ResponseEntity<BaseController.ErrorResponse> response = controller.handleForbidden(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("FORBIDDEN");
        assertThat(response.getBody().getMessage()).isEqualTo("User is not a member of this group");
    }
}
