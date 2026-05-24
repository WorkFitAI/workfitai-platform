package org.workfitai.authservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.workfitai.authservice.constants.Messages;
import org.workfitai.authservice.dto.response.GrantAuditLogResponse;
import org.workfitai.authservice.dto.response.ResponseData;
import org.workfitai.authservice.service.iGrantAuditService;

import java.util.List;

/**
 * Read-only view of the role/permission grant audit trail.
 *
 * Access rules:
 *   GET /audit/grants             → ADMIN only  — full history across all users
 *   GET /audit/grants/target/{u}  → ADMIN or self — role changes received by a user
 *   GET /audit/grants/grantor/{u} → ADMIN only  — actions performed by a grantor
 *   GET /audit/grants/me          → any authenticated user — own incoming changes
 */
@RestController
@RequestMapping("/audit/grants")
@RequiredArgsConstructor
public class AuditController {

    private final iGrantAuditService auditService;

    /** ADMIN — full audit history, newest first */
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ResponseData<List<GrantAuditLogResponse>>> getAllAuditLogs() {
        List<GrantAuditLogResponse> logs = auditService.findAll()
                .stream()
                .map(GrantAuditLogResponse::from)
                .toList();
        return ResponseEntity.ok(ResponseData.success(Messages.Success.AUDIT_LOGS_FETCHED, logs));
    }

    /**
     * Role changes applied TO a specific user.
     * ADMIN can query any username; a regular user can only query their own.
     */
    @GetMapping("/target/{username}")
    @PreAuthorize("hasAuthority('ADMIN') or #username == authentication.name")
    public ResponseEntity<ResponseData<List<GrantAuditLogResponse>>> getAuditLogsByTarget(
            @PathVariable String username) {
        List<GrantAuditLogResponse> logs = auditService.findByTarget(username)
                .stream()
                .map(GrantAuditLogResponse::from)
                .toList();
        return ResponseEntity.ok(ResponseData.success(Messages.Success.AUDIT_LOGS_FETCHED, logs));
    }

    /** ADMIN — all actions performed BY a specific grantor */
    @GetMapping("/grantor/{username}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ResponseData<List<GrantAuditLogResponse>>> getAuditLogsByGrantor(
            @PathVariable String username) {
        List<GrantAuditLogResponse> logs = auditService.findByGrantor(username)
                .stream()
                .map(GrantAuditLogResponse::from)
                .toList();
        return ResponseEntity.ok(ResponseData.success(Messages.Success.AUDIT_LOGS_FETCHED, logs));
    }

    /** Any authenticated user — their own incoming role/permission changes */
    @GetMapping("/me")
    public ResponseEntity<ResponseData<List<GrantAuditLogResponse>>> getMyAuditLogs(
            Authentication authentication) {
        List<GrantAuditLogResponse> logs = auditService.findByTarget(authentication.getName())
                .stream()
                .map(GrantAuditLogResponse::from)
                .toList();
        return ResponseEntity.ok(ResponseData.success(Messages.Success.AUDIT_LOGS_FETCHED, logs));
    }
}
