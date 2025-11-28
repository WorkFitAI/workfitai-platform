package org.workfitai.authservice.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.springframework.http.HttpStatus;

@Value
@Builder
public class ApiError {
    int status;
    String message;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String code;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    List<String> errors;
    LocalDateTime timestamp;

    public static ApiError of(HttpStatus status, String message) {
        return of(status, null, message, List.of());
    }

    public static ApiError of(HttpStatus status, String message, List<String> errors) {
        return of(status, null, message, errors);
    }

    public static ApiError of(HttpStatus status, String code, String message, List<String> errors) {
        return ApiError.builder()
                .status(status.value())
                .code(code)
                .message(message)
                .errors(normalise(errors))
                .timestamp(LocalDateTime.now())
                .build();
    }

    private static List<String> normalise(List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            return List.of();
        }
        return List.copyOf(errors);
    }
}
