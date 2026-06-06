package org.workfitai.monitoringservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.workfitai.monitoringservice.dto.AuditPatternDto;
import org.workfitai.monitoringservice.dto.AuditPatternUpdateRequest;
import org.workfitai.monitoringservice.service.AuditPatternService;

import java.util.List;
import java.util.Map;

/**
 * ADMIN endpoints for managing audit log display patterns.
 *
 * Patterns control the human-readable message shown for each audit action.
 * Defaults are defined in AuditPatternService.DEFAULT_PATTERNS and can be
 * overridden per business need; overrides survive restarts via Redis.
 *
 * GET    /api/admin/audit/patterns           — list all patterns
 * GET    /api/admin/audit/patterns/{key}     — get one pattern
 * PUT    /api/admin/audit/patterns/{key}     — update a pattern message
 * DELETE /api/admin/audit/patterns/{key}     — reset one pattern to default
 * POST   /api/admin/audit/patterns/reset     — reset ALL patterns to defaults
 */
@RestController
@RequestMapping("/api/admin/audit/patterns")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AuditPatternController {

    private final AuditPatternService auditPatternService;

    @GetMapping
    public ResponseEntity<List<AuditPatternDto>> getAllPatterns() {
        return ResponseEntity.ok(auditPatternService.getAllPatterns());
    }

    @GetMapping("/{key}")
    public ResponseEntity<AuditPatternDto> getPattern(@PathVariable String key) {
        return auditPatternService.getPattern(key)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{key}")
    public ResponseEntity<?> updatePattern(
            @PathVariable String key,
            @RequestBody AuditPatternUpdateRequest request) {
        try {
            AuditPatternDto updated = auditPatternService.updatePattern(key, request.message());
            log.info("Admin updated audit pattern '{}' → '{}'", key, request.message());
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<?> resetPattern(@PathVariable String key) {
        try {
            AuditPatternDto reset = auditPatternService.resetPattern(key);
            log.info("Admin reset audit pattern '{}' to default", key);
            return ResponseEntity.ok(reset);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> resetAll() {
        auditPatternService.resetAll();
        log.info("Admin reset all audit patterns to defaults");
        return ResponseEntity.ok(Map.of("message", "All audit patterns reset to defaults"));
    }
}
