package org.workfitai.authservice.service;

import org.workfitai.authservice.dto.IssuedTokens;
import org.workfitai.authservice.dto.LoginRequest;
import org.workfitai.authservice.dto.RegisterRequest;
import org.workfitai.authservice.dto.VerifyOtpRequest;

public interface iAuthService {
    void register(RegisterRequest req);
    IssuedTokens login(LoginRequest req, String deviceId);
    IssuedTokens refresh(String refreshTokenFromCookie, String deviceId);
    void logout(String deviceId, String username);
    void verifyOtp(VerifyOtpRequest req);
}
