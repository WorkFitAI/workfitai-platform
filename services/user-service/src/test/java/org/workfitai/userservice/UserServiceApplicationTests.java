package org.workfitai.userservice;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.workfitai.userservice.client.AuthFeignClient;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;

import static org.mockito.Mockito.when;

@SpringBootTest
class UserServiceApplicationTests {

    @TestBean
    private AuthFeignClient authFeignClient;

    static AuthFeignClient authFeignClient() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair keyPair = gen.generateKeyPair();
        String encodedPublicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        AuthFeignClient stub = Mockito.mock(AuthFeignClient.class);
        when(stub.getPublicKey()).thenReturn(Map.of("publicKey", encodedPublicKey));
        return stub;
    }

    @Test
    void contextLoads() {
    }
}
