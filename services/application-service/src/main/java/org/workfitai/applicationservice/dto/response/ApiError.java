package org.workfitai.applicationservice.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * Standardized error response for API errors.
 * 
 * This class follows the same pattern as user-service's ApiError
 * for consistency across microservices.
 * 
 * Response format:
 * 
 * <pre>
 * {
 *   "status": 409,
 *   "message": "You have already applied to this job",
 *   "errors": [],
 *   "timestamp": "2024-01-15T10:30:00"
 * }
 * </pre>
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY) // Exclude null/empty fields from JSON
@Schema(description = "Error response containing status, message, and optional error details")
public class ApiError {

    /**
     * HTTP status code (e.g., 400, 404, 409, 500).
     */
    @Schema(description = "HTTP status code", example = "409")
    private int status;

    /**
     * Human-readable error message.
     */
    @Schema(description = "Error message", example = "You have already applied to this job")
    private String message;

    /**
     * List of detailed error messages (e.g., validation errors).
     */
    @Schema(description = "List of detailed error messages")
    private List<String> errors;

    /**
     * Timestamp when the error occurred.
     */
    @Schema(description = "Timestamp of the error", example = "2024-01-15T10:30:00")
    private LocalDateTime timestamp;

    /**
     * Factory method to create an ApiError with current timestamp.
     * 
     * @param status  HTTP status
     * @param message Error message
     * @param errors  List of detailed errors (can be empty)
     * @return ApiError instance
     */
    public static ApiError of(int status, String message, List<String> errors) {
        return ApiError.builder()
                .status(status)
                .message(message)
                .errors(errors != null ? errors : List.of())
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Factory method for single message errors.
     * 
     * @param status  HTTP status
     * @param message Error message
     * @return ApiError instance
     */
    public static ApiError of(int status, String message) {
        return of(status, message, List.of());
    }
}
