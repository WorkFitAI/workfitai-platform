package org.workfitai.authservice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import io.jsonwebtoken.JwtException;
import org.workfitai.authservice.client.UserServiceClient;
import org.workfitai.authservice.document.TwoFactorAuth;
import org.workfitai.authservice.dto.request.LoginRequest;
import org.workfitai.authservice.dto.request.HRProfileRequest;
import org.workfitai.authservice.dto.request.PendingRegistration;
import org.workfitai.authservice.dto.request.RegisterRequest;
import org.workfitai.authservice.dto.request.VerifyOtpRequest;
import org.workfitai.authservice.dto.request.Verify2FALoginRequest;
import org.workfitai.authservice.dto.response.IssuedTokens;
import org.workfitai.authservice.dto.response.MeResponse;
import org.workfitai.authservice.dto.response.Partial2FALoginResponse;
import org.workfitai.authservice.enums.UserRole;
import org.workfitai.authservice.enums.UserStatus;
import org.workfitai.authservice.messaging.NotificationProducer;
import org.workfitai.authservice.messaging.UserRegistrationProducer;
import org.workfitai.authservice.model.User;
import org.workfitai.authservice.repository.TwoFactorAuthRepository;
import org.workfitai.authservice.repository.UserRepository;
import org.workfitai.authservice.security.JwtService;
import org.workfitai.authservice.service.OtpService;
import org.workfitai.authservice.service.RefreshTokenService;
import org.workfitai.authservice.service.SessionService;
import org.workfitai.authservice.service.TwoFactorAuthService;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.workfitai.authservice.dto.request.CompanyRegisterRequest;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock UserRepository users;
    @Mock BCryptPasswordEncoder encoder;
    @Mock AuthenticationManager authManager;
    @Mock JwtService jwt;
    @Mock RefreshTokenService refreshStore;
    @Mock UserRegistrationProducer userRegistrationProducer;
    @Mock OtpService otpService;
    @Mock NotificationProducer notificationProducer;
    @Mock UserServiceClient userServiceClient;
    @Mock SessionService sessionService;
    @Mock TwoFactorAuthRepository twoFactorAuthRepository;
    @Mock TwoFactorAuthService twoFactorAuthService;
    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock HttpServletRequest httpRequest;
    @Mock Authentication authentication;

    @InjectMocks AuthServiceImpl authService;

    private User activeUser() {
        return User.builder()
                .id("user-id-1")
                .username("alice")
                .email("alice@example.com")
                .password("hashed")
                .roles(Set.of("CANDIDATE"))
                .status(UserStatus.ACTIVE)
                .isBlocked(false)
                .createdAt(Instant.now())
                .build();
    }

    private org.springframework.security.core.userdetails.UserDetails springUserDetails() {
        return org.springframework.security.core.userdetails.User
                .withUsername("alice")
                .password("hashed")
                .authorities(new SimpleGrantedAuthority("CANDIDATE"))
                .build();
    }

    private LoginRequest loginRequest(String usernameOrEmail, String password) {
        LoginRequest req = new LoginRequest();
        req.setUsernameOrEmail(usernameOrEmail);
        req.setPassword(password);
        return req;
    }

    private void stubSuccessfulAuth() {
        when(authentication.getPrincipal()).thenReturn(springUserDetails());
        when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(users.findByUsername("alice")).thenReturn(Optional.of(activeUser()));
        when(twoFactorAuthRepository.findByUserId("user-id-1")).thenReturn(Optional.empty());
        when(jwt.generateAccessToken(any())).thenReturn("access-token");
        when(jwt.newJti()).thenReturn("jti-1");
        when(jwt.generateRefreshTokenWithJti(any(), anyString())).thenReturn("refresh-token");
        when(jwt.getAccessExpMs()).thenReturn(3_600_000L);
        doNothing().when(refreshStore).saveJti(anyString(), anyString(), anyString());
        when(sessionService.createSession(anyString(), anyString(), any(), any(), any(), any())).thenReturn(null);
    }

    // ─── login: happy path ────────────────────────────────────────────────────

    @Test
    void login_happyPath_noTwoFA_returnsIssuedTokens() {
        stubSuccessfulAuth();

        Object result = authService.login(loginRequest("alice", "pass"), "desktop", httpRequest);

        assertThat(result).isInstanceOf(IssuedTokens.class);
        IssuedTokens tokens = (IssuedTokens) result;
        assertThat(tokens.getAccessToken()).isEqualTo("access-token");
        assertThat(tokens.getRefreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void login_happyPath_generatesAndStoresRefreshJti() {
        stubSuccessfulAuth();

        authService.login(loginRequest("alice", "pass"), "desktop", httpRequest);

        verify(refreshStore).saveJti("user-id-1", "desktop", "jti-1");
    }

    // ─── login: blocked user ──────────────────────────────────────────────────

    @Test
    void login_blockedUser_throwsForbidden() {
        when(authentication.getPrincipal()).thenReturn(springUserDetails());
        when(authManager.authenticate(any())).thenReturn(authentication);
        User blocked = User.builder().id("user-id-1").username("alice").email("alice@example.com")
                .password("hashed").roles(Set.of("CANDIDATE")).status(UserStatus.ACTIVE).isBlocked(true).build();
        when(users.findByUsername("alice")).thenReturn(Optional.of(blocked));

        assertThatThrownBy(() -> authService.login(loginRequest("alice", "pass"), null, httpRequest))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    // ─── login: pending user ──────────────────────────────────────────────────

    @Test
    void login_pendingUser_throwsForbidden_withVerifyEmailMessage() {
        when(authentication.getPrincipal()).thenReturn(springUserDetails());
        when(authManager.authenticate(any())).thenReturn(authentication);
        User pending = User.builder().id("user-id-1").username("alice").email("alice@example.com")
                .password("hashed").roles(Set.of("CANDIDATE")).status(UserStatus.PENDING).isBlocked(false).build();
        when(users.findByUsername("alice")).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> authService.login(loginRequest("alice", "pass"), null, httpRequest))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("verify your email");
    }

    // ─── login: wait-approved user ────────────────────────────────────────────

    @Test
    void login_waitApprovedUser_throwsForbidden_withPendingApprovalMessage() {
        when(authentication.getPrincipal()).thenReturn(springUserDetails());
        when(authManager.authenticate(any())).thenReturn(authentication);
        User waitApproved = User.builder().id("user-id-1").username("alice").email("alice@example.com")
                .password("hashed").roles(Set.of("CANDIDATE")).status(UserStatus.WAIT_APPROVED).isBlocked(false).build();
        when(users.findByUsername("alice")).thenReturn(Optional.of(waitApproved));

        assertThatThrownBy(() -> authService.login(loginRequest("alice", "pass"), null, httpRequest))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("pending approval");
    }

    // ─── login: bad credentials ───────────────────────────────────────────────

    @Test
    void login_badCredentials_throwsUnauthorized() {
        when(authManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(loginRequest("alice", "wrong"), null, httpRequest))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    // ─── login: 2FA required ──────────────────────────────────────────────────

    @Test
    void login_twoFAEnabled_EMAIL_returnsPartial2FAResponse() {
        when(authentication.getPrincipal()).thenReturn(springUserDetails());
        when(authManager.authenticate(any())).thenReturn(authentication);
        User user = activeUser();
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));

        TwoFactorAuth tfaCfg = TwoFactorAuth.builder()
                .userId("user-id-1").method("EMAIL").enabled(true).build();
        when(twoFactorAuthRepository.findByUserId("user-id-1")).thenReturn(Optional.of(tfaCfg));

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        doNothing().when(valueOps).set(anyString(), anyString(), any(Long.class), any(TimeUnit.class));
        when(twoFactorAuthService.generateEmailCode("user-id-1")).thenReturn("123456");
        doNothing().when(notificationProducer).send(any());

        Object result = authService.login(loginRequest("alice", "pass"), "desktop", httpRequest);

        assertThat(result).isInstanceOf(Partial2FALoginResponse.class);
        Partial2FALoginResponse partial = (Partial2FALoginResponse) result;
        assertThat(partial.getMethod()).isEqualTo("EMAIL");
        assertThat(partial.getRequire2FA()).isTrue();
        assertThat(partial.getTempToken()).isNotBlank();
    }

    @Test
    void login_twoFAEnabled_TOTP_returnsPartialResponseWithoutEmailSend() {
        when(authentication.getPrincipal()).thenReturn(springUserDetails());
        when(authManager.authenticate(any())).thenReturn(authentication);
        when(users.findByUsername("alice")).thenReturn(Optional.of(activeUser()));

        TwoFactorAuth tfaCfg = TwoFactorAuth.builder()
                .userId("user-id-1").method("TOTP").enabled(true).build();
        when(twoFactorAuthRepository.findByUserId("user-id-1")).thenReturn(Optional.of(tfaCfg));

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        doNothing().when(valueOps).set(anyString(), anyString(), any(Long.class), any(TimeUnit.class));

        Object result = authService.login(loginRequest("alice", "pass"), null, httpRequest);

        assertThat(result).isInstanceOf(Partial2FALoginResponse.class);
        assertThat(((Partial2FALoginResponse) result).getMethod()).isEqualTo("TOTP");
        // TOTP does not send email
        verify(notificationProducer, never()).send(any());
    }

    // ─── verify2FALogin ───────────────────────────────────────────────────────

    @Test
    void verify2FALogin_validToken_validCode_returnsIssuedTokens() {
        String tempToken = "temp-token-abc";
        String userData = "user-id-1:alice:desktop";

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("temp:login:" + tempToken)).thenReturn(userData);
        when(users.findById("user-id-1")).thenReturn(Optional.of(activeUser()));

        TwoFactorAuth tfaCfg = TwoFactorAuth.builder()
                .userId("user-id-1").method("TOTP").enabled(true).build();
        when(twoFactorAuthRepository.findByUserId("user-id-1")).thenReturn(Optional.of(tfaCfg));
        when(twoFactorAuthService.verify2FACode("user-id-1", "123456", "TOTP")).thenReturn(true);

        when(jwt.generateAccessToken(any())).thenReturn("access-token");
        when(jwt.newJti()).thenReturn("jti-2fa");
        when(jwt.generateRefreshTokenWithJti(any(), anyString())).thenReturn("refresh-token");
        when(jwt.getAccessExpMs()).thenReturn(3_600_000L);
        doNothing().when(refreshStore).saveJti(anyString(), anyString(), anyString());
        when(sessionService.createSession(anyString(), anyString(), any(), any(), any(), any())).thenReturn(null);

        Verify2FALoginRequest req = new Verify2FALoginRequest();
        req.setTempToken(tempToken);
        req.setCode("123456");
        req.setUseBackupCode(false);

        IssuedTokens result = authService.verify2FALogin(req, httpRequest);

        assertThat(result.getAccessToken()).isEqualTo("access-token");
        verify(redisTemplate).delete("temp:login:" + tempToken);
    }

    @Test
    void verify2FALogin_invalidTempToken_throwsUnauthorized() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("temp:login:bad-token")).thenReturn(null);

        Verify2FALoginRequest req = new Verify2FALoginRequest();
        req.setTempToken("bad-token");
        req.setCode("123456");
        req.setUseBackupCode(false);

        assertThatThrownBy(() -> authService.verify2FALogin(req, httpRequest))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void verify2FALogin_invalidCode_throwsUnauthorized() {
        String tempToken = "temp-token-xyz";
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("temp:login:" + tempToken)).thenReturn("user-id-1:alice:desktop");
        when(users.findById("user-id-1")).thenReturn(Optional.of(activeUser()));

        TwoFactorAuth tfaCfg = TwoFactorAuth.builder()
                .userId("user-id-1").method("TOTP").enabled(true).build();
        when(twoFactorAuthRepository.findByUserId("user-id-1")).thenReturn(Optional.of(tfaCfg));
        when(twoFactorAuthService.verify2FACode("user-id-1", "000000", "TOTP")).thenReturn(false);

        Verify2FALoginRequest req = new Verify2FALoginRequest();
        req.setTempToken(tempToken);
        req.setCode("000000");
        req.setUseBackupCode(false);

        assertThatThrownBy(() -> authService.verify2FALogin(req, httpRequest))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    // ─── logout ───────────────────────────────────────────────────────────────

    @Test
    void logout_deletesRefreshJtiForDevice() {
        when(users.findByUsername("alice")).thenReturn(Optional.of(activeUser()));
        doNothing().when(refreshStore).delete(anyString(), anyString());

        authService.logout("desktop", "alice");

        verify(refreshStore).delete("user-id-1", "desktop");
    }

    @Test
    void logout_normalizesNullDevice_toDefault() {
        when(users.findByUsername("alice")).thenReturn(Optional.of(activeUser()));
        doNothing().when(refreshStore).delete(anyString(), anyString());

        authService.logout(null, "alice");

        // null deviceId → normalised to "unknown" or DEFAULT_DEVICE constant
        verify(refreshStore).delete(eq("user-id-1"), anyString());
    }

    @Test
    void logout_throwsUnauthorized_whenUserNotFound() {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.logout("desktop", "ghost"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    // ─── getCurrentUser ───────────────────────────────────────────────────────

    @Test
    void getCurrentUser_returnsAuthenticatedResponse_whenUserExists() {
        when(users.findByUsername("alice")).thenReturn(Optional.of(activeUser()));

        MeResponse me = authService.getCurrentUser("alice");

        assertThat(me.isAuthenticated()).isTrue();
        assertThat(me.getUsername()).isEqualTo("alice");
    }

    @Test
    void getCurrentUser_returnsUnauthenticated_whenUsernameNull() {
        MeResponse me = authService.getCurrentUser(null);

        assertThat(me.isAuthenticated()).isFalse();
        verify(users, never()).findByUsername(anyString());
    }

    @Test
    void getCurrentUser_returnsUnauthenticated_whenUsernameBlank() {
        MeResponse me = authService.getCurrentUser("   ");

        assertThat(me.isAuthenticated()).isFalse();
    }

    @Test
    void getCurrentUser_returnsUnauthenticated_whenUserNotFoundInDb() {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());

        MeResponse me = authService.getCurrentUser("ghost");

        assertThat(me.isAuthenticated()).isFalse();
    }

    // ─── register: role validation ────────────────────────────────────────────

    @Test
    void register_adminRole_throwsForbidden() {
        org.workfitai.authservice.dto.request.RegisterRequest req =
                new org.workfitai.authservice.dto.request.RegisterRequest();
        req.setEmail("admin@example.com");
        req.setPassword("Pass@1234");
        req.setRole(UserRole.ADMIN);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void register_duplicateEmail_throwsBadRequest() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("alice@example.com");
        req.setPassword("Pass@1234");
        req.setRole(UserRole.CANDIDATE);

        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser()));

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("already in use");
    }

    @Test
    void register_hrRole_withoutHrProfile_throwsBadRequest() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("hr@example.com");
        req.setPassword("Pass@1234");
        req.setRole(UserRole.HR);
        req.setHrProfile(null);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("HR profile is required");
    }

    @Test
    void register_hrManagerRole_withoutCompany_throwsBadRequest() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("hrm@example.com");
        req.setPassword("Pass@1234");
        req.setRole(UserRole.HR_MANAGER);
        req.setHrProfile(new HRProfileRequest());
        req.setCompany(null);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("Company information is required");
    }

    @Test
    void register_pendingUser_resendOtp() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("alice@example.com");
        req.setPassword("Pass@1234");
        req.setRole(UserRole.CANDIDATE);
        req.setFullName("Alice Smith");

        User pendingUser = User.builder()
                .id("user-id-1").username("alice").email("alice@example.com")
                .password("old-hash").roles(Set.of("CANDIDATE")).status(UserStatus.PENDING).build();
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(pendingUser));
        when(encoder.encode(anyString())).thenReturn("new-hash");
        when(users.save(any())).thenReturn(pendingUser);
        when(otpService.generateOtp()).thenReturn("123456");
        doNothing().when(otpService).saveOtp(anyString(), anyString());
        doNothing().when(otpService).savePendingRegistration(anyString(), any());
        doNothing().when(notificationProducer).send(any());

        authService.register(req);

        verify(notificationProducer).send(any());
    }

    @Test
    void register_candidate_emailNotInUserService_savesUserAndSendsOtp() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("newuser@example.com");
        req.setPassword("Pass@1234");
        req.setRole(UserRole.CANDIDATE);
        req.setFullName("New User");

        when(users.findByEmail("newuser@example.com")).thenReturn(Optional.empty());
        when(userServiceClient.existsByEmail("newuser@example.com")).thenReturn(false);
        when(users.existsByUsername(anyString())).thenReturn(false);
        when(encoder.encode(anyString())).thenReturn("hashed-pw");
        when(otpService.generateOtp()).thenReturn("654321");
        doNothing().when(otpService).saveOtp(anyString(), anyString());
        doNothing().when(otpService).savePendingRegistration(anyString(), any());
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(notificationProducer).send(any());

        authService.register(req);

        verify(users).save(any(User.class));
        verify(notificationProducer).send(any());
    }

    // ─── register: user-service validation failure (non-fatal) ───────────────

    @Test
    void register_userServiceThrowsNonValidationException_continuesRegistration() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("newuser@example.com");
        req.setPassword("Pass@1234");
        req.setRole(UserRole.CANDIDATE);
        req.setFullName("New User");

        when(users.findByEmail("newuser@example.com")).thenReturn(Optional.empty());
        when(userServiceClient.existsByEmail("newuser@example.com"))
                .thenThrow(new RuntimeException("service unavailable"));
        when(users.existsByUsername(anyString())).thenReturn(false);
        when(encoder.encode(anyString())).thenReturn("hashed-pw");
        when(otpService.generateOtp()).thenReturn("654321");
        doNothing().when(otpService).saveOtp(anyString(), anyString());
        doNothing().when(otpService).savePendingRegistration(anyString(), any());
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(notificationProducer).send(any());

        authService.register(req);

        verify(users).save(any(User.class));
    }

    // ─── login: INACTIVE user reactivation paths ──────────────────────────────

    @Test
    void login_inactiveUser_reactivatableWithinWindow_updatesStatusAndCompletes() {
        when(authentication.getPrincipal()).thenReturn(springUserDetails());
        when(authManager.authenticate(any())).thenReturn(authentication);
        User inactiveUser = User.builder()
                .id("user-id-1").username("alice").email("alice@example.com")
                .password("hashed").roles(Set.of("CANDIDATE")).status(UserStatus.INACTIVE).isBlocked(false).build();
        when(users.findByUsername("alice")).thenReturn(Optional.of(inactiveUser));
        when(userServiceClient.checkAndReactivateAccount("alice")).thenReturn(true);
        when(users.save(any())).thenReturn(inactiveUser);
        when(twoFactorAuthRepository.findByUserId("user-id-1")).thenReturn(Optional.empty());
        when(jwt.generateAccessToken(any())).thenReturn("access-token");
        when(jwt.newJti()).thenReturn("jti-1");
        when(jwt.generateRefreshTokenWithJti(any(), anyString())).thenReturn("refresh-token");
        when(jwt.getAccessExpMs()).thenReturn(3_600_000L);
        doNothing().when(refreshStore).saveJti(anyString(), anyString(), anyString());
        when(sessionService.createSession(anyString(), anyString(), any(), any(), any(), any())).thenReturn(null);

        Object result = authService.login(loginRequest("alice", "pass"), "desktop", httpRequest);

        assertThat(result).isInstanceOf(IssuedTokens.class);
        verify(users).save(any(User.class));
    }

    @Test
    void login_inactiveUser_cannotReactivate_throwsForbidden() {
        when(authentication.getPrincipal()).thenReturn(springUserDetails());
        when(authManager.authenticate(any())).thenReturn(authentication);
        User inactiveUser = User.builder()
                .id("user-id-1").username("alice").email("alice@example.com")
                .password("hashed").roles(Set.of("CANDIDATE")).status(UserStatus.INACTIVE).isBlocked(false).build();
        when(users.findByUsername("alice")).thenReturn(Optional.of(inactiveUser));
        when(userServiceClient.checkAndReactivateAccount("alice")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginRequest("alice", "pass"), null, httpRequest))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN))
                .hasMessageContaining("30 days");
    }

    @Test
    void login_inactiveUser_reactivationServiceFails_throwsForbidden() {
        when(authentication.getPrincipal()).thenReturn(springUserDetails());
        when(authManager.authenticate(any())).thenReturn(authentication);
        User inactiveUser = User.builder()
                .id("user-id-1").username("alice").email("alice@example.com")
                .password("hashed").roles(Set.of("CANDIDATE")).status(UserStatus.INACTIVE).isBlocked(false).build();
        when(users.findByUsername("alice")).thenReturn(Optional.of(inactiveUser));
        when(userServiceClient.checkAndReactivateAccount("alice"))
                .thenThrow(new RuntimeException("downstream error"));

        assertThatThrownBy(() -> authService.login(loginRequest("alice", "pass"), null, httpRequest))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    // ─── refresh ─────────────────────────────────────────────────────────────

    @Test
    void refresh_validToken_returnsNewTokens() {
        when(jwt.extractUsername("valid-refresh")).thenReturn("alice");
        when(jwt.extractJti("valid-refresh")).thenReturn("jti-old");
        when(users.findByUsername("alice")).thenReturn(Optional.of(activeUser()));
        when(users.findById("user-id-1")).thenReturn(Optional.of(activeUser()));
        when(jwt.generateAccessToken(any())).thenReturn("new-access");
        when(jwt.newJti()).thenReturn("jti-new");
        when(jwt.generateRefreshTokenWithJti(any(), anyString())).thenReturn("new-refresh");
        when(jwt.getAccessExpMs()).thenReturn(3_600_000L);
        doNothing().when(refreshStore).saveJti(anyString(), anyString(), anyString());

        IssuedTokens result = authService.refresh("valid-refresh", "desktop");

        assertThat(result.getAccessToken()).isEqualTo("new-access");
        assertThat(result.getRefreshToken()).isEqualTo("new-refresh");
    }

    @Test
    void refresh_invalidJwt_throwsUnauthorized() {
        when(jwt.extractUsername("bad-token")).thenThrow(new JwtException("bad"));

        assertThatThrownBy(() -> authService.refresh("bad-token", "desktop"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void refresh_userNotFound_throwsUnauthorized() {
        when(jwt.extractUsername("valid-refresh")).thenReturn("ghost");
        when(jwt.extractJti("valid-refresh")).thenReturn("jti-1");
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("valid-refresh", "desktop"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    // ─── verifyOtp ────────────────────────────────────────────────────────────

    @Test
    void verifyOtp_invalidOtp_throwsBadRequest() {
        when(otpService.verifyOtp("user@example.com", "000000")).thenReturn(false);

        VerifyOtpRequest req = new VerifyOtpRequest();
        req.setEmail("user@example.com");
        req.setOtp("000000");

        assertThatThrownBy(() -> authService.verifyOtp(req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void verifyOtp_noPendingData_throwsNotFound() {
        when(otpService.verifyOtp("user@example.com", "123456")).thenReturn(true);
        when(otpService.getPendingRegistration("user@example.com")).thenReturn(null);

        VerifyOtpRequest req = new VerifyOtpRequest();
        req.setEmail("user@example.com");
        req.setOtp("123456");

        assertThatThrownBy(() -> authService.verifyOtp(req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void verifyOtp_userNotInPendingStatus_throwsBadRequest() {
        User activeUser = activeUser();
        when(otpService.verifyOtp("alice@example.com", "123456")).thenReturn(true);
        when(otpService.getPendingRegistration("alice@example.com"))
                .thenReturn(PendingRegistration.builder()
                        .email("alice@example.com").username("alice")
                        .role(UserRole.CANDIDATE).build());
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser));

        VerifyOtpRequest req = new VerifyOtpRequest();
        req.setEmail("alice@example.com");
        req.setOtp("123456");

        assertThatThrownBy(() -> authService.verifyOtp(req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("not in pending status");
    }

    @Test
    void verifyOtp_validCandidate_activatesUser() {
        User pendingUser = User.builder()
                .id("user-id-1").username("alice").email("alice@example.com")
                .password("hash").roles(Set.of("CANDIDATE")).status(UserStatus.PENDING).build();

        when(otpService.verifyOtp("alice@example.com", "123456")).thenReturn(true);
        when(otpService.getPendingRegistration("alice@example.com"))
                .thenReturn(PendingRegistration.builder()
                        .email("alice@example.com").username("alice")
                        .role(UserRole.CANDIDATE).build());
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(pendingUser));
        doNothing().when(userRegistrationProducer).publishUserRegistrationEvent(any());
        when(users.save(any())).thenReturn(pendingUser);
        doNothing().when(otpService).deletePendingRegistration(anyString());
        doNothing().when(notificationProducer).send(any());

        VerifyOtpRequest req = new VerifyOtpRequest();
        req.setEmail("alice@example.com");
        req.setOtp("123456");

        authService.verifyOtp(req);

        verify(users).save(any(User.class));
        verify(otpService).deletePendingRegistration("alice@example.com");
    }

    @Test
    void verifyOtp_syncFailure_throwsServiceUnavailable() {
        User pendingUser = User.builder()
                .id("user-id-1").username("alice").email("alice@example.com")
                .password("hash").roles(Set.of("CANDIDATE")).status(UserStatus.PENDING).build();

        when(otpService.verifyOtp("alice@example.com", "123456")).thenReturn(true);
        when(otpService.getPendingRegistration("alice@example.com"))
                .thenReturn(PendingRegistration.builder()
                        .email("alice@example.com").username("alice")
                        .role(UserRole.CANDIDATE).build());
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(pendingUser));
        org.mockito.Mockito.doThrow(new RuntimeException("Kafka down"))
                .when(userRegistrationProducer).publishUserRegistrationEvent(any());

        VerifyOtpRequest req = new VerifyOtpRequest();
        req.setEmail("alice@example.com");
        req.setOtp("123456");

        assertThatThrownBy(() -> authService.verifyOtp(req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    // ─── verify2FALogin: backup code path ────────────────────────────────────

    @Test
    void verify2FALogin_useBackupCode_valid_returnsIssuedTokens() {
        String tempToken = "temp-backup-token";
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("temp:login:" + tempToken)).thenReturn("user-id-1:alice:desktop");
        when(users.findById("user-id-1")).thenReturn(Optional.of(activeUser()));

        TwoFactorAuth tfaCfg = TwoFactorAuth.builder()
                .userId("user-id-1").method("EMAIL").enabled(true).build();
        when(twoFactorAuthRepository.findByUserId("user-id-1")).thenReturn(Optional.of(tfaCfg));
        when(twoFactorAuthService.verifyBackupCode(any(), eq("BACKUP01"))).thenReturn(true);

        when(jwt.generateAccessToken(any())).thenReturn("access-token");
        when(jwt.newJti()).thenReturn("jti-bk");
        when(jwt.generateRefreshTokenWithJti(any(), anyString())).thenReturn("refresh-token");
        when(jwt.getAccessExpMs()).thenReturn(3_600_000L);
        doNothing().when(refreshStore).saveJti(anyString(), anyString(), anyString());
        when(sessionService.createSession(anyString(), anyString(), any(), any(), any(), any())).thenReturn(null);

        Verify2FALoginRequest req = new Verify2FALoginRequest();
        req.setTempToken(tempToken);
        req.setCode("BACKUP01");
        req.setUseBackupCode(true);

        IssuedTokens result = authService.verify2FALogin(req, httpRequest);

        assertThat(result.getAccessToken()).isEqualTo("access-token");
    }

    @Test
    void verify2FALogin_useBackupCode_invalid_throwsUnauthorized() {
        String tempToken = "temp-bad-backup";
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("temp:login:" + tempToken)).thenReturn("user-id-1:alice:desktop");
        when(users.findById("user-id-1")).thenReturn(Optional.of(activeUser()));

        TwoFactorAuth tfaCfg = TwoFactorAuth.builder()
                .userId("user-id-1").method("EMAIL").enabled(true).build();
        when(twoFactorAuthRepository.findByUserId("user-id-1")).thenReturn(Optional.of(tfaCfg));
        when(twoFactorAuthService.verifyBackupCode(any(), eq("WRONG"))).thenReturn(false);

        Verify2FALoginRequest req = new Verify2FALoginRequest();
        req.setTempToken(tempToken);
        req.setCode("WRONG");
        req.setUseBackupCode(true);

        assertThatThrownBy(() -> authService.verify2FALogin(req, httpRequest))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED))
                .hasMessageContaining("backup code");
    }

    // ─── register: HR role checks ─────────────────────────────────────────────

    @Test
    void register_hrRole_nullHrManagerEmail_throwsBadRequest() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("hr@example.com");
        req.setPassword("Pass@1234");
        req.setRole(UserRole.HR);
        HRProfileRequest hrProfile = new HRProfileRequest();
        hrProfile.setHrManagerEmail(null);
        req.setHrProfile(hrProfile);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("HR Manager email is required");
    }

    @Test
    void register_hrRole_blankHrManagerEmail_throwsBadRequest() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("hr@example.com");
        req.setPassword("Pass@1234");
        req.setRole(UserRole.HR);
        HRProfileRequest hrProfile = new HRProfileRequest();
        hrProfile.setHrManagerEmail("   ");
        req.setHrProfile(hrProfile);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("HR Manager email is required");
    }

    @Test
    void register_hrRole_hrManagerNotFound_throwsNotFound() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("hr@example.com");
        req.setPassword("Pass@1234");
        req.setRole(UserRole.HR);
        HRProfileRequest hrProfile = new HRProfileRequest();
        hrProfile.setHrManagerEmail("manager@example.com");
        req.setHrProfile(hrProfile);

        when(users.findByEmailForCompanyNo("manager@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void register_hrRole_hrManagerHasNullCompanyNo_throwsBadRequest() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("hr@example.com");
        req.setPassword("Pass@1234");
        req.setRole(UserRole.HR);
        HRProfileRequest hrProfile = new HRProfileRequest();
        hrProfile.setHrManagerEmail("manager@example.com");
        req.setHrProfile(hrProfile);

        User hrManager = User.builder()
                .id("mgr-id").email("manager@example.com").companyNo(null).build();
        when(users.findByEmailForCompanyNo("manager@example.com")).thenReturn(Optional.of(hrManager));

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("company number");
    }

    @Test
    void register_hrManagerRole_existingHrManagerForSameCompany_throwsBadRequest() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("hrm@example.com");
        req.setPassword("Pass@1234");
        req.setRole(UserRole.HR_MANAGER);
        HRProfileRequest hrProfile = new HRProfileRequest();
        req.setHrProfile(hrProfile);
        CompanyRegisterRequest company = new CompanyRegisterRequest();
        company.setCompanyNo("COMPANY-TAX-123");
        req.setCompany(company);

        when(users.findByRolesContainingAndCompanyNo("HR_MANAGER", "COMPANY-TAX-123"))
                .thenReturn(List.of(User.builder().id("existing-hrm").build()));

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("A HR Manager already exists");
    }

    @Test
    void register_emailExistsInUserService_throwsBadRequest() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("newuser@example.com");
        req.setPassword("Pass@1234");
        req.setRole(UserRole.CANDIDATE);

        when(users.findByEmail("newuser@example.com")).thenReturn(Optional.empty());
        when(userServiceClient.existsByEmail("newuser@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("already registered");
    }

    @Test
    void register_phoneExistsInUserService_throwsBadRequest() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("newuser@example.com");
        req.setPassword("Pass@1234");
        req.setRole(UserRole.CANDIDATE);
        req.setPhoneNumber("0901234567");

        when(users.findByEmail("newuser@example.com")).thenReturn(Optional.empty());
        when(userServiceClient.existsByEmail("newuser@example.com")).thenReturn(false);
        when(userServiceClient.existsByPhoneNumber("0901234567")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("Phone number");
    }

    @Test
    void register_phoneServiceThrows_continuesRegistration() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("newuser2@example.com");
        req.setPassword("Pass@1234");
        req.setRole(UserRole.CANDIDATE);
        req.setFullName("New User");
        req.setPhoneNumber("0901234567");

        when(users.findByEmail("newuser2@example.com")).thenReturn(Optional.empty());
        when(userServiceClient.existsByEmail("newuser2@example.com")).thenReturn(false);
        when(userServiceClient.existsByPhoneNumber("0901234567"))
                .thenThrow(new RuntimeException("phone service unavailable"));
        when(users.existsByUsername(anyString())).thenReturn(false);
        when(userServiceClient.existsByUsername(anyString())).thenReturn(false);
        when(encoder.encode(anyString())).thenReturn("hashed-pw");
        when(otpService.generateOtp()).thenReturn("123456");
        doNothing().when(otpService).saveOtp(anyString(), anyString());
        doNothing().when(otpService).savePendingRegistration(anyString(), any());
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(notificationProducer).send(any());

        authService.register(req);

        verify(users).save(any(User.class));
    }

    @Test
    void register_pendingHrManager_resendOtp() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("hrm@example.com");
        req.setPassword("Pass@1234");
        req.setRole(UserRole.HR_MANAGER);
        req.setFullName("HR Manager");
        HRProfileRequest hrProfile = new HRProfileRequest();
        req.setHrProfile(hrProfile);
        CompanyRegisterRequest company = new CompanyRegisterRequest();
        company.setCompanyNo("COMP-123");
        company.setName("Test Company");
        req.setCompany(company);

        User pendingHrm = User.builder()
                .id("hrm-id").username("hrmanager").email("hrm@example.com")
                .password("old-hash").roles(Set.of("HR_MANAGER")).status(UserStatus.PENDING)
                .companyId(null).build();

        when(users.findByRolesContainingAndCompanyNo("HR_MANAGER", "COMP-123")).thenReturn(List.of());
        when(users.findByEmail("hrm@example.com")).thenReturn(Optional.of(pendingHrm));
        when(encoder.encode(anyString())).thenReturn("new-hash");
        when(users.save(any())).thenReturn(pendingHrm);
        when(otpService.generateOtp()).thenReturn("654321");
        doNothing().when(otpService).saveOtp(anyString(), anyString());
        doNothing().when(otpService).savePendingRegistration(anyString(), any());
        doNothing().when(notificationProducer).send(any());

        authService.register(req);

        verify(notificationProducer).send(any());
    }

    @Test
    void register_pendingHr_hrManagerFound_resendOtp() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("hr@example.com");
        req.setPassword("Pass@1234");
        req.setRole(UserRole.HR);
        req.setFullName("HR User");
        HRProfileRequest hrProfile = new HRProfileRequest();
        hrProfile.setHrManagerEmail("manager@example.com");
        req.setHrProfile(hrProfile);

        User hrManager = User.builder()
                .id("mgr-id").email("manager@example.com").companyNo("COMP-123").build();
        when(users.findByEmailForCompanyNo("manager@example.com"))
                .thenReturn(Optional.of(hrManager));

        User pendingHr = User.builder()
                .id("hr-id").username("hruser").email("hr@example.com")
                .password("old-hash").roles(Set.of("HR")).status(UserStatus.PENDING).build();
        when(users.findByEmail("hr@example.com")).thenReturn(Optional.of(pendingHr));
        when(encoder.encode(anyString())).thenReturn("new-hash");
        when(users.save(any())).thenReturn(pendingHr);
        when(otpService.generateOtp()).thenReturn("111111");
        doNothing().when(otpService).saveOtp(anyString(), anyString());
        doNothing().when(otpService).savePendingRegistration(anyString(), any());
        doNothing().when(notificationProducer).send(any());

        authService.register(req);

        verify(notificationProducer).send(any());
    }

    @Test
    void register_pendingHr_hrManagerNotFoundOnResend_throwsNotFound() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("hr@example.com");
        req.setPassword("Pass@1234");
        req.setRole(UserRole.HR);
        req.setFullName("HR User");
        HRProfileRequest hrProfile = new HRProfileRequest();
        hrProfile.setHrManagerEmail("manager@example.com");
        req.setHrProfile(hrProfile);

        User hrManager = User.builder()
                .id("mgr-id").email("manager@example.com").companyNo("COMP-123").build();
        // First call in register() validation passes; second call in resendOtpForPendingUser fails
        when(users.findByEmailForCompanyNo("manager@example.com"))
                .thenReturn(Optional.of(hrManager))
                .thenReturn(Optional.empty());

        User pendingHr = User.builder()
                .id("hr-id").username("hruser").email("hr@example.com")
                .password("old-hash").roles(Set.of("HR")).status(UserStatus.PENDING).build();
        when(users.findByEmail("hr@example.com")).thenReturn(Optional.of(pendingHr));
        when(encoder.encode(anyString())).thenReturn("new-hash");

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ─── login: geolocation and 2FA edge cases ────────────────────────────────

    @Test
    void login_withGeolocation_passesCoordinatesToSessionCreation() {
        stubSuccessfulAuth();

        LoginRequest req = new LoginRequest();
        req.setUsernameOrEmail("alice");
        req.setPassword("pass");
        req.setGeolocation(new LoginRequest.GeolocationData(37.7749, -122.4194, 10.0));

        authService.login(req, "desktop", httpRequest);

        verify(sessionService).createSession(
                anyString(), anyString(), any(), any(), eq(37.7749), eq(-122.4194));
    }

    @Test
    void login_invalid2FAMethod_throwsInternalServerError() {
        when(authentication.getPrincipal()).thenReturn(springUserDetails());
        when(authManager.authenticate(any())).thenReturn(authentication);
        when(users.findByUsername("alice")).thenReturn(Optional.of(activeUser()));

        TwoFactorAuth tfaCfg = TwoFactorAuth.builder()
                .userId("user-id-1").method("UNKNOWN_METHOD").enabled(true).build();
        when(twoFactorAuthRepository.findByUserId("user-id-1")).thenReturn(Optional.of(tfaCfg));

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        doNothing().when(valueOps).set(anyString(), anyString(), any(Long.class), any(TimeUnit.class));

        assertThatThrownBy(() -> authService.login(loginRequest("alice", "pass"), "desktop", httpRequest))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR))
                .hasMessageContaining("Invalid 2FA method");
    }

    @Test
    void login_userNotFoundAfterAuth_throwsUnauthorized() {
        when(authentication.getPrincipal()).thenReturn(springUserDetails());
        when(authManager.authenticate(any())).thenReturn(authentication);
        when(users.findByUsername("alice")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest("alice", "pass"), null, httpRequest))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    // ─── verify2FALogin: edge cases ───────────────────────────────────────────

    @Test
    void verify2FALogin_malformedTokenData_throwsInternalServerError() {
        String tempToken = "temp-malformed";
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // userData with fewer than 3 colon-separated parts
        when(valueOps.get("temp:login:" + tempToken)).thenReturn("only-one-part");

        Verify2FALoginRequest req = new Verify2FALoginRequest();
        req.setTempToken(tempToken);
        req.setCode("123456");
        req.setUseBackupCode(false);

        assertThatThrownBy(() -> authService.verify2FALogin(req, httpRequest))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR))
                .hasMessageContaining("Invalid token data");
    }

    @Test
    void verify2FALogin_userNotFound_throwsUnauthorized() {
        String tempToken = "temp-user-notfound";
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("temp:login:" + tempToken)).thenReturn("ghost-id:ghost:desktop");
        when(users.findById("ghost-id")).thenReturn(Optional.empty());

        Verify2FALoginRequest req = new Verify2FALoginRequest();
        req.setTempToken(tempToken);
        req.setCode("123456");
        req.setUseBackupCode(false);

        assertThatThrownBy(() -> authService.verify2FALogin(req, httpRequest))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void verify2FALogin_2FAConfigNotFound_throwsInternalServerError() {
        String tempToken = "temp-no-2fa-config";
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("temp:login:" + tempToken)).thenReturn("user-id-1:alice:desktop");
        when(users.findById("user-id-1")).thenReturn(Optional.of(activeUser()));
        when(twoFactorAuthRepository.findByUserId("user-id-1")).thenReturn(Optional.empty());

        Verify2FALoginRequest req = new Verify2FALoginRequest();
        req.setTempToken(tempToken);
        req.setCode("123456");
        req.setUseBackupCode(false);

        assertThatThrownBy(() -> authService.verify2FALogin(req, httpRequest))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR))
                .hasMessageContaining("2FA configuration not found");
    }

    // ─── verifyOtp: HR / HR_MANAGER roles, user not in DB ───────────────────

    @Test
    void verifyOtp_hrRole_setsWaitApprovedStatus() {
        User pendingHr = User.builder()
                .id("hr-id").username("hruser").email("hr@example.com")
                .password("hash").roles(Set.of("HR")).status(UserStatus.PENDING).build();

        when(otpService.verifyOtp("hr@example.com", "123456")).thenReturn(true);
        when(otpService.getPendingRegistration("hr@example.com"))
                .thenReturn(PendingRegistration.builder()
                        .email("hr@example.com").username("hruser").role(UserRole.HR).build());
        when(users.findByEmail("hr@example.com")).thenReturn(Optional.of(pendingHr));
        doNothing().when(userRegistrationProducer).publishUserRegistrationEvent(any());
        when(users.save(any())).thenReturn(pendingHr);
        doNothing().when(otpService).deletePendingRegistration(anyString());
        doNothing().when(notificationProducer).send(any());

        VerifyOtpRequest req = new VerifyOtpRequest();
        req.setEmail("hr@example.com");
        req.setOtp("123456");

        authService.verifyOtp(req);

        // HR is not CANDIDATE, so target status = WAIT_APPROVED
        assertThat(pendingHr.getStatus()).isEqualTo(UserStatus.WAIT_APPROVED);
    }

    @Test
    void verifyOtp_hrManagerRole_setsWaitApprovedStatus() {
        User pendingHrm = User.builder()
                .id("hrm-id").username("hrmanager").email("hrm@example.com")
                .password("hash").roles(Set.of("HR_MANAGER")).status(UserStatus.PENDING).build();

        when(otpService.verifyOtp("hrm@example.com", "123456")).thenReturn(true);
        when(otpService.getPendingRegistration("hrm@example.com"))
                .thenReturn(PendingRegistration.builder()
                        .email("hrm@example.com").username("hrmanager").role(UserRole.HR_MANAGER).build());
        when(users.findByEmail("hrm@example.com")).thenReturn(Optional.of(pendingHrm));
        doNothing().when(userRegistrationProducer).publishUserRegistrationEvent(any());
        when(users.save(any())).thenReturn(pendingHrm);
        doNothing().when(otpService).deletePendingRegistration(anyString());
        doNothing().when(notificationProducer).send(any());

        VerifyOtpRequest req = new VerifyOtpRequest();
        req.setEmail("hrm@example.com");
        req.setOtp("123456");

        authService.verifyOtp(req);

        assertThat(pendingHrm.getStatus()).isEqualTo(UserStatus.WAIT_APPROVED);
    }

    @Test
    void verifyOtp_userNotInDb_createsUserFromPending_thenActivates() {
        PendingRegistration pendingData = PendingRegistration.builder()
                .email("newcandidate@example.com").username("newcandidate")
                .role(UserRole.CANDIDATE).passwordHash("hash").build();

        User createdUser = User.builder()
                .id("new-id").username("newcandidate").email("newcandidate@example.com")
                .roles(Set.of("CANDIDATE")).status(UserStatus.PENDING).build();

        when(otpService.verifyOtp("newcandidate@example.com", "123456")).thenReturn(true);
        when(otpService.getPendingRegistration("newcandidate@example.com")).thenReturn(pendingData);
        // First call in verifyOtp → empty → triggers createUserFromPending
        when(users.findByEmail("newcandidate@example.com")).thenReturn(Optional.empty());
        when(users.save(any())).thenReturn(createdUser);
        doNothing().when(userRegistrationProducer).publishUserRegistrationEvent(any());
        doNothing().when(otpService).deletePendingRegistration(anyString());
        doNothing().when(notificationProducer).send(any());

        VerifyOtpRequest req = new VerifyOtpRequest();
        req.setEmail("newcandidate@example.com");
        req.setOtp("123456");

        authService.verifyOtp(req);

        verify(users, org.mockito.Mockito.atLeastOnce()).save(any(User.class));
    }

    // ─── maskEmail (private method via reflection) ────────────────────────────

    @Test
    void maskEmail_nullEmail_returnsNull() {
        Object result = ReflectionTestUtils.invokeMethod(authService, "maskEmail", (Object) null);
        assertThat(result).isNull();
    }

    @Test
    void maskEmail_emailWithoutAtSign_returnsOriginal() {
        String result = ReflectionTestUtils.invokeMethod(authService, "maskEmail", "nodomain");
        assertThat(result).isEqualTo("nodomain");
    }

    @Test
    void maskEmail_twoCharLocal_returnsMaskedWithStars() {
        String result = ReflectionTestUtils.invokeMethod(authService, "maskEmail", "ab@example.com");
        assertThat(result).isEqualTo("**@example.com");
    }

    @Test
    void maskEmail_oneCharLocal_returnsMaskedWithStars() {
        String result = ReflectionTestUtils.invokeMethod(authService, "maskEmail", "a@example.com");
        assertThat(result).isEqualTo("**@example.com");
    }

    @Test
    void maskEmail_longLocal_showsFirstTwoCharsAndMask() {
        String result = ReflectionTestUtils.invokeMethod(authService, "maskEmail", "alice@example.com");
        assertThat(result).isEqualTo("al****@example.com");
    }

    // ─── generateUniqueUsername (private method via reflection) ──────────────

    @Test
    void generateUniqueUsername_baseUnique_returnsBase() {
        when(users.existsByUsername("johndoe")).thenReturn(false);
        when(userServiceClient.existsByUsername("johndoe")).thenReturn(false);

        String result = ReflectionTestUtils.invokeMethod(authService, "generateUniqueUsername",
                "john.doe@example.com");

        assertThat(result).isEqualTo("johndoe");
    }

    @Test
    void generateUniqueUsername_baseTakenInAuthService_addsNumberSuffix() {
        when(users.existsByUsername("alice")).thenReturn(true);
        when(users.existsByUsername("alice1")).thenReturn(false);
        when(userServiceClient.existsByUsername("alice1")).thenReturn(false);

        String result = ReflectionTestUtils.invokeMethod(authService, "generateUniqueUsername",
                "alice@example.com");

        assertThat(result).isEqualTo("alice1");
    }

    @Test
    void generateUniqueUsername_baseTakenInUserService_addsNumberSuffix() {
        when(users.existsByUsername("alice")).thenReturn(false);
        when(userServiceClient.existsByUsername("alice")).thenReturn(true);
        when(users.existsByUsername("alice1")).thenReturn(false);
        when(userServiceClient.existsByUsername("alice1")).thenReturn(false);

        String result = ReflectionTestUtils.invokeMethod(authService, "generateUniqueUsername",
                "alice@example.com");

        assertThat(result).isEqualTo("alice1");
    }

    @Test
    void generateUniqueUsername_userServiceThrows_treatsAsNotExisting() {
        when(users.existsByUsername("alice")).thenReturn(false);
        when(userServiceClient.existsByUsername("alice"))
                .thenThrow(new RuntimeException("user-service down"));

        String result = ReflectionTestUtils.invokeMethod(authService, "generateUniqueUsername",
                "alice@example.com");

        // Exception in user-service → username treated as available
        assertThat(result).isEqualTo("alice");
    }

    @Test
    void generateUniqueUsername_shortEmailLocal_appendsUserSuffix() {
        // "ab" → length 2 < 3 → becomes "abuser"
        when(users.existsByUsername("abuser")).thenReturn(false);
        when(userServiceClient.existsByUsername("abuser")).thenReturn(false);

        String result = ReflectionTestUtils.invokeMethod(authService, "generateUniqueUsername",
                "ab@example.com");

        assertThat(result).isEqualTo("abuser");
    }

    // ─── getCurrentUser: null roles ───────────────────────────────────────────

    @Test
    void getCurrentUser_userWithNullRoles_returnsAuthenticatedWithEmptyRoles() {
        User userWithNullRoles = User.builder()
                .id("user-id-1").username("alice").email("alice@example.com")
                .password("hashed").roles(null).status(UserStatus.ACTIVE).build();
        when(users.findByUsername("alice")).thenReturn(Optional.of(userWithNullRoles));

        MeResponse me = authService.getCurrentUser("alice");

        assertThat(me.isAuthenticated()).isTrue();
        assertThat(me.getUsername()).isEqualTo("alice");
        assertThat(me.getRoles()).isEmpty();
    }
}
