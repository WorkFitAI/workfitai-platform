package org.workfitai.userservice.exceptions;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.workfitai.userservice.constants.Messages;
import org.workfitai.userservice.dto.response.ApiError;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class RestExceptionHandler {

  private ApiError build(HttpStatus status, String message, List<String> errors) {
    List<String> safeErrors = (errors == null || errors.isEmpty()) ? List.of() : errors;
    return ApiError.builder()
        .status(status.value())
        .message(message)
        .errors(safeErrors)
        .timestamp(LocalDateTime.now())
        .build();
  }

  // 🔸 Lỗi unique constraint (email, phone, username trùng)
  @ExceptionHandler({
      DataIntegrityViolationException.class,
      DuplicateKeyException.class,
      SQLIntegrityConstraintViolationException.class
  })
  public ResponseEntity<ApiError> handleDuplicateKey(Exception ex) {
    String message = Messages.Error.USERNAME_EMAIL_ALREADY_IN_USE;

    if (ex.getCause() != null && ex.getCause().getMessage() != null) {
      String causeMsg = ex.getCause().getMessage().toLowerCase();
      if (causeMsg.contains("phone") || causeMsg.contains("email")) {
        message = Messages.Error.USERNAME_EMAIL_ALREADY_IN_USE;
      } else if (causeMsg.contains("unique")) {
        message = "A unique field value already exists.";
      }
    }

    return ResponseEntity.badRequest()
        .body(build(HttpStatus.BAD_REQUEST, message, List.of()));
  }

  // 🔸 Lỗi validate DTO – @Valid trên RequestBody
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
    var errors = ex.getBindingResult().getFieldErrors().stream()
        .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
        .toList();

    return ResponseEntity.badRequest()
        .body(build(HttpStatus.BAD_REQUEST, Messages.Error.VALIDATION_FAILED, errors));
  }

  // 🔸 Lỗi validate @RequestParam / @PathVariable (ConstraintViolation)
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex) {
    var errors = ex.getConstraintViolations().stream()
        .map(v -> v.getPropertyPath() + ": " + v.getMessage())
        .toList();

    return ResponseEntity.badRequest()
        .body(build(HttpStatus.BAD_REQUEST, Messages.Error.INVALID_REQUEST, errors));
  }

  // 🔸 Lỗi JSON sai format
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiError> handleBadJson(HttpMessageNotReadableException ex) {
    return ResponseEntity.badRequest()
        .body(build(HttpStatus.BAD_REQUEST, Messages.Error.MALFORMED_JSON, List.of()));
  }

  // 🔸 Lỗi Authentication (JWT, login sai,…)
  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ApiError> handleAuth(AuthenticationException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(build(HttpStatus.UNAUTHORIZED, Messages.Error.UNAUTHORIZED, List.of()));
  }

  // 🔸 Lỗi được ném từ service (ResponseStatusException)
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiError> handleStatus(ResponseStatusException ex) {
    HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
    String msg = (ex.getReason() != null) ? ex.getReason() : Messages.Error.DEFAULT_ERROR;
    return ResponseEntity.status(status).body(build(status, msg, List.of()));
  }

  // 🔸 Lỗi bad request thường (service layer throw new IllegalArgumentException)
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiError> handleIllegal(IllegalArgumentException ex) {
    return ResponseEntity.badRequest()
        .body(build(HttpStatus.BAD_REQUEST, ex.getMessage(), List.of()));
  }

  // 🔸 Lỗi not found (ví dụ findById().orElseThrow())
  @ExceptionHandler(java.util.NoSuchElementException.class)
  public ResponseEntity<ApiError> handleNotFound(java.util.NoSuchElementException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(build(HttpStatus.NOT_FOUND, ex.getMessage(), List.of()));
  }

  // 🔸 Lỗi bất ngờ (catch-all)
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleGeneric(Exception ex) {
    log.error("Unexpected error: ", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(build(HttpStatus.INTERNAL_SERVER_ERROR,
            Messages.Error.INTERNAL_SERVER_ERROR, List.of()));
  }
}
