package org.workfitai.authservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.workfitai.authservice.dto.AuthResponse;
import org.workfitai.authservice.dto.LoginRequest;
import org.workfitai.authservice.dto.RefreshRequest;
import org.workfitai.authservice.dto.RegisterRequest;
import org.workfitai.authservice.response.ResponseData;
import org.workfitai.authservice.service.iAuthService;

@RestController
@RequestMapping()
@RequiredArgsConstructor
public class AuthController {

    private final iAuthService authService;

    @GetMapping()
    public ResponseData<String> healthCheck() {
        return ResponseData.success("Auth Service is running");
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest req,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId
    ) {
        return ResponseEntity.ok(authService.register(req, deviceId));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest req,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId
    ) {
        return ResponseEntity.ok(authService.login(req, deviceId));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @AuthenticationPrincipal UserDetails me
    ) {
        authService.logout(deviceId, me);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshRequest req,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId
    ) {
        return ResponseEntity.ok(authService.refresh(req, deviceId));
    }
}
