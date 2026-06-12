package org.workfitai.applicationservice.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.workfitai.applicationservice.dto.response.ActivePoolResponse;
import org.workfitai.applicationservice.service.IApplicationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service-to-service endpoint, not exposed via API Gateway.
 * Accessible only within the Docker network.
 * Used by recommendation-engine at startup to warm up CvReferStore.
 */
@RestController
@RequestMapping("/internal/applications")
@RequiredArgsConstructor
@Slf4j
public class InternalApplicationController {

    private final IApplicationService applicationService;

    @GetMapping("/active-pool")
    public ResponseEntity<List<ActivePoolResponse>> getActivePool() {
        List<ActivePoolResponse> pool = applicationService.getActivePool();
        log.info("Internal sync: returning active pool — {} jobs, {} total applicants",
                pool.size(), pool.stream().mapToInt(p -> p.getApplicants() != null ? p.getApplicants().size() : 0).sum());
        return ResponseEntity.ok(pool);
    }
}
