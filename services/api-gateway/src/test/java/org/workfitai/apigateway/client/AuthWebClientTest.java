package org.workfitai.apigateway.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

/**
 * Tests for {@link AuthWebClient}.
 *
 * <p>The reactive {@link WebClient} fluent chain is mocked step by step since raw generic
 * wildcards (e.g. {@code RequestHeadersUriSpec<?>}) cannot be stubbed type-safely; casts are
 * required and scoped with {@code @SuppressWarnings}.
 */
@ExtendWith(MockitoExtension.class)
class AuthWebClientTest {

    @Mock WebClient.Builder builder;
    @Mock WebClient webClient;
    @Mock WebClient.RequestHeadersUriSpec<?> uriSpec;
    @Mock WebClient.RequestHeadersSpec<?> headersSpec;
    @Mock WebClient.ResponseSpec responseSpec;

    AuthWebClient authWebClient;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() {
        authWebClient = new AuthWebClient(builder);
        ReflectionTestUtils.setField(authWebClient, "authServiceUrl", "http://auth-service");

        when(builder.build()).thenReturn(webClient);
        when(webClient.get()).thenReturn((WebClient.RequestHeadersUriSpec) uriSpec);
        when(uriSpec.uri(anyString())).thenReturn((WebClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getPublicKey_returnsBodyFromDownstreamResponse() {
        // Arrange
        Map<String, String> body = Map.of("publicKey", "encoded-key-value");
        when(responseSpec.bodyToMono(Map.class)).thenReturn((Mono) Mono.just(body));

        // Act
        Map<String, String> result = authWebClient.getPublicKey();

        // Assert
        assertThat(result).containsEntry("publicKey", "encoded-key-value");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getPublicKey_requestsConfiguredAuthServiceUrl() {
        // Arrange
        when(responseSpec.bodyToMono(Map.class)).thenReturn((Mono) Mono.just(Map.of()));

        // Act
        authWebClient.getPublicKey();

        // Assert
        verify(uriSpec).uri("http://auth-service/api/v1/keys/public");
    }
}
