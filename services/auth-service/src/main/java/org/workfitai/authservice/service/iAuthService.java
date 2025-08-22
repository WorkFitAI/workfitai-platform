package org.workfitai.authservice.service;

import org.workfitai.authservice.dto.AuthResponse;
import org.workfitai.authservice.dto.LoginRequest;
import org.workfitai.authservice.dto.RefreshRequest;
import org.workfitai.authservice.dto.RegisterRequest;
import org.workfitai.authservice.response.*;

public interface iAuthService {
    AuthResponse register(RegisterRequest req, String deviceId);
    AuthResponse login(LoginRequest req, String deviceId);
    AuthResponse refresh(RefreshRequest req, String deviceId);
}
