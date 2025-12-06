package org.workfitai.authservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.workfitai.authservice.dto.VerifyOtpRequest;
import org.workfitai.authservice.service.iAuthService;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class OtpController {

    private final iAuthService authService;

    @PostMapping("/verify-otp")
    public ResponseEntity<String> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        authService.verifyOtp(request);
        return ResponseEntity.ok("OTP verified successfully. Please wait for approval if required.");
    }
}