package org.workfitai.authservice.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.List;

@Value
@Builder
public class ApiError {
    int code;                 // HTTP status code
    String message;           // human-readable summary
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    List<String> errors;      // field/validation errors (optional)
    String traceId;           // from MDC, also echoed in header
    OffsetDateTime timestamp; // when produced
}