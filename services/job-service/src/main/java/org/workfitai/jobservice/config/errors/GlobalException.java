package org.workfitai.jobservice.config.errors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
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

    /* ===================== SECURITY ===================== */

    // @PreAuthorize, @PostAuthorize
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<RestResponse<Object>> handleAuthorizationDenied(AuthorizationDeniedException ex) {
        log.warn("Authorization denied (method-level): {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(RestResponse.error(
                        HttpStatus.FORBIDDEN.value(),
                        "Access Denied"
                ));
    }

    // URL-based security (/admin/**, /hr/**, ...)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<RestResponse<Object>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied (filter-level): {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(RestResponse.error(
                        HttpStatus.FORBIDDEN.value(),
                        "Access Denied"
                ));
    }

    /* ===================== BUSINESS ===================== */

    @ExceptionHandler(NoPermissionException.class)
    public ResponseEntity<RestResponse<Object>> handleNoPermission(NoPermissionException ex) {
        log.warn("No permission: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(RestResponse.error(
                        HttpStatus.FORBIDDEN.value(),
                        ex.getMessage()
                ));
    }

    @ExceptionHandler(InvalidDataException.class)
    public ResponseEntity<RestResponse<Object>> handleInvalidData(InvalidDataException ex) {
        log.warn("Invalid data: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(RestResponse.error(
                        HttpStatus.BAD_REQUEST.value(),
                        ex.getMessage()
                ));
    }

    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<RestResponse<Object>> handleResourceConflict(ResourceConflictException ex) {
        log.warn("Resource conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(RestResponse.error(
                        HttpStatus.CONFLICT.value(),
                        ex.getMessage()
                ));
    }

    /* ===================== VALIDATION ===================== */

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RestResponse<Object>> handleValidation(MethodArgumentNotValidException ex) {
        BindingResult result = ex.getBindingResult();

        List<String> errors = result.getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.toList());

        String message = errors.size() > 1 ? errors.toString() : errors.get(0);

        log.warn("Validation error: {}", message);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(RestResponse.error(
                        HttpStatus.BAD_REQUEST.value(),
                        message
                ));
    }

    /* ===================== DATABASE ===================== */

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<RestResponse<Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.error("Data integrity violation", ex);
        String message = "Database constraint violation";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(RestResponse.error(
                        HttpStatus.BAD_REQUEST.value(),
                        message
                ));
    }

    @ExceptionHandler(TransactionSystemException.class)
    public ResponseEntity<RestResponse<Object>> handleTransaction(TransactionSystemException ex) {
        log.error("Transaction system exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(RestResponse.error(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Transaction failed"
                ));
    }

    /* ===================== FALLBACK ===================== */

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RestResponse<Object>> handleAll(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(RestResponse.error(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Internal Server Error"
                ));
    }
}
