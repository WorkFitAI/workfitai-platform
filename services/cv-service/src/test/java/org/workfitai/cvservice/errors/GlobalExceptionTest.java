package org.workfitai.cvservice.errors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.workfitai.cvservice.model.dto.response.RestResponse;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionTest {

    private final GlobalException handler = new GlobalException();

    @Test
    void handleAllException_returns500() {
        ResponseEntity<RestResponse<Object>> response = handler.handleAllException(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).isEqualTo("boom");
    }

    @Test
    void handleResourceNotFound_returns404() {
        ResponseEntity<RestResponse<Object>> response = handler.handleResourceNotFound(new ResourceNotFoundException("not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo("not found");
    }

    @Test
    void handleBusinessException_usesExceptionStatus() {
        ResponseEntity<RestResponse<Object>> response = handler.handleBusinessException(new InvalidDataException("bad data", HttpStatus.BAD_REQUEST));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("bad data");
    }

    @Test
    void handleCVConflict_returns409() {
        ResponseEntity<RestResponse<Object>> response = handler.handleCVConflict(new CVConflictException("conflict"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).isEqualTo("conflict");
    }

    @Test
    void validationError_joinsMultipleFieldErrors() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError error1 = new FieldError("obj", "field1", "must not be blank");
        FieldError error2 = new FieldError("obj", "field2", "must be positive");
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(error1, error2));

        ResponseEntity<RestResponse<Object>> response = handler.validationError(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).contains("must not be blank", "must be positive");
    }

    @Test
    void validationError_returnsSingleMessage_whenOnlyOneFieldError() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError error = new FieldError("obj", "field1", "must not be blank");
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(error));

        ResponseEntity<RestResponse<Object>> response = handler.validationError(ex);

        assertThat(response.getBody().getMessage()).isEqualTo("must not be blank");
    }

    @Test
    void handleConstraintViolation_joinsMultipleMessages() {
        ConstraintViolation<?> violation1 = mock(ConstraintViolation.class);
        ConstraintViolation<?> violation2 = mock(ConstraintViolation.class);
        when(violation1.getMessage()).thenReturn("must not be null");
        when(violation2.getMessage()).thenReturn("must be valid");
        ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation1, violation2));

        ResponseEntity<RestResponse<Object>> response = handler.handleConstraintViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).contains("must not be null", "must be valid");
    }

    @Test
    void handleConstraintViolation_returnsSingleMessage_whenOnlyOneViolation() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        when(violation.getMessage()).thenReturn("must not be null");
        ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));

        ResponseEntity<RestResponse<Object>> response = handler.handleConstraintViolation(ex);

        assertThat(response.getBody().getMessage()).isEqualTo("must not be null");
    }

    @Test
    void handleUnsupportedMediaType_returns415() {
        ResponseEntity<RestResponse<Object>> response = handler.handleUnsupportedMediaType(new RuntimeException("unsupported"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void handleMaxSizeExceeded_returns413() {
        ResponseEntity<RestResponse<Object>> response = handler.handleMaxSizeExceeded(new RuntimeException("too large"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }
}
