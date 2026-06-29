package org.workfitai.authservice.service.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.workfitai.authservice.dto.request.OAuthAuthorizeRequest;
import org.workfitai.authservice.dto.request.OAuthLinkRequest;
import org.workfitai.authservice.dto.response.OAuthCallbackResponse;
import org.workfitai.authservice.dto.response.OAuthLinkResponse;
import org.workfitai.authservice.exception.CannotUnlinkLastAuthMethodException;
import org.workfitai.authservice.exception.InvalidOAuthStateException;
import org.workfitai.authservice.dto.response.AuthStatusResponse;
import org.workfitai.authservice.dto.response.LinkedAccountsResponse;
import org.workfitai.authservice.dto.response.LinkedProviderResponse;
import org.workfitai.authservice.dto.response.OAuthAuthorizeResponse;
import org.workfitai.authservice.enums.Provider;
import org.workfitai.authservice.enums.UserStatus;
import org.workfitai.authservice.exception.OAuthProviderException;
import org.workfitai.authservice.messaging.UserRegistrationProducer;
import org.workfitai.authservice.model.OAuthProvider;
import org.workfitai.authservice.model.User;
import org.workfitai.authservice.repository.UserRepository;
import org.workfitai.authservice.security.JwtService;
import org.workfitai.authservice.service.RefreshTokenService;
import org.workfitai.authservice.service.SessionService;
import org.workfitai.authservice.service.oauth.provider.GitHubOAuthService;
import org.workfitai.authservice.service.oauth.provider.GoogleOAuthService;
import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
class OAuthServiceTest {

    @Mock GoogleOAuthService googleOAuthService;
    @Mock GitHubOAuthService gitHubOAuthService;
    @Mock OAuthProviderService oauthProviderService;
    @Mock OAuthEventPublisher oauthEventPublisher;
    @Mock UserRepository userRepository;
    @Mock BCryptPasswordEncoder encoder;
    @Mock JwtService jwtService;
    @Mock RefreshTokenService refreshTokenService;
    @Mock SessionService sessionService;
    @Mock UserRegistrationProducer userRegistrationProducer;
    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock HttpServletRequest httpRequest;

    @InjectMocks OAuthService oauthService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(oauthService, "frontendBaseUrl", "http://localhost:3000");
        ReflectionTestUtils.setField(oauthService, "backendBaseUrl", "http://localhost:9085/");
        Mockito.lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // IpAddressUtil loops over request.getHeader(...) — null returns are safe
        Mockito.lenient().when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    private User activeUser() {
        User u = new User();
        u.setId("user-id-1");
        u.setUsername("alice");
        u.setEmail("alice@example.com");
        u.setPassword("hashed-password");
        u.setStatus(UserStatus.ACTIVE);
        u.setOauthProviders(new HashSet<>(Set.of("GOOGLE")));
        return u;
    }

    // ─── authorize: trailing-slash bug fix (the fix in this branch) ───────────

    @Test
    void authorize_defaultRedirectUri_stripsTrailingSlashFromBackendUrl() {
        when(googleOAuthService.getDefaultScope()).thenReturn("openid email profile");
        when(googleOAuthService.getAuthorizationUrl(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> "https://accounts.google.com/o/oauth2/v2/auth?redirect_uri=" + inv.getArgument(0));
        doNothing().when(valueOps).set(anyString(), anyString(), any(Long.class), any(TimeUnit.class));

        OAuthAuthorizeResponse response = oauthService.authorize(Provider.GOOGLE, null, null);

        // The redirect URI must NOT have double slashes
        assertThat(response.getAuthorizationUrl()).doesNotContain("//auth/oauth");
        // Correct form: single slash between base and path
        verify(googleOAuthService).getAuthorizationUrl(
                eq("http://localhost:9085/auth/oauth/callback/google"),
                anyString(),
                anyString());
    }

    @Test
    void authorize_backendUrlWithNoTrailingSlash_buildsCorrectCallbackUrl() {
        ReflectionTestUtils.setField(oauthService, "backendBaseUrl", "http://localhost:9085");
        when(googleOAuthService.getDefaultScope()).thenReturn("openid email profile");
        when(googleOAuthService.getAuthorizationUrl(anyString(), anyString(), anyString()))
                .thenReturn("https://accounts.google.com/auth");
        doNothing().when(valueOps).set(anyString(), anyString(), any(Long.class), any(TimeUnit.class));

        oauthService.authorize(Provider.GOOGLE, null, null);

        verify(googleOAuthService).getAuthorizationUrl(
                eq("http://localhost:9085/auth/oauth/callback/google"),
                anyString(),
                anyString());
    }

    @Test
    void authorize_withExplicitRedirectUri_doesNotOverride() {
        when(googleOAuthService.getDefaultScope()).thenReturn("openid email profile");
        when(googleOAuthService.getAuthorizationUrl(anyString(), anyString(), anyString()))
                .thenReturn("https://accounts.google.com/auth");
        doNothing().when(valueOps).set(anyString(), anyString(), any(Long.class), any(TimeUnit.class));

        OAuthAuthorizeRequest req = OAuthAuthorizeRequest.builder()
                .redirectUri("https://myapp.example.com/callback")
                .build();

        oauthService.authorize(Provider.GOOGLE, req, null);

        verify(googleOAuthService).getAuthorizationUrl(
                eq("https://myapp.example.com/callback"),
                anyString(),
                anyString());
    }

    @Test
    void authorize_GITHUB_delegatesToGitHubService() {
        when(gitHubOAuthService.getDefaultScope()).thenReturn("read:user user:email");
        when(gitHubOAuthService.getAuthorizationUrl(anyString(), anyString(), anyString()))
                .thenReturn("https://github.com/login/oauth/authorize");
        doNothing().when(valueOps).set(anyString(), anyString(), any(Long.class), any(TimeUnit.class));

        OAuthAuthorizeResponse response = oauthService.authorize(Provider.GITHUB, null, null);

        assertThat(response.getProvider()).isEqualTo("GITHUB");
        verify(gitHubOAuthService).getAuthorizationUrl(anyString(), anyString(), anyString());
    }

    @Test
    void authorize_generatesStateAndExpiresIn() {
        when(googleOAuthService.getDefaultScope()).thenReturn("openid email profile");
        when(googleOAuthService.getAuthorizationUrl(anyString(), anyString(), anyString()))
                .thenReturn("https://accounts.google.com/auth");
        doNothing().when(valueOps).set(anyString(), anyString(), any(Long.class), any(TimeUnit.class));

        OAuthAuthorizeResponse response = oauthService.authorize(Provider.GOOGLE, null, null);

        assertThat(response.getState()).isNotBlank();
        assertThat(response.getExpiresIn()).isEqualTo(600L); // 10 minutes in seconds
    }

    // ─── getLinkedAccounts ────────────────────────────────────────────────────

    @Test
    void getLinkedAccounts_returnsProviders_andPasswordStatus() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(activeUser()));
        when(oauthProviderService.getLinkedProviders("user-id-1")).thenReturn(
                List.of(LinkedProviderResponse.builder().provider("GOOGLE").build()));

        LinkedAccountsResponse result = oauthService.getLinkedAccounts("alice");

        assertThat(result.getLinkedProviders()).hasSize(1);
        assertThat(result.getHasPassword()).isTrue();
        assertThat(result.getCanUnlinkAll()).isTrue(); // has password
    }

    @Test
    void getLinkedAccounts_noPassword_canUnlinkAll_falseWhenSingleProvider() {
        User noPasswordUser = activeUser();
        noPasswordUser.setPassword(null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(noPasswordUser));
        when(oauthProviderService.getLinkedProviders("user-id-1")).thenReturn(
                List.of(LinkedProviderResponse.builder().provider("GOOGLE").build()));

        LinkedAccountsResponse result = oauthService.getLinkedAccounts("alice");

        assertThat(result.getHasPassword()).isFalse();
        assertThat(result.getCanUnlinkAll()).isFalse(); // single provider, no password
    }

    @Test
    void getLinkedAccounts_throwsOAuthProviderException_whenUserNotFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> oauthService.getLinkedAccounts("ghost"))
                .isInstanceOf(OAuthProviderException.class);
    }

    // ─── unlinkProvider ───────────────────────────────────────────────────────

    @Test
    void unlinkProvider_removesProviderAndPublishesEvent() {
        User user = activeUser();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        OAuthProvider linkedProvider = OAuthProvider.builder()
                .provider(Provider.GOOGLE).userId("user-id-1").email("alice@gmail.com").build();
        when(oauthProviderService.findByUserId("user-id-1")).thenReturn(List.of(linkedProvider));
        doNothing().when(oauthProviderService).unlinkProvider(anyString(), any(Provider.class));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(oauthEventPublisher).publishAccountUnlinkedEvent(anyString(), anyString(), anyString(), any());

        oauthService.unlinkProvider("alice", Provider.GOOGLE);

        verify(oauthProviderService).unlinkProvider("user-id-1", Provider.GOOGLE);
        verify(oauthEventPublisher).publishAccountUnlinkedEvent(
                eq("user-id-1"), eq("alice"), eq("alice@example.com"), eq(Provider.GOOGLE));
    }

    @Test
    void unlinkProvider_throwsOAuthProviderException_whenUserNotFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> oauthService.unlinkProvider("ghost", Provider.GOOGLE))
                .isInstanceOf(OAuthProviderException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void unlinkProvider_throwsOAuthProviderException_whenProviderNotLinked() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(activeUser()));
        when(oauthProviderService.findByUserId("user-id-1")).thenReturn(List.of()); // no GITHUB linked

        assertThatThrownBy(() -> oauthService.unlinkProvider("alice", Provider.GITHUB))
                .isInstanceOf(OAuthProviderException.class)
                .hasMessageContaining("Provider not linked");
    }

    // ─── getAuthStatus ────────────────────────────────────────────────────────

    @Test
    void getAuthStatus_returnsStatusWithCorrectMethodCounts() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(activeUser()));
        when(oauthProviderService.getLinkedProviders("user-id-1")).thenReturn(
                List.of(LinkedProviderResponse.builder().provider("GOOGLE").build()));

        AuthStatusResponse status = oauthService.getAuthStatus("alice");

        assertThat(status.isHasPassword()).isTrue();
        assertThat(status.isCanUnlinkOAuth()).isTrue();
        assertThat(status.getTotalAuthMethods()).isEqualTo(2); // password + GOOGLE
    }

    @Test
    void getAuthStatus_noPasswordOnlyOAuth_singleProvider_notCanUnlink() {
        User user = activeUser();
        user.setPassword(null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(oauthProviderService.getLinkedProviders("user-id-1")).thenReturn(
                List.of(LinkedProviderResponse.builder().provider("GOOGLE").build()));

        AuthStatusResponse status = oauthService.getAuthStatus("alice");

        assertThat(status.isHasPassword()).isFalse();
        assertThat(status.isCanUnlinkOAuth()).isFalse();
        assertThat(status.getMessage()).containsIgnoringCase("set a password");
    }

    // ─── handleCallback: invalid state ────────────────────────────────────────

    @Test
    void handleCallback_invalidState_throwsInvalidOAuthStateException() {
        String state = "GOOGLE:invalid-uuid:LOGIN::test";
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("oauth:state:" + state)).thenReturn(null);

        assertThatThrownBy(() -> oauthService.handleCallback(Provider.GOOGLE, "code", state, null, httpRequest))
                .isInstanceOf(InvalidOAuthStateException.class)
                .hasMessageContaining("Invalid or expired OAuth state");
    }

    @Test
    void handleCallback_tokenExchangeFails_propagatesOAuthProviderException() {
        String state = "GOOGLE:tok-exchange:LOGIN::test";
        String stateKey = "oauth:state:" + state;
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(stateKey)).thenReturn("http://localhost:9085/callback");
        // redisTemplate.delete returns null by default (not used by service)
        when(googleOAuthService.handleCallback(anyString(), anyString()))
                .thenThrow(new OAuthProviderException("token exchange failed"));

        assertThatThrownBy(() -> oauthService.handleCallback(Provider.GOOGLE, "code", state, null, httpRequest))
                .isInstanceOf(OAuthProviderException.class)
                .hasMessageContaining("token exchange failed");
    }

    @Test
    void handleCallback_loginMode_existingUserFoundByEmail_returnsTokens() {
        String state = "GOOGLE:login-email:LOGIN::client";
        String stateKey = "oauth:state:" + state;
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(stateKey)).thenReturn("http://localhost:9085/callback");

        OAuthCallbackResponse.UserInfo userInfo = OAuthCallbackResponse.UserInfo.builder()
                .providerId("g-user-999").email("alice@gmail.com").name("Alice G")
                .emailVerified(true).build();
        OAuthCallbackResponse callbackResp = OAuthCallbackResponse.builder()
                .token("google-access-tok").userInfo(userInfo).build();
        when(googleOAuthService.handleCallback(anyString(), anyString())).thenReturn(callbackResp);

        // findOrCreateUser: not found by providerId, found by email
        when(oauthProviderService.findByProviderAndProviderId(eq(Provider.GOOGLE), eq("g-user-999")))
                .thenReturn(Optional.empty());
        User existingUser = activeUser();
        when(userRepository.findByEmail("alice@gmail.com")).thenReturn(Optional.of(existingUser));

        // saveOAuthProvider (second call to findByProviderAndProviderId) → empty → create new
        // already stubbed above to return empty

        when(userRepository.save(any())).thenReturn(existingUser);
        when(jwtService.generateAccessToken(any())).thenReturn("access-token");
        when(jwtService.newJti()).thenReturn("jti-1");
        when(jwtService.generateRefreshTokenWithJti(any(), anyString())).thenReturn("refresh-token");
        when(jwtService.getAccessExpMs()).thenReturn(3_600_000L);
        when(jwtService.getRefreshExpMs()).thenReturn(604_800_000L);
        doNothing().when(refreshTokenService).saveJti(anyString(), anyString(), anyString());
        when(sessionService.createSession(anyString(), anyString(), any(), any(), any(), any())).thenReturn(null);
        doNothing().when(oauthEventPublisher).publishLoginEvent(any(), any(), any(), any());

        OAuthCallbackResponse result = oauthService.handleCallback(Provider.GOOGLE, "code", state, null, httpRequest);

        assertThat(result.getToken()).isEqualTo("access-token");
        assertThat(result.getTokenType()).isEqualTo("Bearer");
    }

    @Test
    void handleCallback_linkMode_alreadyLinkedToDifferentUser_throwsOAuthProviderException() {
        String state = "GOOGLE:link-conflict:LINK:user-id-1:client";
        String stateKey = "oauth:state:" + state;
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(stateKey)).thenReturn("http://localhost:9085/callback");

        OAuthCallbackResponse.UserInfo userInfo = OAuthCallbackResponse.UserInfo.builder()
                .providerId("g-other-user").email("other@gmail.com").name("Other")
                .emailVerified(true).build();
        OAuthCallbackResponse callbackResp = OAuthCallbackResponse.builder()
                .token("google-tok").userInfo(userInfo).build();
        when(googleOAuthService.handleCallback(anyString(), anyString())).thenReturn(callbackResp);

        // Provider already linked to a DIFFERENT user
        OAuthProvider conflictingProvider = OAuthProvider.builder()
                .userId("other-user-id").provider(Provider.GOOGLE).build();
        when(oauthProviderService.findByProviderAndProviderId(eq(Provider.GOOGLE), eq("g-other-user")))
                .thenReturn(Optional.of(conflictingProvider));
        // findById for LINK mode target user
        when(userRepository.findById("user-id-1")).thenReturn(Optional.of(activeUser()));

        assertThatThrownBy(() -> oauthService.handleCallback(Provider.GOOGLE, "code", state, null, httpRequest))
                .isInstanceOf(OAuthProviderException.class)
                .hasMessageContaining("already linked to another user");
    }

    // ─── linkProvider (deprecated path) ──────────────────────────────────────

    @Test
    void linkProvider_userNotFound_throwsOAuthProviderException() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        OAuthLinkRequest req = new OAuthLinkRequest("auth-code", "http://localhost:3000/callback");

        assertThatThrownBy(() -> oauthService.linkProvider("ghost", Provider.GOOGLE, req))
                .isInstanceOf(OAuthProviderException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void linkProvider_alreadyLinkedToDifferentUser_throwsOAuthProviderException() {
        User user = activeUser();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        OAuthCallbackResponse.UserInfo userInfo = OAuthCallbackResponse.UserInfo.builder()
                .providerId("g-conflict-id").email("alice@gmail.com").name("Alice")
                .emailVerified(true).build();
        OAuthCallbackResponse callbackResp = OAuthCallbackResponse.builder()
                .token("google-tok").userInfo(userInfo).build();
        when(googleOAuthService.handleCallback(anyString(), anyString())).thenReturn(callbackResp);

        OAuthProvider conflictProvider = OAuthProvider.builder()
                .userId("different-user-id").provider(Provider.GOOGLE).build();
        when(oauthProviderService.findByProviderAndProviderId(eq(Provider.GOOGLE), eq("g-conflict-id")))
                .thenReturn(Optional.of(conflictProvider));

        OAuthLinkRequest req = new OAuthLinkRequest("auth-code", "http://localhost:3000/callback");

        assertThatThrownBy(() -> oauthService.linkProvider("alice", Provider.GOOGLE, req))
                .isInstanceOf(OAuthProviderException.class)
                .hasMessageContaining("already linked to another user");
    }

    @Test
    void linkProvider_happyPath_returnsSuccessResponse() {
        User user = activeUser();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        OAuthCallbackResponse.UserInfo userInfo = OAuthCallbackResponse.UserInfo.builder()
                .providerId("g-new-id").email("alice@gmail.com").name("Alice")
                .emailVerified(true).build();
        OAuthCallbackResponse callbackResp = OAuthCallbackResponse.builder()
                .token("google-tok").userInfo(userInfo).build();
        when(googleOAuthService.handleCallback(anyString(), anyString())).thenReturn(callbackResp);
        when(oauthProviderService.findByProviderAndProviderId(eq(Provider.GOOGLE), eq("g-new-id")))
                .thenReturn(Optional.empty());
        when(userRepository.save(any())).thenReturn(user);
        doNothing().when(oauthEventPublisher).publishAccountLinkedEvent(any(User.class), any(OAuthProvider.class));

        OAuthLinkRequest req = new OAuthLinkRequest("auth-code", "http://localhost:3000/callback");

        OAuthLinkResponse result = oauthService.linkProvider("alice", Provider.GOOGLE, req);

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getProvider()).isEqualTo("GOOGLE");
        assertThat(result.getEmail()).isEqualTo("alice@gmail.com");
        verify(oauthProviderService).linkProvider(eq("user-id-1"), any(OAuthProvider.class));
    }

    // ─── unlinkProvider: last auth method ─────────────────────────────────────

    @Test
    void unlinkProvider_lastAuthMethod_throwsCannotUnlinkLastAuthMethodException() {
        User user = activeUser();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        OAuthProvider linkedProvider = OAuthProvider.builder()
                .provider(Provider.GOOGLE).userId("user-id-1").email("alice@gmail.com").build();
        when(oauthProviderService.findByUserId("user-id-1")).thenReturn(List.of(linkedProvider));
        doThrow(new CannotUnlinkLastAuthMethodException("Cannot unlink last auth method"))
                .when(oauthProviderService).unlinkProvider(anyString(), any(Provider.class));

        assertThatThrownBy(() -> oauthService.unlinkProvider("alice", Provider.GOOGLE))
                .isInstanceOf(CannotUnlinkLastAuthMethodException.class)
                .hasMessageContaining("Cannot unlink last auth method");
    }

    // ─── handleCallback: LOGIN mode — brand-new user creation ─────────────────

    @Test
    void handleCallback_loginMode_newUserCreated_returnsTokens() {
        String state = "GOOGLE:new-user:LOGIN::client";
        String stateKey = "oauth:state:" + state;
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(stateKey)).thenReturn("http://localhost:9085/callback");

        OAuthCallbackResponse.UserInfo userInfo = OAuthCallbackResponse.UserInfo.builder()
                .providerId("g-brand-new").email("brand@new.com").name("Brand New")
                .emailVerified(true).build();
        OAuthCallbackResponse callbackResp = OAuthCallbackResponse.builder()
                .token("google-tok").userInfo(userInfo).build();
        when(googleOAuthService.handleCallback(anyString(), anyString())).thenReturn(callbackResp);

        // Not found by providerId
        when(oauthProviderService.findByProviderAndProviderId(eq(Provider.GOOGLE), eq("g-brand-new")))
                .thenReturn(Optional.empty());
        // Not found by email either — triggers user creation
        when(userRepository.findByEmail("brand@new.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername(anyString())).thenReturn(false); // no username collision
        when(encoder.encode(anyString())).thenReturn("hashed-pw");

        User newUser = new User();
        newUser.setId("new-user-id");
        newUser.setUsername("brandnew");
        newUser.setEmail("brand@new.com");
        newUser.setPassword("hashed-pw");
        newUser.setRoles(new java.util.HashSet<>(Set.of("CANDIDATE")));
        when(userRepository.save(any())).thenReturn(newUser);

        when(jwtService.generateAccessToken(any())).thenReturn("access-token");
        when(jwtService.newJti()).thenReturn("jti-new");
        when(jwtService.generateRefreshTokenWithJti(any(), anyString())).thenReturn("refresh-token");
        when(jwtService.getAccessExpMs()).thenReturn(3_600_000L);
        when(jwtService.getRefreshExpMs()).thenReturn(604_800_000L);
        doNothing().when(refreshTokenService).saveJti(anyString(), anyString(), anyString());
        when(sessionService.createSession(anyString(), anyString(), any(), any(), any(), any())).thenReturn(null);
        doNothing().when(oauthEventPublisher).publishLoginEvent(any(), any(), any(), any());
        when(oauthProviderService.saveProvider(any())).thenReturn(null);
        doNothing().when(userRegistrationProducer).publishUserRegistrationEvent(any());

        OAuthCallbackResponse result = oauthService.handleCallback(
                Provider.GOOGLE, "code", state, null, httpRequest);

        assertThat(result.getToken()).isEqualTo("access-token");
        assertThat(result.getTokenType()).isEqualTo("Bearer");
        // Verify user was saved (new user creation)
        verify(userRepository, org.mockito.Mockito.atLeastOnce()).save(any());
    }

    // ─── handleCallback: LINK mode — success ──────────────────────────────────

    @Test
    void handleCallback_linkMode_success_returnsLinkSuccessToken() {
        String state = "GOOGLE:link-success:LINK:user-id-1:client";
        String stateKey = "oauth:state:" + state;
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(stateKey)).thenReturn("http://localhost:9085/callback");

        OAuthCallbackResponse.UserInfo userInfo = OAuthCallbackResponse.UserInfo.builder()
                .providerId("g-link-new").email("alice@gmail.com").name("Alice")
                .emailVerified(true).build();
        OAuthCallbackResponse callbackResp = OAuthCallbackResponse.builder()
                .token("google-tok").userInfo(userInfo).build();
        when(googleOAuthService.handleCallback(anyString(), anyString())).thenReturn(callbackResp);

        User user = activeUser();
        when(userRepository.findById("user-id-1")).thenReturn(Optional.of(user));
        // Not linked to another user — OK to link
        when(oauthProviderService.findByProviderAndProviderId(eq(Provider.GOOGLE), eq("g-link-new")))
                .thenReturn(Optional.empty());
        when(userRepository.save(any())).thenReturn(user);
        when(oauthProviderService.linkProvider(anyString(), any(OAuthProvider.class))).thenReturn(null);
        doNothing().when(oauthEventPublisher).publishAccountLinkedEvent(any(User.class), any(OAuthProvider.class));

        OAuthCallbackResponse result = oauthService.handleCallback(
                Provider.GOOGLE, "code", state, null, httpRequest);

        assertThat(result.getTokenType()).isEqualTo("LINK_SUCCESS");
    }

    // ─── authorize: LINK mode (userId present) ────────────────────────────────

    @Test
    void authorize_withUserId_generatesLinkModeState() {
        when(googleOAuthService.getDefaultScope()).thenReturn("openid email profile");
        when(googleOAuthService.getAuthorizationUrl(anyString(), anyString(), anyString()))
                .thenReturn("https://accounts.google.com/auth");
        doNothing().when(valueOps).set(anyString(), anyString(), any(Long.class), any(java.util.concurrent.TimeUnit.class));

        OAuthAuthorizeResponse response = oauthService.authorize(Provider.GOOGLE, null, "user-id-123");

        // State should contain "LINK" mode
        assertThat(response.getState()).contains("LINK");
        assertThat(response.getProvider()).isEqualTo("GOOGLE");
    }

    // ─── authorize: custom scope ──────────────────────────────────────────────

    @Test
    void authorize_withCustomScope_usesCustomScope() {
        when(googleOAuthService.getAuthorizationUrl(anyString(), anyString(), eq("openid")))
                .thenReturn("https://accounts.google.com/auth");
        doNothing().when(valueOps).set(anyString(), anyString(), any(Long.class), any(java.util.concurrent.TimeUnit.class));

        OAuthAuthorizeRequest req = OAuthAuthorizeRequest.builder()
                .scope(List.of("openid"))
                .build();

        oauthService.authorize(Provider.GOOGLE, req, null);

        verify(googleOAuthService).getAuthorizationUrl(anyString(), anyString(), eq("openid"));
    }

    // ─── getAuthStatus: no-password + multiple-providers ─────────────────────

    @Test
    void getAuthStatus_noPassword_multipleProviders_canUnlink() {
        User user = activeUser();
        user.setPassword(null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(oauthProviderService.getLinkedProviders("user-id-1")).thenReturn(List.of(
                LinkedProviderResponse.builder().provider("GOOGLE").build(),
                LinkedProviderResponse.builder().provider("GITHUB").build()));

        org.workfitai.authservice.dto.response.AuthStatusResponse status =
                oauthService.getAuthStatus("alice");

        assertThat(status.isHasPassword()).isFalse();
        assertThat(status.isCanUnlinkOAuth()).isTrue(); // >1 provider
        assertThat(status.getMessage()).containsIgnoringCase("unlink");
    }

    @Test
    void getAuthStatus_hasPassword_noOAuth_returnsTraditionalLoginMessage() {
        User user = activeUser();
        user.setOauthProviders(new java.util.HashSet<>());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(oauthProviderService.getLinkedProviders("user-id-1")).thenReturn(List.of());

        org.workfitai.authservice.dto.response.AuthStatusResponse status =
                oauthService.getAuthStatus("alice");

        assertThat(status.isHasPassword()).isTrue();
        assertThat(status.getTotalAuthMethods()).isEqualTo(1);
        assertThat(status.getMessage()).containsIgnoringCase("traditional login");
    }

    @Test
    void getAuthStatus_userNotFound_throwsNotFoundException() {
        when(userRepository.findByUsername("ghost"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> oauthService.getAuthStatus("ghost"))
                .isInstanceOf(org.workfitai.authservice.exception.NotFoundException.class);
    }
}
