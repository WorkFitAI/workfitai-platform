package org.workfitai.jobservice.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.workfitai.jobservice.model.dto.request.Recommendation.ReqJobRecommendationDTO;
import org.workfitai.jobservice.model.dto.response.Recommendation.ResJobRecommendationDTO;
import org.workfitai.jobservice.model.dto.response.RestResponse;
import org.workfitai.jobservice.service.iRecommendationService;
import org.workfitai.jobservice.util.ApiMessage;

import java.util.UUID;

import static org.workfitai.jobservice.util.MessageConstant.*;

@RestController
@RequestMapping("/public/recommendations")
@RequiredArgsConstructor
@Slf4j
public class RecommendationController {

    private final iRecommendationService recommendationService;

    /**
     * Get job recommendations for current authenticated user based on their CV
     * Used for home page and personalized job discovery
     */
    @GetMapping("/for-me")
    @ApiMessage("Recommendations fetched successfully based on your CV")
    public RestResponse<ResJobRecommendationDTO> getRecommendationsForMe(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer topK,
            @RequestParam(required = false) String locations,
            @RequestParam(required = false) String experienceLevels,
            @RequestParam(required = false) String employmentTypes,
            @RequestParam(required = false) Integer minSalary,
            @RequestParam(required = false) Integer maxSalary) {
        // Get authenticated user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userId = authentication.getName();

        log.info("Getting recommendations for user: {}, topK: {}", userId, topK);

        // Build filters
        ReqJobRecommendationDTO.RecommendationFilters filters = buildFilters(
                locations, experienceLevels, employmentTypes, minSalary, maxSalary);

        ResJobRecommendationDTO recommendations = recommendationService.getRecommendationsByCV(
                userId, topK, filters);

        return RestResponse.success(recommendations);
    }

    /**
     * Get job recommendations based on custom profile text
     * Used for advanced search or exploration
     */
    @PostMapping("/by-profile")
    @ApiMessage("Recommendations fetched successfully based on profile")
    public RestResponse<ResJobRecommendationDTO> getRecommendationsByProfile(
            @RequestBody ReqJobRecommendationDTO request) {
        log.info("Getting recommendations by profile, topK: {}", request.getTopK());

        // Set default topK if not provided
        if (request.getTopK() == null) {
            request.setTopK(20);
        }

        ResJobRecommendationDTO recommendations = recommendationService.getRecommendationsByProfile(request);
        return RestResponse.success(recommendations);
    }

    /**
     * Get similar jobs to a given job
     * Used in job detail page to show related opportunities
     */
    @GetMapping("/similar/{jobId}")
    @ApiMessage("Similar jobs fetched successfully")
    public RestResponse<ResJobRecommendationDTO> getSimilarJobs(
            @PathVariable UUID jobId,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) Integer topK,
            @RequestParam(defaultValue = "false") Boolean excludeSameCompany) {
        log.info("Getting similar jobs for jobId: {}, topK: {}", jobId, topK);

        ResJobRecommendationDTO recommendations = recommendationService.getSimilarJobs(
                jobId, topK, excludeSameCompany);

        return RestResponse.success(recommendations);
    }

    /**
     * Helper method to build filters from query parameters
     */
    private ReqJobRecommendationDTO.RecommendationFilters buildFilters(
            String locations,
            String experienceLevels,
            String employmentTypes,
            Integer minSalary,
            Integer maxSalary) {
        ReqJobRecommendationDTO.RecommendationFilters filters = new ReqJobRecommendationDTO.RecommendationFilters();

        if (locations != null && !locations.isEmpty()) {
            filters.setLocations(java.util.Arrays.asList(locations.split(",")));
        }

        if (experienceLevels != null && !experienceLevels.isEmpty()) {
            filters.setExperienceLevels(java.util.Arrays.asList(experienceLevels.split(",")));
        }

        if (employmentTypes != null && !employmentTypes.isEmpty()) {
            filters.setEmploymentTypes(java.util.Arrays.asList(employmentTypes.split(",")));
        }

        if (minSalary != null) {
            filters.setMinSalary(minSalary);
        }

        if (maxSalary != null) {
            filters.setMaxSalary(maxSalary);
        }

        return filters;
    }
}
