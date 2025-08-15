package org.workfitai.authservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.workfitai.authservice.response.ResponseData;

import java.util.NoSuchElementException;

@Slf4j
@RestControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ResponseData<Void>> handleStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(ResponseData.error(ex.getStatusCode().value(),
                        ex.getReason() != null ? ex.getReason() : "Error"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ResponseData<Void>> handleIllegal(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ResponseData.error(400, ex.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ResponseData<Void>> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ResponseData.error(404, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseData<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.badRequest().body(ResponseData.error(400, msg));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseData<Void>> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ResponseData.error(500, "Internal server error"));
    }
}