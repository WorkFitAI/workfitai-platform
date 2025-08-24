package org.workfitai.authservice.config;

import com.mongodb.MongoWriteConcernException;
import com.mongodb.MongoWriteException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.workfitai.authservice.response.ApiError;
import org.workfitai.authservice.response.ResponseData;
import org.workfitai.authservice.web.MdcTraceFilter;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class RestExceptionHandler {

    private ApiError build(int code, String message, List<String> errors) {
        return ApiError.builder()
                .code(code)
                .message(message)
                .errors(errors)
                .traceId(MDC.get(MdcTraceFilter.TRACE_ID))
                .timestamp(OffsetDateTime.now())
                .build();
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ResponseData<Void>> handleDuplicate(DuplicateKeyException ex) {
        // You can parse ex.getMessage() to decide which field, but a stable message is fine
        return ResponseEntity.badRequest()
                .body(ResponseData.error(400, "Username or email already in use"));
    }

    // If the driver bubbles raw Mongo exceptions in some cases:
    @ExceptionHandler({ MongoWriteException.class, MongoWriteConcernException.class })
    public ResponseEntity<ResponseData<Void>> handleMongoWrite(RuntimeException ex) {
        return ResponseEntity.badRequest()
                .body(ResponseData.error(400, "Username or email already in use"));
    }

    // 400 – DTO @Valid errors
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        var errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest().body(build(400, "Validation failed", errors));
    }

    // 400 – @Validated on @ConfigurationProperties, @PathVariable, @RequestParam…
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex) {
        var errors = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();
        return ResponseEntity.badRequest().body(build(400, "Invalid request", errors));
    }

    // 400 – bad/malformed JSON
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleBadJson(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(build(400, "Malformed JSON", List.of()));
    }

    // 401 – spring-security/explicit
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuth(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(build(401, "Unauthorized", List.of()));
    }

    // Map service-layer ResponseStatusException without catching in services
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleStatus(ResponseStatusException ex) {
        int code = ex.getStatusCode().value();
        String msg = (ex.getReason() != null) ? ex.getReason() : "Error";
        return ResponseEntity.status(code).body(build(code, msg, List.of()));
    }

    // (Optional) 404/400 helpers – when services throw common JDK exceptions
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegal(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(build(400, ex.getMessage(), List.of()));
    }

    @ExceptionHandler(java.util.NoSuchElementException.class)
    public ResponseEntity<ApiError> handleNotFound(java.util.NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(build(404, ex.getMessage(), List.of()));
    }

    // 500 – last resort
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(build(500, "Internal server error", List.of()));
    }
}