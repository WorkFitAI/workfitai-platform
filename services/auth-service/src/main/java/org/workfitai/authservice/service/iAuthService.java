package org.workfitai.authservice.service;

import org.workfitai.authservice.dto.request.LoginRequest;
import org.workfitai.authservice.dto.request.RegisterRequest;
import org.workfitai.authservice.dto.request.VerifyOtpRequest;
import org.workfitai.authservice.dto.response.IssuedTokens;

public interface iAuthService {
    void register(RegisterRequest req);

    IssuedTokens login(LoginRequest req, String deviceId);

    IssuedTokens refresh(String refreshTokenFromCookie, String deviceId);

    void logout(String deviceId, String username);

    void verifyOtp(VerifyOtpRequest req);
}
