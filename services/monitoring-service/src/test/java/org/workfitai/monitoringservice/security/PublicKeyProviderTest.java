package org.workfitai.monitoringservice.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicKeyProviderTest {

    @Mock
    private RestTemplate restTemplate;

    private PublicKeyProvider publicKeyProvider;

    private void init() {
        publicKeyProvider = new PublicKeyProvider(restTemplate);
        ReflectionTestUtils.setField(publicKeyProvider, "authServiceUrl", "http://auth-service:8081");
    }

    private String sampleEncodedPublicKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    @SuppressWarnings("unchecked")
    private void stubAuthServiceResponse(Map<String, String> body) {
        ResponseEntity<Map<String, String>> response = mock(ResponseEntity.class);
        when(response.getBody()).thenReturn(body);
        when(restTemplate.exchange(
                eq("http://auth-service:8081/api/v1/keys/public"),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)))
                .thenReturn(response);
    }

    @Test
    void getPublicKey_fetchesAndCachesKey_onFirstCall() throws Exception {
        init();
        String encoded = sampleEncodedPublicKey();
        stubAuthServiceResponse(Map.of("publicKey", encoded));

        RSAPublicKey key = publicKeyProvider.getPublicKey();

        assertThat(key).isNotNull();
        assertThat(key.getAlgorithm()).isEqualTo("RSA");
    }

    @Test
    void getPublicKey_returnsCachedKey_withoutRefetching() throws Exception {
        init();
        String encoded = sampleEncodedPublicKey();
        byte[] decoded = Base64.getDecoder().decode(encoded);
        RSAPublicKey expected = (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new java.security.spec.X509EncodedKeySpec(decoded));
        ReflectionTestUtils.setField(publicKeyProvider, "cachedKey", expected);

        RSAPublicKey key = publicKeyProvider.getPublicKey();

        assertThat(key).isEqualTo(expected);
    }

    @Test
    void getPublicKey_throws_whenAuthServiceResponseMissingKey() {
        init();
        stubAuthServiceResponse(Map.of());

        assertThrows(IllegalStateException.class, publicKeyProvider::getPublicKey);
    }

    @Test
    void getPublicKey_throws_whenAuthServiceResponseNull() {
        init();
        @SuppressWarnings("unchecked")
        ResponseEntity<Map<String, String>> response = mock(ResponseEntity.class);
        when(response.getBody()).thenReturn(null);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class)))
                .thenReturn(response);

        assertThrows(IllegalStateException.class, publicKeyProvider::getPublicKey);
    }

    @Test
    void refreshKey_updatesCachedKey_onSuccess() throws Exception {
        init();
        String encoded = sampleEncodedPublicKey();
        stubAuthServiceResponse(Map.of("publicKey", encoded));

        publicKeyProvider.refreshKey();

        assertThat(ReflectionTestUtils.getField(publicKeyProvider, "cachedKey")).isNotNull();
    }

    @Test
    void refreshKey_throwsRuntimeException_whenFetchFails() {
        init();
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class)))
                .thenThrow(new RuntimeException("auth-service unreachable"));

        assertThrows(RuntimeException.class, publicKeyProvider::refreshKey);
    }

    @Test
    void init_swallowsException_andLeavesCachedKeyNull() {
        init();
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class)))
                .thenThrow(new RuntimeException("auth-service unreachable"));

        publicKeyProvider.init();

        assertThat(ReflectionTestUtils.getField(publicKeyProvider, "cachedKey")).isNull();
    }
}
