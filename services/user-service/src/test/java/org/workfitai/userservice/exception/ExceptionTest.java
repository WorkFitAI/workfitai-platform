package org.workfitai.userservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExceptionTest {

    // ---- BadRequestException ----

    @Test
    void badRequest_message_constructor() {
        BadRequestException ex = new BadRequestException("bad input");
        assertThat(ex.getMessage()).isEqualTo("bad input");
    }

    @Test
    void badRequest_messageAndCause_constructor() {
        Throwable cause = new RuntimeException("root");
        BadRequestException ex = new BadRequestException("wrapped", cause);
        assertThat(ex.getMessage()).isEqualTo("wrapped");
        assertThat(ex.getCause()).isEqualTo(cause);
    }

    @Test
    void badRequest_isRuntimeException() {
        assertThatThrownBy(() -> { throw new BadRequestException("fail"); })
                .isInstanceOf(RuntimeException.class);
    }

    // ---- NotFoundException ----

    @Test
    void notFound_message_constructor() {
        NotFoundException ex = new NotFoundException("not found");
        assertThat(ex.getMessage()).isEqualTo("not found");
    }

    @Test
    void notFound_messageAndCause_constructor() {
        Throwable cause = new RuntimeException("root");
        NotFoundException ex = new NotFoundException("resource missing", cause);
        assertThat(ex.getCause()).isEqualTo(cause);
    }

    @Test
    void notFound_isRuntimeException() {
        assertThatThrownBy(() -> { throw new NotFoundException("gone"); })
                .isInstanceOf(RuntimeException.class);
    }

    // ---- ApiException ----

    @Test
    void apiException_messageOnly_defaultsBadRequest() {
        ApiException ex = new ApiException("msg");
        assertThat(ex.getMessage()).isEqualTo("msg");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getErrors()).isEmpty();
    }

    @Test
    void apiException_messageAndStatus_constructor() {
        ApiException ex = new ApiException("api error", HttpStatus.NOT_FOUND);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void apiException_messageAndErrors_constructor() {
        java.util.List<String> errors = java.util.List.of("field required");
        ApiException ex = new ApiException("validation", errors);
        assertThat(ex.getErrors()).containsExactly("field required");
    }

    @Test
    void apiException_allArgs_constructor() {
        java.util.List<String> errors = java.util.List.of("e1", "e2");
        ApiException ex = new ApiException("all", HttpStatus.CONFLICT, errors);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getErrors()).hasSize(2);
    }

    @Test
    void apiException_validationError_factory() {
        ApiException ex = ApiException.validationError(java.util.List.of("invalid email"));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("Validation error");
    }

    @Test
    void apiException_notFound_messageFactory() {
        ApiException ex = ApiException.notFound("User not found");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void apiException_notFound_resourceAndId_factory() {
        ApiException ex = ApiException.notFound("User", "uuid-123");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getMessage()).contains("uuid-123");
    }

    @Test
    void apiException_conflict_factory() {
        ApiException ex = ApiException.conflict("duplicate email");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void apiException_isRuntimeException() {
        assertThatThrownBy(() -> { throw new ApiException("x", HttpStatus.INTERNAL_SERVER_ERROR); })
                .isInstanceOf(RuntimeException.class);
    }
}
