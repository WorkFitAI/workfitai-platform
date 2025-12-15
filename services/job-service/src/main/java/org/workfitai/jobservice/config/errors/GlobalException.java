package org.workfitai.jobservice.config.errors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.workfitai.jobservice.model.dto.response.RestResponse;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalException {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RestResponse<Object>> handleAllException(Exception ex) {
        log.error("Unhandled exception occurred: {}", ex.getMessage(), ex);

        // Check for nested causes that might be more informative
        Throwable cause = ex.getCause();
        String message = ex.getMessage();

        if (cause != null) {
            log.error("Root cause: {}", cause.getMessage(), cause);
            message = cause.getMessage() != null ? cause.getMessage() : ex.getMessage();
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(RestResponse.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), message));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<RestResponse<Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.error("Data integrity violation: {}", ex.getMessage(), ex);
        String message = "Database constraint violation: " + ex.getMostSpecificCause().getMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(RestResponse.error(HttpStatus.BAD_REQUEST.value(), message));
    }

    @ExceptionHandler(TransactionSystemException.class)
    public ResponseEntity<RestResponse<Object>> handleTransactionSystemException(TransactionSystemException ex) {
        log.error("Transaction system exception: {}", ex.getMessage(), ex);

        Throwable cause = ex.getRootCause();
        String message = "Transaction failed";

        if (cause != null) {
            log.error("Transaction root cause: {}", cause.getMessage(), cause);
            message = "Transaction failed: " + cause.getMessage();
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(RestResponse.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), message));
    }

    @ExceptionHandler(InvalidDataException.class)
    public ResponseEntity<RestResponse<Object>> handleInvalidData(InvalidDataException ex) {
        log.warn("Invalid data exception: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(RestResponse.error(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
    }

    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<RestResponse<Object>> handleJobConflict(ResourceConflictException ex) {
        log.warn("Resource conflict exception: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(RestResponse.error(HttpStatus.CONFLICT.value(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RestResponse<Object>> validationError(MethodArgumentNotValidException ex) {
        BindingResult result = ex.getBindingResult();
        List<FieldError> fieldErrors = result.getFieldErrors();

        // Lấy danh sách lỗi validation
        List<String> errors = fieldErrors.stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.toList());

        String message = errors.size() > 1 ? errors.toString() : errors.get(0);

        log.warn("Validation error: {}", message);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(RestResponse.error(HttpStatus.BAD_REQUEST.value(), message));
    }
}