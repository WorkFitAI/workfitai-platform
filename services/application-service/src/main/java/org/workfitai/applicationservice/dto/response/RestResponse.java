package org.workfitai.applicationservice.dto.response;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

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
 */
@Data
@Schema(description = "Standard API response wrapper")
public class RestResponse<T> {

    @Schema(description = "HTTP status code", example = "200")
    private final int status;

    @Schema(description = "Response message", example = "Success")
    private final String message;

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

    /**
     * Constructor for responses with data (success).
     */
    public RestResponse(int status, String message, T data) {
        this.status = status;
        this.message = message;
        this.data = data;
        this.timestamp = LocalDateTime.now();
    }

    // ==================== Static Factory Methods ====================

    /**
     * Creates a successful response with HTTP 200 OK.
     * 
     * @param data The response payload
     * @return RestResponse with status 200 and the provided data
     */
    public static <T> RestResponse<T> success(T data) {
        return new RestResponse<>(200, "Success", data);
    }

    /**
     * Creates a successful response with HTTP 200 OK and custom message.
     * 
     * @param message Custom success message
     * @param data    The response payload
     * @return RestResponse with status 200, custom message, and data
     */
    public static <T> RestResponse<T> success(String message, T data) {
        return new RestResponse<>(200, message, data);
    }

    /**
     * Creates a response for newly created resource (HTTP 201 Created).
     * Used after successful POST operations.
     * 
     * @param data The created resource
     * @return RestResponse with status 201 and the created data
     */
    public static <T> RestResponse<T> created(T data) {
        return new RestResponse<>(201, "Created", data);
    }

    /**
     * Creates a response for successful deletion (HTTP 200 OK).
     * 
     * @return RestResponse with status 200 and deletion confirmation message
     */
    public static <T> RestResponse<T> deleted() {
        return new RestResponse<>(200, "Deleted");
    }

    /**
     * Creates an error response without data.
     * 
     * @param code    HTTP status code
     * @param message Error message
     * @return RestResponse with the error status and message
     */
    public static <T> RestResponse<T> error(int code, String message) {
        return new RestResponse<>(code, message);
    }
}
