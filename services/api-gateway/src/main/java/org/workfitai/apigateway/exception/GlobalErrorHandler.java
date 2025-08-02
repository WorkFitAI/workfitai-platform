package org.workfitai.apigateway.exception;

import org.workfitai.apigateway.model.dto.response.ResponseData;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestControllerAdvice
public class GlobalErrorHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseData<Void> handleValidationException(MethodArgumentNotValidException ex) {
        String error = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .findFirst()
                .orElse("Invalid input data");
        log.warn("[VALIDATION] {}", error);
        return ResponseData.error(400, "Validation failed: " + error);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseData<Void> handleConstraintViolation(ConstraintViolationException ex) {
        String error = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .findFirst()
                .orElse("Constraint violation");
        log.warn("[CONSTRAINT] {}", error);
        return ResponseData.error(400, "Invalid request: " + error);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseData<Void> handleInvalidJson(HttpMessageNotReadableException ex) {
        log.warn("[MALFORMED REQUEST] {}", ex.getMessage());
        return ResponseData.error(400, "Malformed JSON or invalid request body");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseData<Void> handleStatusException(ResponseStatusException ex) {
        return ResponseData.error(ex.getStatusCode().value(), ex.getReason());
    }

    @ExceptionHandler(Exception.class)
    public ResponseData<Void> handleGenericException(Exception ex) {
        log.error("[UNEXPECTED ERROR]", ex);
        return ResponseData.error(500, "An unexpected error occurred. Please try again later.");
    }
}
