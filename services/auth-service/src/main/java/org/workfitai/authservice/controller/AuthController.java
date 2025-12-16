package org.workfitai.authservice.controller;

import java.security.Principal;
import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.workfitai.authservice.constants.Messages;
import org.workfitai.authservice.dto.request.LoginRequest;
import org.workfitai.authservice.dto.request.RegisterRequest;
import org.workfitai.authservice.dto.request.Verify2FALoginRequest;
import org.workfitai.authservice.dto.request.VerifyOtpRequest;
import org.workfitai.authservice.dto.response.IssuedTokens;
import org.workfitai.authservice.dto.response.Partial2FALoginResponse;
import org.workfitai.authservice.dto.response.ResponseData;
import org.workfitai.authservice.dto.response.TokensResponse;
import org.workfitai.authservice.security.JwtService;
import org.workfitai.authservice.service.iAuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping()
@RequiredArgsConstructor
public class AuthController {

        private final iAuthService authService;
        private final JwtService jwtService;

        @GetMapping()
        public ResponseData<String> healthCheck() {
                return ResponseData.success(Messages.Success.AUTH_SERVICE_RUNNING,
                                Messages.Success.AUTH_SERVICE_RUNNING);
        }

        @PostMapping("/register")
        public ResponseEntity<ResponseData<String>> register(
                        @Valid @RequestBody RegisterRequest req) {
                authService.register(req);
                return ResponseEntity.ok(ResponseData.success(Messages.Success.USER_REGISTERED,
                                "OTP has been sent to your email for verification"));
        }

        @PostMapping("/verify-otp")
        public ResponseEntity<ResponseData<Void>> verifyOtp(@Valid @RequestBody VerifyOtpRequest req) {
                authService.verifyOtp(req);
                return ResponseEntity.ok(ResponseData.success("Account verified"));
        }

        @PostMapping("/login")
        public ResponseEntity<ResponseData<?>> login(
                        @Valid @RequestBody LoginRequest req,
                        @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
                        HttpServletRequest request) {
                Object result = authService.login(req, deviceId, request);

                // Check if 2FA is required
                if (result instanceof Partial2FALoginResponse) {
                        return ResponseEntity.ok()
                                        .body(ResponseData.success(
                                                        "2FA verification required",
                                                        result));
                }

                // Normal login - return tokens
                IssuedTokens issued = (IssuedTokens) result;
                var cookie = ResponseCookie.from(Messages.Misc.REFRESH_TOKEN_COOKIE_NAME, issued.getRefreshToken())
                                .httpOnly(true)
                                .path("/")
                                .maxAge(Duration.ofMillis(jwtService.getRefreshExpMs()))
                                .build();
                return ResponseEntity.ok()
                                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                                .body(ResponseData.success(
                                                Messages.Success.TOKENS_ISSUED,
                                                TokensResponse.withUserInfo(
                                                                issued.getAccessToken(),
                                                                issued.getExpiresIn(),
                                                                issued.getUsername(),
                                                                issued.getRoles())));
        }

        @PostMapping("/verify-2fa-login")
        public ResponseEntity<ResponseData<TokensResponse>> verify2FALogin(
                        @Valid @RequestBody Verify2FALoginRequest req,
                        HttpServletRequest request) {
                var issued = authService.verify2FALogin(req, request);
                var cookie = ResponseCookie.from(Messages.Misc.REFRESH_TOKEN_COOKIE_NAME, issued.getRefreshToken())
                                .httpOnly(true)
                                .path("/")
                                .maxAge(Duration.ofMillis(jwtService.getRefreshExpMs()))
                                .build();
                return ResponseEntity.ok()
                                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                                .body(ResponseData.success(
                                                Messages.Success.TOKENS_ISSUED,
                                                TokensResponse.withUserInfo(
                                                                issued.getAccessToken(),
                                                                issued.getExpiresIn(),
                                                                issued.getUsername(),
                                                                issued.getRoles())));
        }

        @PostMapping("/logout")
        public ResponseEntity<ResponseData<Void>> logout(
                        @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
                        Principal principal) {

                if (principal == null) {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, Messages.Error.UNAUTHORIZED);
                }
                authService.logout(deviceId, principal.getName());

                // Xoá cookie refresh token (RT)
                ResponseCookie deleteCookie = ResponseCookie.from(Messages.Misc.REFRESH_TOKEN_COOKIE_NAME, "")
                                .httpOnly(true)
                                .path("/")
                                .maxAge(0) // hết hạn ngay
                                .build();

                return ResponseEntity.ok()
                                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                                .body(ResponseData.success(Messages.Success.LOGGED_OUT));
        }

        @PostMapping("/refresh")
        public ResponseEntity<ResponseData<TokensResponse>> refresh(
                        @CookieValue(name = Messages.Misc.REFRESH_TOKEN_COOKIE_NAME) String refreshToken,
                        @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
                var issued = authService.refresh(refreshToken, deviceId);
                var cookie = ResponseCookie.from(Messages.Misc.REFRESH_TOKEN_COOKIE_NAME, issued.getRefreshToken())// rotate
                                .httpOnly(true).secure(true).sameSite("Strict")
                                .path("/")
                                .maxAge(Duration.ofMillis(jwtService.getRefreshExpMs()))
                                .build();
                return ResponseEntity.ok()
                                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                                .body(ResponseData.success(
                                                Messages.Success.TOKENS_REFRESHED,
                                                TokensResponse.withUserInfo(
                                                                issued.getAccessToken(),
                                                                issued.getExpiresIn(),
                                                                issued.getUsername(),
                                                                issued.getRoles())));
        }
}
