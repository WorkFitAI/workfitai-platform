package org.workfitai.userservice.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;
import org.workfitai.userservice.dto.response.ApiError;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RestExceptionHandlerTest {

    private final RestExceptionHandler handler = new RestExceptionHandler();

    // ---- handleDuplicateKey ----

    @Test
    void handleDuplicateKey_noCause_returnsBadRequest() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("dup");
        ResponseEntity<ApiError> resp = handler.handleDuplicateKey(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleDuplicateKey_phoneCause_returnsPhoneMessage() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "dup", new RuntimeException("phone number unique constraint"));
        ResponseEntity<ApiError> resp = handler.handleDuplicateKey(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().getMessage()).isNotBlank();
    }

    @Test
    void handleDuplicateKey_emailCause_returnsEmailMessage() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "dup", new RuntimeException("email unique constraint"));
        ResponseEntity<ApiError> resp = handler.handleDuplicateKey(ex);
        assertThat(resp.getBody().getMessage()).isNotBlank();
    }

    @Test
    void handleDuplicateKey_uniqueCause_returnsDuplicateMessage() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "dup", new RuntimeException("unique constraint violated"));
        ResponseEntity<ApiError> resp = handler.handleDuplicateKey(ex);
        assertThat(resp.getBody().getMessage()).isNotBlank();
    }

    // ---- handleValidation ----

    @Test
    void handleValidation_emailField_returnsErrors() {
        MethodArgumentNotValidException ex = buildValidationEx("email", "Email", "bad@");
        ResponseEntity<ApiError> resp = handler.handleValidation(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().getErrors()).isNotEmpty();
    }

    @Test
    void handleValidation_fullNameNotBlank_mapsToRequiredMessage() {
        MethodArgumentNotValidException ex = buildValidationEx("fullName", "NotBlank", "");
        ResponseEntity<ApiError> resp = handler.handleValidation(ex);
        assertThat(resp.getBody().getErrors()).isNotEmpty();
    }

    @Test
    void handleValidation_phoneNumberPattern_mapsToPatternMessage() {
        MethodArgumentNotValidException ex = buildValidationEx("phoneNumber", "Pattern", "abc");
        ResponseEntity<ApiError> resp = handler.handleValidation(ex);
        assertThat(resp.getBody().getErrors()).isNotEmpty();
    }

    @Test
    void handleValidation_fullNameSize_mapsSizeMessage() {
        MethodArgumentNotValidException ex = buildValidationEx("fullName", "Size", "x");
        ResponseEntity<ApiError> resp = handler.handleValidation(ex);
        assertThat(resp.getBody().getErrors()).isNotEmpty();
    }

    @Test
    void handleValidation_birthdayNotNull_mapsBirthdayRequired() {
        MethodArgumentNotValidException ex = buildValidationEx("birthday", "NotNull", null);
        ResponseEntity<ApiError> resp = handler.handleValidation(ex);
        assertThat(resp.getBody().getErrors()).isNotEmpty();
    }

    @Test
    void handleValidation_addressNotBlank_mapsAddressRequired() {
        MethodArgumentNotValidException ex = buildValidationEx("address", "NotEmpty", "");
        ResponseEntity<ApiError> resp = handler.handleValidation(ex);
        assertThat(resp.getBody().getErrors()).isNotEmpty();
    }

    @Test
    void handleValidation_unknownFieldNotBlank_returnsGenericRequired() {
        MethodArgumentNotValidException ex = buildValidationEx("someField", "NotBlank", "");
        ResponseEntity<ApiError> resp = handler.handleValidation(ex);
        assertThat(resp.getBody().getErrors()).anyMatch(e -> e.contains("someField"));
    }

    @Test
    void handleValidation_nullCode_returnsFieldDefaultMessage() {
        MethodArgumentNotValidException ex = buildValidationEx("email", null, "bad");
        ResponseEntity<ApiError> resp = handler.handleValidation(ex);
        assertThat(resp.getBody().getErrors()).isNotEmpty();
    }

    @Test
    void handleValidation_unknownFieldPattern_returnsFormatMessage() {
        MethodArgumentNotValidException ex = buildValidationEx("someCode", "Pattern", "x");
        ResponseEntity<ApiError> resp = handler.handleValidation(ex);
        assertThat(resp.getBody().getErrors()).anyMatch(e -> e.contains("someCode"));
    }

    @Test
    void handleValidation_unknownFieldSize_returnsSizeMessage() {
        MethodArgumentNotValidException ex = buildValidationEx("otherField", "Size", "x");
        ResponseEntity<ApiError> resp = handler.handleValidation(ex);
        assertThat(resp.getBody().getErrors()).anyMatch(e -> e.contains("otherField"));
    }

    @Test
    void handleValidation_defaultCode_returnsFieldMessage() {
        MethodArgumentNotValidException ex = buildValidationEx("someField", "Custom", "val");
        ResponseEntity<ApiError> resp = handler.handleValidation(ex);
        assertThat(resp.getBody().getErrors()).anyMatch(e -> e.contains("someField"));
    }

    // ---- handleParamConstraint ----

    @Test
    void handleParamConstraint_returnsBadRequest() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("param.field");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must not be blank");
        ConstraintViolationException ex = new ConstraintViolationException("v", Set.of(violation));

        ResponseEntity<ApiError> resp = handler.handleParamConstraint(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().getErrors()).isNotEmpty();
    }

    // ---- handleBadJson ----

    @Test
    void handleBadJson_returnsBadRequest() {
        HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);
        ResponseEntity<ApiError> resp = handler.handleBadJson(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---- handleAuth ----

    @Test
    void handleAuth_returnsUnauthorized() {
        BadCredentialsException ex = new BadCredentialsException("bad credentials");
        ResponseEntity<ApiError> resp = handler.handleAuth(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---- handleAccessDenied ----

    @Test
    void handleAccessDenied_returnsForbidden() {
        AccessDeniedException ex = new AccessDeniedException("no access");
        ResponseEntity<ApiError> resp = handler.handleAccessDenied(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ---- handleStatus ----

    @Test
    void handleStatus_withReason_usesReason() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "resource missing");
        ResponseEntity<ApiError> resp = handler.handleStatus(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().getMessage()).isEqualTo("resource missing");
    }

    @Test
    void handleStatus_withoutReason_usesDefaultMessage() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.BAD_REQUEST);
        ResponseEntity<ApiError> resp = handler.handleStatus(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---- handleIllegal ----

    @Test
    void handleIllegal_returnsBadRequest() {
        IllegalArgumentException ex = new IllegalArgumentException("bad arg");
        ResponseEntity<ApiError> resp = handler.handleIllegal(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().getMessage()).isEqualTo("bad arg");
    }

    // ---- handleNotFound ----

    @Test
    void handleNotFound_returnsNotFound() {
        NoSuchElementException ex = new NoSuchElementException("not there");
        ResponseEntity<ApiError> resp = handler.handleNotFound(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---- handleBadRequestCustom / handleNotFoundCustom ----

    @Test
    void handleBadRequestCustom_returnsBadRequest() {
        BadRequestException ex = new BadRequestException("invalid input");
        ResponseEntity<ApiError> resp = handler.handleBadRequestCustom(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleNotFoundCustom_returnsNotFound() {
        NotFoundException ex = new NotFoundException("entity gone");
        ResponseEntity<ApiError> resp = handler.handleNotFoundCustom(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---- handleDataAccess ----

    @Test
    void handleDataAccess_generic_returnsBadRequest() {
        DataRetrievalFailureException ex = new DataRetrievalFailureException("timeout");
        ResponseEntity<ApiError> resp = handler.handleDataAccess(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleDataAccess_duplicateMessage_returnsDuplicateError() {
        DataRetrievalFailureException ex = new DataRetrievalFailureException("duplicate key violation");
        ResponseEntity<ApiError> resp = handler.handleDataAccess(ex);
        assertThat(resp.getBody().getMessage()).isNotBlank();
    }

    @Test
    void handleDataAccess_foreignKeyMessage_returnsForeignKeyError() {
        DataRetrievalFailureException ex = new DataRetrievalFailureException("foreign key constraint");
        ResponseEntity<ApiError> resp = handler.handleDataAccess(ex);
        assertThat(resp.getBody().getMessage()).isNotBlank();
    }

    @Test
    void handleDataAccess_notNullMessage_returnsNotNullError() {
        DataRetrievalFailureException ex = new DataRetrievalFailureException("not null violation");
        ResponseEntity<ApiError> resp = handler.handleDataAccess(ex);
        assertThat(resp.getBody().getMessage()).isNotBlank();
    }

    // ---- handleGeneric ----

    @Test
    void handleGeneric_returnsInternalServerError() {
        Exception ex = new Exception("unexpected");
        ResponseEntity<ApiError> resp = handler.handleGeneric(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().getMessage()).isNotBlank();
    }

    // ---- helpers ----

    private MethodArgumentNotValidException buildValidationEx(String field, String code, Object rejected) {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult br = mock(BindingResult.class);
        FieldError fe = mock(FieldError.class);
        when(fe.getField()).thenReturn(field);
        when(fe.getCode()).thenReturn(code);
        when(fe.getRejectedValue()).thenReturn(rejected);
        when(fe.getDefaultMessage()).thenReturn("validation failed");
        when(br.getFieldErrors()).thenReturn(List.of(fe));
        when(ex.getBindingResult()).thenReturn(br);
        return ex;
    }
}
