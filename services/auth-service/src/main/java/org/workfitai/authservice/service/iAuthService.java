package org.workfitai.authservice.service;

import org.workfitai.authservice.dto.IssuedTokens;
import org.workfitai.authservice.dto.LoginRequest;
import org.workfitai.authservice.dto.RegisterRequest;

public interface iAuthService {
    IssuedTokens register(RegisterRequest req, String deviceId);
    IssuedTokens login(LoginRequest req, String deviceId);
    IssuedTokens refresh(String refreshTokenFromCookie, String deviceId);
    void logout(String deviceId, String username);
}
