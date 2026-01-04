package org.workfitai.userservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.workfitai.userservice.dto.request.DeactivateAccountRequest;
import org.workfitai.userservice.dto.request.DeleteAccountRequest;
import org.workfitai.userservice.dto.response.AccountManagementResponse;
import org.workfitai.userservice.service.AccountManagementService;

@Slf4j
@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
public class AccountManagementController {

    private final AccountManagementService accountManagementService;

    @PostMapping("/deactivate")
    public ResponseEntity<AccountManagementResponse> deactivateAccount(
            @Valid @RequestBody DeactivateAccountRequest request,
            Authentication authentication) {

        String username = authentication.getName();
        log.info("Deactivate account request for user: {}", username);
        log.info("Authentication principal: {}", authentication.getPrincipal());
        log.info("Authentication details: {}", authentication.getDetails());

        AccountManagementResponse response = accountManagementService.deactivateAccount(username, request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/delete-request")
    public ResponseEntity<AccountManagementResponse> requestAccountDeletion(
            @Valid @RequestBody DeleteAccountRequest request,
            Authentication authentication) {

        String username = authentication.getName();
        log.info("Delete account request for user: {}", username);

        AccountManagementResponse response = accountManagementService.requestAccountDeletion(username, request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/cancel-deletion")
    public ResponseEntity<AccountManagementResponse> cancelAccountDeletion(Authentication authentication) {
        String username = authentication.getName();
        log.info("Cancel account deletion request for user: {}", username);

        AccountManagementResponse response = accountManagementService.cancelAccountDeletion(username);

        return ResponseEntity.ok(response);
    }
}
