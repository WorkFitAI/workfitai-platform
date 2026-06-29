package org.workfitai.authservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.anyLong;

import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.workfitai.authservice.config.SecurityTestConfig;
import org.workfitai.authservice.dto.response.AuthStatusResponse;
import org.workfitai.authservice.dto.response.LinkedAccountsResponse;
import org.workfitai.authservice.dto.response.OAuthAuthorizeResponse;
import org.workfitai.authservice.dto.response.OAuthCallbackResponse;
import org.workfitai.authservice.dto.response.OAuthLinkResponse;
import org.workfitai.authservice.enums.Provider;
import org.workfitai.authservice.model.User;
import org.workfitai.authservice.repository.UserRepository;
import org.workfitai.authservice.security.JwtAuthenticationFilter;
import org.workfitai.authservice.security.JwtService;
import org.workfitai.authservice.security.RefreshCookieFactory;
import org.workfitai.authservice.security.SessionValidationFilter;
import org.workfitai.authservice.service.oauth.OAuthService;

@WebMvcTest(OAuthController.class)
@Import(SecurityTestConfig.class)
class OAuthControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean OAuthService oauthService;
    @MockBean UserRepository userRepository;
    @MockBean JwtService jwtService;
    @MockBean RedisTemplate<String, String> redisTemplate;
    @MockBean RefreshCookieFactory refreshCookieFactory;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean SessionValidationFilter sessionValidationFilter;

    // ─── authorize ────────────────────────────────────────────────────────────

    @Test
    void authorize_validProvider_returns200() throws Exception {
        OAuthAuthorizeResponse resp = OAuthAuthorizeResponse.builder()
                .authorizationUrl("https://accounts.google.com/o/oauth2/auth?state=abc")
                .state("abc")
                .build();
        when(oauthService.authorize(any(Provider.class), any(), any())).thenReturn(resp);

        mvc.perform(get("/oauth/authorize/GOOGLE"))
                .andExpect(status().isOk());
    }

    @Test
    void authorize_invalidProvider_returns400() throws Exception {
        mvc.perform(get("/oauth/authorize/INVALID_PROVIDER"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "alice")
    void authorize_authenticatedUser_resolvesUserId() throws Exception {
        // Arrange: In WebMvcTest with mocked JwtFilter, @AuthenticationPrincipal resolves to
        // the WithMockUser principal name (String). Controller tries to findByUsername but
        // UserRepository is mocked — returns empty, so userId stays null.
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());

        OAuthAuthorizeResponse resp = OAuthAuthorizeResponse.builder()
                .authorizationUrl("https://accounts.google.com/auth")
                .state("GOOGLE:alice:LOGIN::client")
                .provider("GOOGLE")
                .build();
        // userId is null because user not found in repo
        when(oauthService.authorize(eq(Provider.GOOGLE), any(), eq(null))).thenReturn(resp);

        mvc.perform(get("/oauth/authorize/GOOGLE"))
                .andExpect(status().isOk());
    }

    // ─── callback — error flows (no redirect infrastructure needed) ──────────

    @Test
    void callback_withErrorParam_redirectsToFrontendWithError() throws Exception {
        mvc.perform(get("/oauth/callback/GOOGLE")
                        .param("code", "")
                        .param("state", "valid-state")
                        .param("error", "access_denied")
                        .param("error_description", "User denied access"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void callback_missingCode_redirectsToFrontendWithError() throws Exception {
        mvc.perform(get("/oauth/callback/GOOGLE")
                        .param("code", "")
                        .param("state", "valid-state"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void callback_missingState_redirectsToFrontendWithError() throws Exception {
        mvc.perform(get("/oauth/callback/GOOGLE")
                        .param("code", "auth-code-123")
                        .param("state", ""))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void callback_success_loginMode_redirectsWithSession() throws Exception {
        // Arrange: handleCallback returns Bearer token type (LOGIN mode)
        OAuthCallbackResponse callbackResp = OAuthCallbackResponse.builder()
                .token("access-token")
                .refreshToken("refresh-token")
                .tokenType("Bearer")
                .expiresIn(3600L)
                .build();
        when(oauthService.handleCallback(any(), anyString(), anyString(), any(), any()))
                .thenReturn(callbackResp);

        // Mock JWT username extraction for session building
        when(jwtService.extractUsername("access-token")).thenReturn("alice");
        // getClaims returns a Claims object — use a real io.jsonwebtoken.Claims stub via map
        io.jsonwebtoken.Claims claims = org.mockito.Mockito.mock(io.jsonwebtoken.Claims.class);
        when(claims.get("roles")).thenReturn(java.util.List.of("CANDIDATE"));
        when(claims.get("companyId")).thenReturn(null);
        when(jwtService.getClaims("access-token")).thenReturn(claims);

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);

        // Act & Assert: should redirect to /oauth-callback?session=...
        mvc.perform(get("/oauth/callback/GOOGLE")
                        .param("code", "auth-code-123")
                        .param("state", "GOOGLE:alice:LOGIN::client"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/oauth-callback")));
    }

    @Test
    void callback_success_linkMode_redirectsWithLinkSuccess() throws Exception {
        // Arrange: LINK_SUCCESS token type
        OAuthCallbackResponse callbackResp = OAuthCallbackResponse.builder()
                .token("link-token")
                .tokenType("LINK_SUCCESS")
                .build();
        when(oauthService.handleCallback(any(), anyString(), anyString(), any(), any()))
                .thenReturn(callbackResp);

        // Act & Assert: should redirect to /oauth-callback?status=link_success
        mvc.perform(get("/oauth/callback/GOOGLE")
                        .param("code", "link-code-123")
                        .param("state", "GOOGLE:alice:LINK:user-id-1:client"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("link_success")));
    }

    @Test
    void callback_serviceThrows_redirectsWithError() throws Exception {
        when(oauthService.handleCallback(any(), anyString(), anyString(), any(), any()))
                .thenThrow(new RuntimeException("OAuth provider unavailable"));

        mvc.perform(get("/oauth/callback/GOOGLE")
                        .param("code", "bad-code")
                        .param("state", "valid-state"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("error")));
    }

    // ─── exchange ─────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void exchangeSession_invalidSession_returns401() throws Exception {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenReturn(null);

        mvc.perform(get("/oauth/exchange")
                        .param("session", "nonexistent-session"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @SuppressWarnings("unchecked")
    void exchangeSession_validSession_returns200WithTokens() throws Exception {
        // Build a valid JSON session payload
        String sessionJson = """
                {
                    "accessToken": "access-tok",
                    "refreshToken": "refresh-tok",
                    "username": "alice",
                    "roles": ["CANDIDATE"],
                    "companyId": null,
                    "expiresIn": 3600
                }
                """;

        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.get("oauth:session:valid-session-id")).thenReturn(sessionJson);

        ResponseCookie cookie = ResponseCookie.from("refresh_token", "refresh-tok")
                .httpOnly(true).path("/").build();
        when(refreshCookieFactory.build(anyString(), anyLong())).thenReturn(cookie);
        when(jwtService.getRefreshExpMs()).thenReturn(604_800_000L);

        mvc.perform(get("/oauth/exchange")
                        .param("session", "valid-session-id"))
                .andExpect(status().isOk());

        verify(redisTemplate).delete("oauth:session:valid-session-id");
    }

    @Test
    @SuppressWarnings("unchecked")
    void exchangeSession_invalidJson_returns500() throws Exception {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.get("oauth:session:bad-json")).thenReturn("{invalid-json}");

        mvc.perform(get("/oauth/exchange")
                        .param("session", "bad-json"))
                .andExpect(status().isInternalServerError());
    }

    // ─── linked-accounts ──────────────────────────────────────────────────────

    @Test
    void getLinkedAccounts_returns200() throws Exception {
        when(oauthService.getLinkedAccounts(any()))
                .thenReturn(LinkedAccountsResponse.builder().build());

        mvc.perform(get("/oauth/linked-accounts"))
                .andExpect(status().isOk());
    }

    // ─── auth-status ──────────────────────────────────────────────────────────

    @Test
    void getAuthStatus_returns200() throws Exception {
        when(oauthService.getAuthStatus(any()))
                .thenReturn(AuthStatusResponse.builder().build());

        mvc.perform(get("/oauth/auth-status"))
                .andExpect(status().isOk());
    }

    // ─── link ─────────────────────────────────────────────────────────────────

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "alice")
    void linkProvider_validRequest_returns200() throws Exception {
        when(oauthService.linkProvider(any(), any(Provider.class), any()))
                .thenReturn(OAuthLinkResponse.builder().build());

        mvc.perform(post("/oauth/link/GOOGLE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"auth-code-123\", \"redirectUri\": \"http://localhost:3000/callback\"}"))
                .andExpect(status().isOk());
    }

    // ─── unlink ───────────────────────────────────────────────────────────────

    @Test
    void unlinkProvider_returns204() throws Exception {
        mvc.perform(delete("/oauth/unlink/GOOGLE"))
                .andExpect(status().isNoContent());

        verify(oauthService).unlinkProvider(any(), any(Provider.class));
    }
}
