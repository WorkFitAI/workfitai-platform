package org.workfitai.authservice.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.workfitai.authservice.config.RsaKeyProperties;
import org.workfitai.authservice.config.SecurityTestConfig;
import org.workfitai.authservice.security.JwtAuthenticationFilter;
import org.workfitai.authservice.security.SessionValidationFilter;

@WebMvcTest(PublicKeyController.class)
@Import(SecurityTestConfig.class)
class PublicKeyControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean RsaKeyProperties rsaKeys;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean SessionValidationFilter sessionValidationFilter;

    private RSAPublicKey testPublicKey;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        testPublicKey = (RSAPublicKey) gen.generateKeyPair().getPublic();
        when(rsaKeys.publicKey()).thenReturn(testPublicKey);
    }

    @Test
    void getPublicKey_returns200WithKeyFields() throws Exception {
        mockMvc.perform(get("/api/v1/keys/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alg").value("RS256"))
                .andExpect(jsonPath("$.type").value("RSA"))
                .andExpect(jsonPath("$.publicKey").exists());
    }
}
