package org.workfitai.notificationservice.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.workfitai.notificationservice.client.AuthFeignClient;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicKeyProviderTest {

    @Mock
    private AuthFeignClient authFeignClient;

    private PublicKeyProvider publicKeyProvider;

    private void init() {
        publicKeyProvider = new PublicKeyProvider(authFeignClient);
    }

    private String sampleEncodedPublicKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    @Test
    void getPublicKey_fetchesAndCachesKey_onFirstCall() throws Exception {
        init();
        when(authFeignClient.getPublicKey()).thenReturn(Map.of("publicKey", sampleEncodedPublicKey()));

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
                .generatePublic(new X509EncodedKeySpec(decoded));
        ReflectionTestUtils.setField(publicKeyProvider, "cachedKey", expected);

        RSAPublicKey key = publicKeyProvider.getPublicKey();

        assertThat(key).isEqualTo(expected);
    }

    @Test
    void getPublicKey_throws_whenAuthFeignClientThrows() {
        init();
        when(authFeignClient.getPublicKey()).thenThrow(new RuntimeException("auth-service unreachable"));

        assertThrows(RuntimeException.class, publicKeyProvider::getPublicKey);
    }

    @Test
    void init_swallowsException_andLeavesCachedKeyNull() {
        init();
        when(authFeignClient.getPublicKey()).thenThrow(new RuntimeException("auth-service unreachable"));

        publicKeyProvider.init();

        assertThat(ReflectionTestUtils.getField(publicKeyProvider, "cachedKey")).isNull();
    }

    @Test
    void init_cachesKey_onSuccess() throws Exception {
        init();
        when(authFeignClient.getPublicKey()).thenReturn(Map.of("publicKey", sampleEncodedPublicKey()));

        publicKeyProvider.init();

        assertThat(ReflectionTestUtils.getField(publicKeyProvider, "cachedKey")).isNotNull();
    }
}
