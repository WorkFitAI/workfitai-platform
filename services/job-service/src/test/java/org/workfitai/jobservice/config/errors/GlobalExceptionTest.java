package org.workfitai.jobservice.config.errors;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.workfitai.jobservice.model.dto.response.RestResponse;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionTest {

    private final GlobalException handler = new GlobalException();

    @Test
    void handleAuthorizationDenied_returnsForbidden() {
        AuthorizationDeniedException ex = new AuthorizationDeniedException("denied", mock(AuthorizationResult.class));

        ResponseEntity<RestResponse<Object>> response = handler.handleAuthorizationDenied(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getMessage()).isEqualTo("Access Denied");
    }

    @Test
    void handleAccessDenied_returnsForbidden() {
        ResponseEntity<RestResponse<Object>> response = handler.handleAccessDenied(new AccessDeniedException("denied"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void handleNoPermission_returnsForbiddenWithMessage() {
        ResponseEntity<RestResponse<Object>> response = handler.handleNoPermission(new NoPermissionException("no permission here"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getMessage()).isEqualTo("no permission here");
    }

    @Test
    void handleInvalidData_returnsBadRequestWithMessage() {
        ResponseEntity<RestResponse<Object>> response = handler.handleInvalidData(new InvalidDataException("bad input"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("bad input");
    }

    @Test
    void handleResourceConflict_returnsConflictWithMessage() {
        ResponseEntity<RestResponse<Object>> response = handler.handleResourceConflict(new ResourceConflictException("conflict"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).isEqualTo("conflict");
    }

    @Test
    void handleValidation_returnsSingleErrorMessage_whenOnlyOneFieldError() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("obj", "title", "must not be blank");
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<RestResponse<Object>> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("must not be blank");
    }

    @Test
    void handleValidation_returnsErrorListMessage_whenMultipleFieldErrors() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError titleError = new FieldError("obj", "title", "must not be blank");
        FieldError salaryError = new FieldError("obj", "salary", "must be positive");
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(titleError, salaryError));

        ResponseEntity<RestResponse<Object>> response = handler.handleValidation(ex);

        assertThat(response.getBody().getMessage()).contains("must not be blank").contains("must be positive");
    }

    @Test
    void handleDataIntegrity_returnsBadRequest() {
        ResponseEntity<RestResponse<Object>> response = handler.handleDataIntegrity(new DataIntegrityViolationException("constraint"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Database constraint violation");
    }

    @Test
    void handleTransaction_returnsInternalServerError() {
        ResponseEntity<RestResponse<Object>> response = handler.handleTransaction(new TransactionSystemException("tx failed"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).isEqualTo("Transaction failed");
    }

    @Test
    void handleClientDisconnect_doesNotThrow() {
        handler.handleClientDisconnect(new AsyncRequestNotUsableException("disconnected"));
    }

    @Test
    void handleFeignException_returnsServiceUnavailable() {
        Request request = Request.create(Request.HttpMethod.GET, "/x", java.util.Map.of(), null, StandardCharsets.UTF_8);
        FeignException ex = FeignException.errorStatus("call", feign.Response.builder()
                .status(503)
                .request(request)
                .headers(java.util.Map.of())
                .build());

        ResponseEntity<RestResponse<Object>> response = handler.handleFeignException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void handleAll_returnsInternalServerError() {
        ResponseEntity<RestResponse<Object>> response = handler.handleAll(new RuntimeException("unexpected"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).isEqualTo("Internal Server Error");
    }
}
