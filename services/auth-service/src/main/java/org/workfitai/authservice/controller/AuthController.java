package org.workfitai.authservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
            @Valid @RequestBody RegisterRequest req
    ) {
        return ResponseEntity.ok(authService.register(req));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest req
    ) {
        return ResponseEntity.ok(authService.login(req));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshRequest req
    ) {
        return ResponseEntity.ok(authService.refresh(req));
    }
}
