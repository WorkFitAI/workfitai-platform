package org.workfitai.authservice.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.workfitai.authservice.dto.response.ApiError;

import jakarta.validation.ConstraintViolationException;

/**
 * Unit tests for GlobalExceptionHandler – direct instantiation, no Spring context.
 * JaCoCo excludes the exception package so these tests enforce correctness, not
 * line-coverage metrics.
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    // ─── handleValidation ─────────────────────────────────────────────────────

    @Test
    void handleValidation_singleFieldError_returns400WithFieldDetails() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult br = mock(BindingResult.class);
        FieldError fe = new FieldError("registerRequest", "email", "must not be blank");
        when(ex.getBindingResult()).thenReturn(br);
        when(br.getFieldErrors()).thenReturn(List.of(fe));

        ResponseEntity<ApiError> result = handler.handleValidation(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getStatus()).isEqualTo(400);
        assertThat(result.getBody().getErrors()).hasSize(1);
        assertThat(result.getBody().getErrors().get(0)).contains("email");
    }

    @Test
    void handleValidation_multipleFieldErrors_returnsAllErrors() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult br = mock(BindingResult.class);
        List<FieldError> errors = List.of(
                new FieldError("obj", "email", "must not be blank"),
                new FieldError("obj", "password", "must not be blank")
        );
        when(ex.getBindingResult()).thenReturn(br);
        when(br.getFieldErrors()).thenReturn(errors);

        ResponseEntity<ApiError> result = handler.handleValidation(ex);

        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getErrors()).hasSize(2);
    }

    // ─── handleConstraintViolation ────────────────────────────────────────────

    @Test
    void handleConstraintViolation_emptyViolations_returns400WithEmptyErrors() {
        ConstraintViolationException ex = new ConstraintViolationException("validation failed", Set.of());

        ResponseEntity<ApiError> result = handler.handleConstraintViolation(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getStatus()).isEqualTo(400);
    }

    // ─── handleInvalidJson ────────────────────────────────────────────────────

    @Test
    void handleInvalidJson_anyMalformedPayload_returns400() {
        org.springframework.http.converter.HttpMessageNotReadableException ex =
                mock(org.springframework.http.converter.HttpMessageNotReadableException.class);
        when(ex.getMessage()).thenReturn("JSON parse error");

        ResponseEntity<ApiError> result = handler.handleInvalidJson(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getStatus()).isEqualTo(400);
    }

    // ─── handleTypeMismatch ───────────────────────────────────────────────────

    @Test
    void handleTypeMismatch_invalidEnumValue_returns400WithDescriptiveMessage() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getValue()).thenReturn("INVALID_STATUS");
        when(ex.getName()).thenReturn("status");

        ResponseEntity<ApiError> result = handler.handleTypeMismatch(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getMessage()).contains("INVALID_STATUS");
        assertThat(result.getBody().getMessage()).contains("status");
    }

    // ─── handleIllegalArgument ────────────────────────────────────────────────

    @Test
    void handleIllegalArgument_anyMessage_returns400WithThatMessage() {
        IllegalArgumentException ex = new IllegalArgumentException("unsupported provider");

        ResponseEntity<ApiError> result = handler.handleIllegalArgument(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getMessage()).isEqualTo("unsupported provider");
    }

    // ─── handleNotFound ───────────────────────────────────────────────────────

    @Test
    void handleNotFound_noSuchElement_returns404() {
        NoSuchElementException ex = new NoSuchElementException("resource not found");

        ResponseEntity<ApiError> result = handler.handleNotFound(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getStatus()).isEqualTo(404);
    }

    // ─── handleStatusException ────────────────────────────────────────────────

    @Test
    void handleStatusException_404ResponseStatusException_returns404WithReason() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");

        ResponseEntity<ApiError> result = handler.handleStatusException(ex);

        assertThat(result.getStatusCode().value()).isEqualTo(404);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getMessage()).contains("User not found");
    }

    @Test
    void handleStatusException_400ResponseStatusException_returns400() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid input");

        ResponseEntity<ApiError> result = handler.handleStatusException(ex);

        assertThat(result.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void handleStatusException_nullReason_fallsBackToExceptionMessage() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND);

        ResponseEntity<ApiError> result = handler.handleStatusException(ex);

        assertThat(result.getStatusCode().value()).isEqualTo(404);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getMessage()).isNotNull();
    }

    // ─── handleAccessDenied ───────────────────────────────────────────────────

    @Test
    void handleAccessDenied_anyException_returns403() {
        AccessDeniedException ex = new AccessDeniedException("Forbidden operation");

        ResponseEntity<ApiError> result = handler.handleAccessDenied(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getStatus()).isEqualTo(403);
    }

    // ─── handleGeneric ────────────────────────────────────────────────────────

    @Test
    void handleGeneric_accessDeniedWrappedAsException_returns403() {
        AccessDeniedException ex = new AccessDeniedException("access denied");

        ResponseEntity<ApiError> result = handler.handleGeneric(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getStatus()).isEqualTo(403);
    }

    @Test
    void handleGeneric_typeMismatchWrappedAsException_returns400() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getValue()).thenReturn("BAD_VALUE");
        when(ex.getName()).thenReturn("param");

        ResponseEntity<ApiError> result = handler.handleGeneric(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleGeneric_unexpectedRuntimeException_returns500() {
        RuntimeException ex = new RuntimeException("something went wrong internally");

        ResponseEntity<ApiError> result = handler.handleGeneric(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getStatus()).isEqualTo(500);
    }
}
