package org.workfitai.cvservice.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.OffsetDateTime;


@Data
public class RestResponse<T> {
    private final int status;

    private final String message;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private T data;

    private OffsetDateTime timestamp;

    public RestResponse(int status, String message) {
        this.status = status;
        this.message = message;
        this.timestamp = OffsetDateTime.now();
    }

    public RestResponse(int status, String message, T data) {
        this.status = status;
        this.message = message;
        this.data = data;
        this.timestamp = OffsetDateTime.now();
    }

    public static <T> RestResponse<T> success(T data) {
        return new RestResponse<>(200, "Success", data);
    }

    public static <T> RestResponse<T> created(T data) {
        return new RestResponse<>(200, "Created", data);
    }

    public static <T> RestResponse<T> deleted() {
        return new RestResponse<>(200, "Deleted");
    }

    public static <T> RestResponse<T> error(int code, String message) {
        return new RestResponse<>(code, message);
    }

}

