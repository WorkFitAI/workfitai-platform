package org.workfitai.userservice.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

@Getter
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final List<String> errors;

  public ApiException(String message) {
    super(message);
    this.status = HttpStatus.BAD_REQUEST;
    this.errors = List.of();
  }

  public ApiException(String message, HttpStatus status) {
    super(message);
    this.status = status;
    this.errors = List.of();
  }

  public ApiException(String message, List<String> errors) {
    super(message);
    this.status = HttpStatus.BAD_REQUEST;
    this.errors = errors != null ? List.copyOf(errors) : List.of();
  }

  public ApiException(String message, HttpStatus status, List<String> errors) {
    super(message);
    this.status = status;
    this.errors = errors != null ? List.copyOf(errors) : List.of();
  }

  /**
   * Create validation exception with formatted error message
   */
  public static ApiException validationError(List<String> validationErrors) {
    String message = "Validation error: " + String.join("; ", validationErrors);
    return new ApiException(message, HttpStatus.BAD_REQUEST, validationErrors);
  }

  /**
   * Create not found exception
   */
  public static ApiException notFound(String resource, String identifier) {
    String message = String.format("%s with identifier '%s' not found", resource, identifier);
    return new ApiException(message, HttpStatus.NOT_FOUND);
  }

  /**
   * Create conflict exception (for duplicate resources)
   */
  public static ApiException conflict(String message) {
    return new ApiException(message, HttpStatus.CONFLICT);
  }
}
