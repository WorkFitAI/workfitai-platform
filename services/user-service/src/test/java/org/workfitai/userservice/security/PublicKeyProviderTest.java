package org.workfitai.userservice.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.userservice.client.AuthFeignClient;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicKeyProviderTest {

    @Mock
    AuthFeignClient authFeignClient;

    PublicKeyProvider provider;
    String encodedPublicKey;

    @BeforeEach
    void setUp() throws Exception {
        provider = new PublicKeyProvider(authFeignClient);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        encodedPublicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    @Test
    void init_fetchesAndCachesPublicKey() throws Exception {
        when(authFeignClient.getPublicKey()).thenReturn(Map.of("publicKey", encodedPublicKey));

        provider.init();
        RSAPublicKey first = provider.getPublicKey();
        RSAPublicKey second = provider.getPublicKey();

        assertThat(first).isSameAs(second);
        assertThat(first.getAlgorithm()).isEqualTo("RSA");
        verify(authFeignClient, times(1)).getPublicKey();
    }

    @Test
    void init_failureDefersFetchUntilPublicKeyRequested() throws Exception {
        when(authFeignClient.getPublicKey())
                .thenThrow(new IllegalStateException("auth unavailable"))
                .thenReturn(Map.of("publicKey", encodedPublicKey));

        provider.init();

        assertThat(provider.getPublicKey().getAlgorithm()).isEqualTo("RSA");
        verify(authFeignClient, times(2)).getPublicKey();
    }

    @Test
    void invalidateKey_forcesNextRequestToRefetch() throws Exception {
        when(authFeignClient.getPublicKey()).thenReturn(Map.of("publicKey", encodedPublicKey));

        RSAPublicKey first = provider.getPublicKey();
        provider.invalidateKey();
        RSAPublicKey second = provider.getPublicKey();

        assertThat(second).isNotNull();
        assertThat(second).isNotSameAs(first);
        verify(authFeignClient, times(2)).getPublicKey();
    }

    @Test
    void getPublicKey_invalidKeyResponsePropagatesFailure() {
        when(authFeignClient.getPublicKey()).thenReturn(Map.of("publicKey", "not-base64"));

        assertThatThrownBy(() -> provider.getPublicKey())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
