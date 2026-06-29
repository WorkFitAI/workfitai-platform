package org.workfitai.cvservice.controller;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.workfitai.cvservice.client.AuthFeignClient;
import org.workfitai.cvservice.converter.JwtAuthConverter;
import org.workfitai.cvservice.model.dto.response.ResCvDTO;
import org.workfitai.cvservice.security.PublicKeyProvider;
import org.workfitai.cvservice.security.SecurityConfig;
import org.workfitai.cvservice.security.SecurityJwtConfiguration;
import org.workfitai.cvservice.service.iCVService;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the REAL SecurityConfig filter chain (unlike CVControllerTest, which disables
 * filters to focus on request mapping). Signs JWTs with a key pair whose public half is
 * served by a stubbed AuthFeignClient, so NimbusJwtDecoder performs genuine signature
 * verification and JwtAuthConverter performs genuine role mapping.
 */
@WebMvcTest(CVController.class)
@Import({ SecurityConfig.class, SecurityJwtConfiguration.class, JwtAuthConverter.class, PublicKeyProvider.class })
class CVControllerSecurityTest {

    private static KeyPair keyPair;

    @TestBean
    private AuthFeignClient authFeignClient;

    static AuthFeignClient authFeignClient() throws Exception {
        keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String encodedPublicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        AuthFeignClient stub = Mockito.mock(AuthFeignClient.class);
        when(stub.getPublicKey()).thenReturn(Map.of("publicKey", encodedPublicKey));
        return stub;
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private iCVService service;

    private String signedJwtWithRole(String role) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("alice")
                .claim("roles", List.of(role))
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .build();
        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        signedJWT.sign(new RSASSASigner((RSAPrivateKey) keyPair.getPrivate()));
        return signedJWT.serialize();
    }

    @Test
    void getCV_returns401_whenNoToken() throws Exception {
        mockMvc.perform(get("/candidate/id/cv1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCV_returns403_whenRoleIsNotCandidate() throws Exception {
        mockMvc.perform(get("/candidate/id/cv1")
                        .header("Authorization", "Bearer " + signedJwtWithRole("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCV_returns200_whenRoleIsCandidate() throws Exception {
        when(service.getById("cv1")).thenReturn(ResCvDTO.builder().cvId("cv1").build());

        mockMvc.perform(get("/candidate/id/cv1")
                        .header("Authorization", "Bearer " + signedJwtWithRole("CANDIDATE")))
                .andExpect(status().isOk());
    }
}
