package org.workfitai.authservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.workfitai.authservice.dto.response.ApiError;
import org.workfitai.authservice.exception.BadRequestException;
import org.workfitai.authservice.exception.CannotUnlinkLastAuthMethodException;
import org.workfitai.authservice.exception.NotFoundException;

import com.mongodb.MongoWriteConcernException;
import com.mongodb.MongoWriteException;

import jakarta.validation.ConstraintViolationException;

/**
 * Unit tests for RestExceptionHandler – direct instantiation, no Spring context.
 * JaCoCo excludes the config package so these tests ensure handler correctness
 * without affecting coverage metrics.
 */
class RestExceptionHandlerTest {

    private RestExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RestExceptionHandler();
    }

    // ─── handleDuplicate ──────────────────────────────────────────────────────

    @Test
    void handleDuplicate_duplicateKeyException_returns400WithAlreadyInUseMessage() {
        DuplicateKeyException ex = new DuplicateKeyException("duplicate key: alice@example.com");

        ResponseEntity<ApiError> result = handler.handleDuplicate(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getStatus()).isEqualTo(400);
        assertThat(result.getBody().getMessage()).contains("already in use");
    }

    // ─── handleMongoWrite ─────────────────────────────────────────────────────

    @Test
    void handleMongoWrite_mongoWriteException_returns400() {
        MongoWriteException ex = mock(MongoWriteException.class);

        ResponseEntity<ApiError> result = handler.handleMongoWrite(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getStatus()).isEqualTo(400);
    }

    @Test
    void handleMongoWrite_mongoWriteConcernException_returns400() {
        MongoWriteConcernException ex = mock(MongoWriteConcernException.class);

        ResponseEntity<ApiError> result = handler.handleMongoWrite(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).isNotNull();
    }

    // ─── handleValidation ─────────────────────────────────────────────────────

    @Test
    void handleValidation_singleFieldError_returns400WithFieldList() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult br = mock(BindingResult.class);
        FieldError fe = new FieldError("registerRequest", "email", "must not be blank");
        when(ex.getBindingResult()).thenReturn(br);
        when(br.getFieldErrors()).thenReturn(List.of(fe));

        ResponseEntity<ApiError> result = handler.handleValidation(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getErrors()).hasSize(1);
        assertThat(result.getBody().getErrors().get(0)).contains("email");
    }

    // ─── handleConstraint ─────────────────────────────────────────────────────

    @Test
    void handleConstraint_emptyViolations_returns400() {
        ConstraintViolationException ex = new ConstraintViolationException("invalid", Set.of());

        ResponseEntity<ApiError> result = handler.handleConstraint(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getStatus()).isEqualTo(400);
    }

    // ─── handleBadJson ────────────────────────────────────────────────────────

    @Test
    void handleBadJson_malformedJsonException_returns400() {
        HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);
        when(ex.getMessage()).thenReturn("JSON parse error");

        ResponseEntity<ApiError> result = handler.handleBadJson(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getStatus()).isEqualTo(400);
    }

    // ─── handleAuth ───────────────────────────────────────────────────────────

    @Test
    void handleAuth_badCredentials_returns401() {
        BadCredentialsException ex = new BadCredentialsException("Invalid credentials");

        ResponseEntity<ApiError> result = handler.handleAuth(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getStatus()).isEqualTo(401);
    }

    // ─── handleStatus ─────────────────────────────────────────────────────────

    @Test
    void handleStatus_notFoundResponseStatusException_returns404() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");

        ResponseEntity<ApiError> result = handler.handleStatus(ex);

        assertThat(result.getStatusCode().value()).isEqualTo(404);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getMessage()).isEqualTo("User not found");
    }

    @Test
    void handleStatus_nullReasonFallsBackToDefault_returns500() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);

        ResponseEntity<ApiError> result = handler.handleStatus(ex);

        assertThat(result.getStatusCode().value()).isEqualTo(500);
        assertThat(result.getBody()).isNotNull();
        // Null reason → falls back to Messages.Error.DEFAULT_ERROR ("Error")
        assertThat(result.getBody().getMessage()).isNotNull();
    }

    // ─── handleIllegal ────────────────────────────────────────────────────────

    @Test
    void handleIllegal_illegalArgument_returns400WithOriginalMessage() {
        IllegalArgumentException ex = new IllegalArgumentException("invalid provider name");

        ResponseEntity<ApiError> result = handler.handleIllegal(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getMessage()).isEqualTo("invalid provider name");
    }

    // ─── handleNotFound ───────────────────────────────────────────────────────

    @Test
    void handleNotFound_noSuchElement_returns404() {
        NoSuchElementException ex = new NoSuchElementException("entity not found");

        ResponseEntity<ApiError> result = handler.handleNotFound(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getStatus()).isEqualTo(404);
    }

    // ─── handleBadRequest ─────────────────────────────────────────────────────

    @Test
    void handleBadRequest_customBadRequestException_returns400() {
        BadRequestException ex = new BadRequestException("invalid request body");

        ResponseEntity<ApiError> result = handler.handleBadRequest(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getMessage()).isEqualTo("invalid request body");
    }

    // ─── handleNotFoundCustom ─────────────────────────────────────────────────

    @Test
    void handleNotFoundCustom_notFoundException_returns404() {
        NotFoundException ex = new NotFoundException("resource with id 42 not found");

        ResponseEntity<ApiError> result = handler.handleNotFoundCustom(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getMessage()).isEqualTo("resource with id 42 not found");
    }

    // ─── handleCannotUnlinkLastAuthMethod ─────────────────────────────────────

    @Test
    void handleCannotUnlinkLastAuthMethod_throws400WithOriginalMessage() {
        CannotUnlinkLastAuthMethodException ex =
                new CannotUnlinkLastAuthMethodException("Cannot unlink your last authentication method");

        ResponseEntity<ApiError> result = handler.handleCannotUnlinkLastAuthMethod(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getMessage()).contains("last authentication method");
    }

    // ─── handleTypeMismatch ───────────────────────────────────────────────────

    @Test
    void handleTypeMismatch_enumMismatch_returns400WithValueAndParamInMessage() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getValue()).thenReturn("UNKNOWN_PROVIDER");
        when(ex.getName()).thenReturn("provider");

        ResponseEntity<ApiError> result = handler.handleTypeMismatch(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getMessage()).contains("UNKNOWN_PROVIDER");
        assertThat(result.getBody().getMessage()).contains("provider");
    }

    // ─── handleAccessDenied ───────────────────────────────────────────────────

    @Test
    void handleAccessDenied_forbiddenOperation_returns403() {
        AccessDeniedException ex = new AccessDeniedException("insufficient authority");

        ResponseEntity<ApiError> result = handler.handleAccessDenied(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getStatus()).isEqualTo(403);
    }

    // ─── handleGeneric ────────────────────────────────────────────────────────

    @Test
    void handleGeneric_unexpectedRuntimeException_returns500() {
        RuntimeException ex = new RuntimeException("unexpected database failure");

        ResponseEntity<ApiError> result = handler.handleGeneric(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getStatus()).isEqualTo(500);
    }
}
