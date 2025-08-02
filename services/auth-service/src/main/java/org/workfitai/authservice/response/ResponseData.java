package org.workfitai.authservice.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ResponseData<T> implements Serializable {
    private final int status;

    private final String message;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private T data;

    private LocalDateTime timestamp;

    public ResponseData(int status, String message) {
        this.status = status;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }

    public ResponseData(int status, String message, T data) {
        this.status = status;
        this.message = message;
        this.data = data;
        this.timestamp = LocalDateTime.now();
    }

    public static <T> ResponseData<T> success(T data) {
        return new ResponseData<>(200, "Success", data);
    }

    public static <T> ResponseData<T> error(int code, String message) {
        return new ResponseData<>(code, message);
    }
}
