package org.workfitai.jobservice;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.workfitai.jobservice.client.AuthFeignClient;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;

import static org.mockito.Mockito.when;

@SpringBootTest
class JobServiceApplicationTests {

    @TestBean
    private AuthFeignClient authFeignClient;

    static AuthFeignClient authFeignClient() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String encodedPublicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        AuthFeignClient stub = Mockito.mock(AuthFeignClient.class);
        when(stub.getPublicKey()).thenReturn(Map.of("publicKey", encodedPublicKey));
        return stub;
    }

    @Test
    void contextLoads() {
    }

}
