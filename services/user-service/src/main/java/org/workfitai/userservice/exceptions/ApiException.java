package org.workfitai.userservice.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiException extends RuntimeException {

  private final HttpStatus status;

  public ApiException(String message) {
    super(message);
    this.status = HttpStatus.BAD_REQUEST;
  }

  public ApiException(String message, HttpStatus status) {
    super(message);
    this.status = status;
  }
}
