package org.workfitai.monitoringservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.workfitai.monitoringservice.dto.AuditEventResponse;
import org.workfitai.monitoringservice.dto.AuditSearchRequest;
import org.workfitai.monitoringservice.dto.HrmDashboardResponse;
import org.workfitai.monitoringservice.service.AuditSearchService;
import org.workfitai.monitoringservice.service.HrmDashboardService;

/**
 * HRM-only audit endpoints — all results auto-scoped to the caller's companyId from JWT.
 *
 * companyId is extracted from the JWT "companyId" claim at request time
 * (enforced at write time by the publishing service; enforced here at read time).
 * HR_MANAGER cannot supply a different companyId — it is always taken from their token.
 */
@RestController
@RequestMapping("/hrm")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('HR_MANAGER')")
public class HrmController {

    private final AuditSearchService auditSearchService;
    private final HrmDashboardService hrmDashboardService;

    /**
     * Company-scoped dashboard: manager app stats + audit stats.
     * companyId always extracted from JWT — never a query param.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<HrmDashboardResponse> getDashboard(Authentication authentication) {
        String companyId = extractCompanyId(authentication);
        if (companyId == null) {
            log.warn("[HRM-DASH] HR_MANAGER {} has no companyId claim", authentication.getName());
            return ResponseEntity.ok(new HrmDashboardResponse(null, null, null, null));
        }
        return ResponseEntity.ok(hrmDashboardService.getDashboard(companyId));
    }

    /**
     * Company-scoped audit history for HR_MANAGER.
     * companyId is never a query param — always extracted from JWT to prevent scope escape.
     *
     * @param req      optional filters: entityType, action, from, to (companyId ignored if supplied)
     * @param pageable pagination (default size=50, sorted by occurredAt DESC)
     */
    @GetMapping("/audit")
    public ResponseEntity<Page<AuditEventResponse>> getAudit(
            AuditSearchRequest req,
            @PageableDefault(size = 50, sort = "occurredAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            Authentication authentication) {

        String companyId = extractCompanyId(authentication);
        if (companyId == null) {
            log.warn("[HRM-AUDIT] HR_MANAGER {} has no companyId claim — returning empty",
                    authentication.getName());
            return ResponseEntity.ok(Page.empty(pageable));
        }

        log.debug("[HRM-AUDIT] user={} companyId={}", authentication.getName(), companyId);
        return ResponseEntity.ok(auditSearchService.searchHrm(companyId, req, pageable));
    }

    /** Extracts companyId from the JWT "companyId" claim. Returns null if absent. */
    private String extractCompanyId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtToken) {
            Jwt jwt = jwtToken.getToken();
            Object claim = jwt.getClaims().get("companyId");
            return claim != null ? claim.toString() : null;
        }
        return null;
    }
}
