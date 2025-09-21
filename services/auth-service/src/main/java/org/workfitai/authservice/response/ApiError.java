package org.workfitai.authservice.response;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ApiError {
    int code; // HTTP status code
    String message; // human-readable summary
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    List<String> errors; // field/validation errors (optional)
    LocalDateTime timestamp; // when produced
}