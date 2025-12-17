package org.workfitai.authservice.service;

import jakarta.servlet.http.HttpServletRequest;
import org.workfitai.authservice.dto.request.LoginRequest;
import org.workfitai.authservice.dto.request.RegisterRequest;
import org.workfitai.authservice.dto.request.Verify2FALoginRequest;
import org.workfitai.authservice.dto.request.VerifyOtpRequest;
import org.workfitai.authservice.dto.response.IssuedTokens;

public interface iAuthService {
    void register(RegisterRequest req);

    Object login(LoginRequest req, String deviceId, HttpServletRequest request); // Returns IssuedTokens or
                                                                                 // Partial2FALoginResponse

    IssuedTokens verify2FALogin(Verify2FALoginRequest request, HttpServletRequest httpRequest);

    IssuedTokens refresh(String refreshTokenFromCookie, String deviceId);

    void logout(String deviceId, String username);

    void verifyOtp(VerifyOtpRequest req);
}
