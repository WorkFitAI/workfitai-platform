package org.workfitai.userservice.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.workfitai.userservice.constants.ValidationMessages;
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
    String message = ValidationMessages.DATABASE_DUPLICATE_DATA;

    if (ex.getCause() != null && ex.getCause().getMessage() != null) {
      String causeMsg = ex.getCause().getMessage().toLowerCase();
      if (causeMsg.contains("phone")) {
        message = ValidationMessages.CANDIDATE_PHONENUMBER_DUPLICATE;
      } else if (causeMsg.contains("email")) {
        message = ValidationMessages.CANDIDATE_EMAIL_DUPLICATE;
      } else if (causeMsg.contains("unique")) {
        message = ValidationMessages.DATABASE_DUPLICATE_DATA;
      }
    }

    return ResponseEntity.badRequest()
        .body(build(HttpStatus.BAD_REQUEST, message, List.of()));
  }

  // 🔸 Lỗi validate DTO – @Valid trên RequestBody
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
    var errors = ex.getBindingResult().getFieldErrors().stream()
        .map(this::getFieldErrorMessage)
        .toList();

    return ResponseEntity.badRequest()
        .body(build(HttpStatus.BAD_REQUEST, ValidationMessages.VALIDATION_ERROR_GENERAL, errors));
  }

  // 🔸 Lỗi validate @RequestParam / @PathVariable (ConstraintViolation)
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiError> handleParamConstraint(ConstraintViolationException ex) {
    var errors = ex.getConstraintViolations().stream()
        .map(v -> v.getPropertyPath() + ": " + v.getMessage())
        .toList();

    return ResponseEntity.badRequest()
        .body(build(HttpStatus.BAD_REQUEST, ValidationMessages.INVALID_REQUEST_PARAMETERS, errors));
  }

  // 🔸 Lỗi JSON sai format
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiError> handleBadJson(HttpMessageNotReadableException ex) {
    return ResponseEntity.badRequest()
        .body(build(HttpStatus.BAD_REQUEST, ValidationMessages.MALFORMED_JSON, List.of()));
  }

  // 🔸 Lỗi Authentication (JWT, login sai,…)
  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ApiError> handleAuth(AuthenticationException ex) {
    log.error("❌ Authentication failed: {}", ex.getMessage(), ex);
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(build(HttpStatus.UNAUTHORIZED, ValidationMessages.UNAUTHORIZED_ACCESS, List.of(ex.getMessage())));
  }

  // 🔸 Lỗi Authorization (không đủ quyền)
  @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
  public ResponseEntity<ApiError> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
    log.error("❌ Access denied: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(build(HttpStatus.FORBIDDEN, "Access denied: " + ex.getMessage(), List.of()));
  }

  // 🔸 Lỗi được ném từ service (ResponseStatusException)
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiError> handleStatus(ResponseStatusException ex) {
    HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
    String msg = (ex.getReason() != null) ? ex.getReason() : ValidationMessages.UNEXPECTED_ERROR;
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

  // 🔸 Custom exceptions from service layer
  @ExceptionHandler(BadRequestException.class)
  public ResponseEntity<ApiError> handleBadRequestCustom(BadRequestException ex) {
    return ResponseEntity.badRequest()
        .body(build(HttpStatus.BAD_REQUEST, ex.getMessage(), List.of()));
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ApiError> handleNotFoundCustom(NotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(build(HttpStatus.NOT_FOUND, ex.getMessage(), List.of()));
  }

  // 🔸 Lỗi SQL/Database general
  @ExceptionHandler(org.springframework.dao.DataAccessException.class)
  public ResponseEntity<ApiError> handleDataAccess(org.springframework.dao.DataAccessException ex) {
    log.error("Database access error: {}", ex.getMessage(), ex);

    String message = ValidationMessages.DATABASE_ERROR_GENERAL;
    if (ex.getMessage() != null) {
      String msg = ex.getMessage().toLowerCase();
      if (msg.contains("duplicate") || msg.contains("unique")) {
        message = ValidationMessages.DATABASE_DUPLICATE_DATA;
      } else if (msg.contains("foreign key")) {
        message = ValidationMessages.DATABASE_FOREIGN_KEY_ERROR;
      } else if (msg.contains("not null")) {
        message = ValidationMessages.DATABASE_NOT_NULL_ERROR;
      }
    }

    return ResponseEntity.badRequest()
        .body(build(HttpStatus.BAD_REQUEST, message, List.of()));
  }

  // 🔸 Lỗi bất ngờ (catch-all)
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleGeneric(Exception ex) {
    log.error("Unexpected error: ", ex);

    // Tạo thông báo lỗi dễ đọc cho user
    String userMessage = ValidationMessages.UNEXPECTED_ERROR;

    // Log chi tiết để debug
    String errorDetails = String.format("Error type: %s, Message: %s",
        ex.getClass().getSimpleName(),
        ex.getMessage());
    log.error("Error details: {}", errorDetails);

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(build(HttpStatus.INTERNAL_SERVER_ERROR, userMessage, List.of(errorDetails)));
  }

  /**
   * Convert FieldError to formatted error message using ValidationMessages
   * constants
   */
  private String getFieldErrorMessage(FieldError fieldError) {
    String fieldName = fieldError.getField();
    Object rejectedValue = fieldError.getRejectedValue();
    String rejectedValueStr = rejectedValue != null ? rejectedValue.toString() : "null";

    // Map common validation annotations to our validation messages
    String code = fieldError.getCode();
    if (code == null) {
      return String.format("%s: %s", fieldName, fieldError.getDefaultMessage());
    }

    switch (code.toLowerCase()) {
      case "notnull":
      case "notblank":
      case "notempty":
        return mapRequiredFieldError(fieldName);
      case "pattern":
        return mapPatternError(fieldName, rejectedValueStr);
      case "size":
        return mapSizeError(fieldName, rejectedValueStr);
      case "email":
        return ValidationMessages.CANDIDATE_EMAIL_INVALID;
      default:
        return String.format("%s: %s", fieldName, fieldError.getDefaultMessage());
    }
  }

  private String mapRequiredFieldError(String fieldName) {
    switch (fieldName.toLowerCase()) {
      case "fullname":
        return ValidationMessages.CANDIDATE_FULLNAME_REQUIRED;
      case "email":
        return ValidationMessages.CANDIDATE_EMAIL_REQUIRED;
      case "phonenumber":
        return ValidationMessages.CANDIDATE_PHONENUMBER_REQUIRED;
      case "birthday":
        return ValidationMessages.CANDIDATE_BIRTHDAY_REQUIRED;
      case "address":
        return ValidationMessages.CANDIDATE_ADDRESS_REQUIRED;
      default:
        return String.format("%s is required", fieldName);
    }
  }

  private String mapPatternError(String fieldName, String rejectedValue) {
    switch (fieldName.toLowerCase()) {
      case "phonenumber":
        return ValidationMessages.CANDIDATE_PHONENUMBER_INVALID;
      case "email":
        return ValidationMessages.CANDIDATE_EMAIL_INVALID;
      default:
        return String.format("%s has invalid format: %s", fieldName, rejectedValue);
    }
  }

  private String mapSizeError(String fieldName, String rejectedValue) {
    switch (fieldName.toLowerCase()) {
      case "fullname":
        return ValidationMessages.CANDIDATE_FULLNAME_SIZE;
      default:
        return String.format("%s has invalid length: %s", fieldName, rejectedValue);
    }
  }
}
