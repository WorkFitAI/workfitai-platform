package org.workfitai.authservice.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.workfitai.authservice.constants.Messages;

@Data
public class ResponseData<T> implements Serializable {
    private final int status;
    private final String message;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final T data;
    private final LocalDateTime timestamp;

    private ResponseData(int status, String message, T data) {
        this.status = status;
        this.message = message;
        this.data = data;
        this.timestamp = LocalDateTime.now();
    }

    public static <T> ResponseData<T> success(T data) {
        return success(Messages.Success.OPERATION_SUCCESS, data);
    }

    public static ResponseData<Void> success(String message) {
        return success(message, null);
    }

    public static <T> ResponseData<T> success(String message, T data) {
        return new ResponseData<>(HttpStatus.OK.value(), message, data);
    }
}
