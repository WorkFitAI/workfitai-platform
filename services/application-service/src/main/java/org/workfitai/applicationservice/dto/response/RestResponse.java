package org.workfitai.applicationservice.dto.response;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic REST response wrapper used across all endpoints.
 *
 * Provides consistent API response structure:
 * - status: HTTP status code
 * - message: Human-readable message
 * - data: Payload (null for errors)
 * - timestamp: When the response was generated
 *
 * Usage patterns:
 * - RestResponse.success(data) → 200 with data
 * - RestResponse.created(data) → 201 with data
 * - RestResponse.error(code, msg) → Error with message, no data
 *
 * This follows the same pattern as job-service and cv-service
 * for consistency across the platform.
 *
 * Note: @NoArgsConstructor required for Feign/Jackson deserialization
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Standard API response wrapper")
public class RestResponse<T> {

    @Schema(description = "HTTP status code", example = "200")
    private int status;

    @Schema(description = "Response message", example = "Success")
    private String message;

    /**
     * Response payload. Excluded from JSON if null (for error responses).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Response data payload")
    private T data;

    @Schema(description = "Response timestamp", example = "2024-01-15T10:30:00")
    private LocalDateTime timestamp;

    /**
     * Constructor for responses without data (errors).
     */
    public RestResponse(int status, String message) {
        this.status = status;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }

    public RestResponse(int status, String message, T data) {
        this.status = status;
        this.message = message;
        this.data = data;
        this.timestamp = LocalDateTime.now();
    }

    // Factory methods

    public static <T> RestResponse<T> success(T data) {
        return new RestResponse<>(200, "Success", data);
    }

    public static <T> RestResponse<T> success(String message, T data) {
        return new RestResponse<>(200, message, data);
    }

    public static <T> RestResponse<T> created(T data) {
        return new RestResponse<>(201, "Created", data);
    }

    public static <T> RestResponse<T> deleted() {
        return new RestResponse<>(200, "Deleted");
    }

    public static <T> RestResponse<T> error(int code, String message) {
        return new RestResponse<>(code, message);
    }
}
