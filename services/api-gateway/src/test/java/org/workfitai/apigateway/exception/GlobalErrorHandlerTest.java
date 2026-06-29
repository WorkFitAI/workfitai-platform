package org.workfitai.apigateway.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.server.ResponseStatusException;
import org.workfitai.apigateway.model.dto.response.ResponseData;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import reactor.core.publisher.Mono;

class GlobalErrorHandlerTest {

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient
                .bindToController(new ThrowingController())
                .controllerAdvice(new GlobalErrorHandler())
                .build();
    }

    // ─── Inner controller that throws exceptions ──────────────────────────────

    @RestController
    static class ThrowingController {

        @GetMapping("/test/throw/not-found")
        Mono<String> throwNotFound() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not found");
        }

        @GetMapping("/test/throw/unauthorized")
        Mono<String> throwUnauthorized() {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized");
        }

        @GetMapping("/test/throw/forbidden")
        Mono<String> throwForbidden() {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "access denied");
        }

        @GetMapping("/test/throw/no-reason")
        Mono<String> throwNoReason() {
            // ResponseStatusException with no reason → getReason() == null
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        @GetMapping("/test/throw/generic")
        Mono<String> throwGeneric() {
            throw new RuntimeException("unexpected error");
        }

        @GetMapping("/test/throw/constraint")
        Mono<String> throwConstraintEmpty() {
            // empty violation set → handler uses orElse("Constraint violation")
            throw new ConstraintViolationException("violation", Set.of());
        }

        @PostMapping("/test/throw/validation")
        Mono<String> throwValidation(@Valid @RequestBody ValidatedDto dto) {
            return Mono.just("ok");
        }

        @Data
        static class ValidatedDto {
            @NotBlank(message = "name is required")
            private String name;
        }
    }

    // ─── ResponseStatusException ──────────────────────────────────────────────

    @Test
    void handleStatusException_404_bodyContainsStatusCode() {
        client.get().uri("/test/throw/not-found")
                .exchange()
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.message").isEqualTo("not found");
    }

    @Test
    void handleStatusException_401_bodyContainsStatusCode() {
        client.get().uri("/test/throw/unauthorized")
                .exchange()
                .expectBody()
                .jsonPath("$.status").isEqualTo(401);
    }

    @Test
    void handleStatusException_403_bodyContainsStatusAndReason() {
        client.get().uri("/test/throw/forbidden")
                .exchange()
                .expectBody()
                .jsonPath("$.status").isEqualTo(403)
                .jsonPath("$.message").isEqualTo("access denied");
    }

    // ─── Generic exception → 500 ──────────────────────────────────────────────

    @Test
    void handleGenericException_returns500InBody() {
        client.get().uri("/test/throw/generic")
                .exchange()
                .expectBody()
                .jsonPath("$.status").isEqualTo(500);
    }

    @Test
    void handleGenericException_doesNotExposeInternalMessage() {
        client.get().uri("/test/throw/generic")
                .exchange()
                .expectBody()
                .jsonPath("$.message").value(
                        msg -> assertThat(msg.toString())
                                .doesNotContain("unexpected error"));
    }

    // ─── Validation exception → 400 ───────────────────────────────────────────

    @Test
    void handleValidationException_returns400InBody_whenNameBlank() {
        client.post().uri("/test/throw/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue("{\"name\": \"\"}"))
                .exchange()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400);
    }

    // ─── Invalid JSON → 400 ───────────────────────────────────────────────────

    @Test
    void handleInvalidJson_returns400InBody_whenMalformedJson() {
        client.post().uri("/test/throw/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue("{malformed"))
                .exchange()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400);
    }

    // ─── ConstraintViolationException (WebTestClient) ─────────────────────────

    @Test
    void handleConstraintViolation_emptyViolations_returns400WithFallbackMessage() {
        client.get().uri("/test/throw/constraint")
                .exchange()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400);
    }

    // ─── Direct unit tests for handlers without WebTestClient ─────────────────
    //
    // These cover branches that are hard or impossible to trigger through the
    // controller (e.g. null reason, mocked violations with path/message).

    @Test
    @SuppressWarnings("unchecked")
    void handleConstraintViolation_withViolation_includesPropertyPathAndMessage() {
        GlobalErrorHandler handler = new GlobalErrorHandler();

        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        Path mockPath = mock(Path.class);
        when(mockPath.toString()).thenReturn("email");
        when(violation.getPropertyPath()).thenReturn(mockPath);
        when(violation.getMessage()).thenReturn("must be a valid email");

        ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));
        ResponseData<Void> result = handler.handleConstraintViolation(ex);

        assertThat(result.getStatus()).isEqualTo(400);
        assertThat(result.getMessage()).contains("email");
        assertThat(result.getMessage()).contains("must be a valid email");
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleConstraintViolation_emptyViolationSet_usesFallbackMessage() {
        GlobalErrorHandler handler = new GlobalErrorHandler();

        ResponseData<Void> result = handler.handleConstraintViolation(
                new ConstraintViolationException(Set.of()));

        assertThat(result.getStatus()).isEqualTo(400);
        assertThat(result.getMessage()).contains("Constraint violation");
    }

    @Test
    void handleStatusException_nullReason_messageIsNull() {
        GlobalErrorHandler handler = new GlobalErrorHandler();

        // ResponseStatusException(status) sets reason = null
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.FORBIDDEN);
        ResponseData<Void> result = handler.handleStatusException(ex);

        assertThat(result.getStatus()).isEqualTo(403);
        assertThat(result.getMessage()).isNull();
    }

    @Test
    void handleGenericException_nullExceptionMessage_returnsStandardInternalServerErrorMessage() {
        GlobalErrorHandler handler = new GlobalErrorHandler();

        ResponseData<Void> result = handler.handleGenericException(new RuntimeException((String) null));

        assertThat(result.getStatus()).isEqualTo(500);
        assertThat(result.getMessage()).isEqualTo("Internal server error. Please try again later.");
    }

    @Test
    void handleGenericException_anyException_alwaysReturns500WithStandardMessage() {
        GlobalErrorHandler handler = new GlobalErrorHandler();

        ResponseData<Void> result = handler.handleGenericException(new IllegalStateException("boom"));

        assertThat(result.getStatus()).isEqualTo(500);
        assertThat(result.getMessage()).doesNotContain("boom");
    }

    @Test
    void handleInvalidJson_directInvocation_returns400WithMalformedMessage() {
        GlobalErrorHandler handler = new GlobalErrorHandler();
        org.springframework.http.converter.HttpMessageNotReadableException ex =
                mock(org.springframework.http.converter.HttpMessageNotReadableException.class);

        ResponseData<Void> result = handler.handleInvalidJson(ex);

        assertThat(result.getStatus()).isEqualTo(400);
        assertThat(result.getMessage()).contains("Malformed JSON");
    }
}
