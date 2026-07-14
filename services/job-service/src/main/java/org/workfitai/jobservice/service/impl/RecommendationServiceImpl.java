package org.workfitai.jobservice.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.workfitai.jobservice.client.CVFeignClient;
import org.workfitai.jobservice.client.RecommendationFeignClient;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.dto.request.Recommendation.ReqJobRecommendationDTO;
import org.workfitai.jobservice.model.dto.response.Recommendation.ResJobRecommendationDTO;
import org.workfitai.jobservice.model.mapper.JobMapper;
import org.workfitai.jobservice.repository.JobRepository;
import org.workfitai.jobservice.service.iRecommendationService;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationServiceImpl implements iRecommendationService {

    private final RecommendationFeignClient recommendationFeignClient;
    private final CVFeignClient cvFeignClient;
    private final JobRepository jobRepository;
    private final JobMapper jobMapper;
    private final ObjectMapper objectMapper;

    @Override
    public ResJobRecommendationDTO getRecommendationsByCV(String userId, Integer topK,
            ReqJobRecommendationDTO.RecommendationFilters filters) {
        log.info("Fetching recommendations for user: {}", userId);

        // 1. Get CV from CV Service
        Map<String, Object> cvData = fetchCVData(userId);

        if (cvData == null) {
            log.warn("No CV found for user: {}", userId);
            return buildEmptyResponse();
        }

        String resumeSummary = asText(cvData.get("resumeSummary"));
        String resumeSkills = asText(cvData.get("resumeSkills"));
        String resumeExperience = asText(cvData.get("resumeExperience"));
        String resumeEducation = asText(cvData.get("resumeEducation"));

        String cvProfileText = buildProfileText(resumeSummary, resumeSkills, resumeExperience, resumeEducation);

        if (cvProfileText.isEmpty()) {
            log.warn("CV data empty for user: {}", userId);
            return buildEmptyResponse();
        }

        // 2. Call Recommendation Engine. Pass BOTH the flattened profileText (backward
        // compatible / cross-encoder query) AND the structured sections, so the engine
        // uses its multi-field pipeline and seniority guardrail rather than a single
        // encode of profileText.
        //
        // candidateUsername is deliberately NOT set here: it makes the engine enforce
        // per-candidate AI consent by calling user-service, and ai_consent_client fails
        // CLOSED — so a transient user-service outage turns the whole home feed into a
        // 503 for every candidate. /for-me is the candidate viewing their own feed and
        // is already gated by the admin platform toggle in the engine; the extra hard
        // dependency on user-service is not worth that failure mode.
        ReqJobRecommendationDTO request = ReqJobRecommendationDTO.builder()
                .profileText(cvProfileText)
                .resumeSummary(resumeSummary)
                .resumeSkills(resumeSkills)
                .resumeExperience(resumeExperience)
                .resumeEducation(resumeEducation)
                .topK(topK != null ? topK : 20)
                .filters(filters)
                .build();

        return getRecommendationsByProfile(request);
    }

    @Override
    public ResJobRecommendationDTO getRecommendationsByProfile(ReqJobRecommendationDTO request) {
        log.info("Getting recommendations by profile, topK: {}", request.getTopK());

        // Feign exceptions propagate to the caller so @AfterThrowing in JobAuditAspect fires correctly.
        // Swallowing them caused audit logs to show SUCCESS even when the engine was down.
        Map<String, Object> response = recommendationFeignClient.getRecommendationsByProfile(request);

        if (response == null || !response.containsKey("data")) {
            log.warn("No data returned from engine");
            return buildEmptyResponse();
        }

        // Extract data wrapper
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");

        if (!data.containsKey("recommendations")) {
            log.warn("No recommendations in data");
            return buildEmptyResponse();
        }

        // Extract job IDs and scores
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> recommendations = (List<Map<String, Object>>) data.get("recommendations");

        if (recommendations == null || recommendations.isEmpty()) {
            log.warn("Empty recommendations list");
            return buildEmptyResponse();
        }

        List<String> jobIds = new ArrayList<>();
        Map<String, Double> scoreMap = new HashMap<>();
        Map<String, Integer> rankMap = new HashMap<>();

        for (Map<String, Object> rec : recommendations) {
            String jobId = (String) rec.get("jobId");
            double score = rec.get("score") instanceof Number ? ((Number) rec.get("score")).doubleValue() : 0.0;
            int rank = rec.containsKey("rank") && rec.get("rank") instanceof Number
                    ? ((Number) rec.get("rank")).intValue()
                    : 1;

            jobIds.add(jobId);
            scoreMap.put(jobId, score);
            rankMap.put(jobId, rank);
        }

        log.info("Received {} job IDs from recommendation engine: {}", jobIds.size(), jobIds);

        // Debug: Check if jobs exist in database (without filters)
        List<UUID> uuidList = jobIds.stream().map(UUID::fromString).collect(Collectors.toList());
        List<Job> allJobs = jobRepository.findAllById(uuidList);
        log.info("DEBUG: Found {} jobs in database (before filtering), missing {} jobs",
                allJobs.size(), jobIds.size() - allJobs.size());

        if (!allJobs.isEmpty()) {
            Map<String, Long> statusCounts = allJobs.stream()
                    .collect(Collectors.groupingBy(j -> j.getStatus().name(), Collectors.counting()));
            long deletedCount = allJobs.stream().filter(Job::isDeleted).count();
            long expiredCount = allJobs.stream()
                    .filter(j -> j.getExpiresAt().isBefore(Instant.now())).count();

            log.info("DEBUG: Job status breakdown - Status: {}, Deleted: {}, Expired: {}",
                    statusCounts, deletedCount, expiredCount);
        }

        // Fetch full job details from database (with active filters)
        List<Job> jobs = jobRepository.findActiveJobsByIds(uuidList);

        log.info("Query returned {} jobs from database (after active filters)", jobs.size());

        if (jobs.isEmpty()) {
            log.warn("No active jobs found in database for recommended job IDs");
            return buildEmptyResponse();
        }

        Map<String, Job> jobMap = jobs.stream()
                .collect(Collectors.toMap(job -> job.getJobId().toString(), job -> job));

        log.info("Found {}/{} active jobs in database", jobs.size(), jobIds.size());

        List<ResJobRecommendationDTO.JobRecommendation> jobRecommendations = new ArrayList<>();

        for (String jobId : jobIds) {
            Job job = jobMap.get(jobId);
            if (job != null) {
                jobRecommendations.add(
                        ResJobRecommendationDTO.JobRecommendation.builder()
                                .job(jobMapper.toResJobDTO(job))
                                .score(scoreMap.getOrDefault(jobId, 0.0))
                                .rank(rankMap.getOrDefault(jobId, 1))
                                .build());
            } else {
                log.warn("Job {} from recommendations not found in active jobs (may be expired/deleted)", jobId);
            }
        }

        String processingTime = response.containsKey("processingTime")
                ? response.get("processingTime").toString()
                : "N/A";

        return ResJobRecommendationDTO.builder()
                .recommendations(jobRecommendations)
                .totalResults(jobRecommendations.size())
                .processingTime(processingTime)
                .build();
    }

    @Override
    public ResJobRecommendationDTO getSimilarJobs(UUID jobId, Integer topK, Boolean excludeSameCompany) {
        log.info("Getting similar jobs for jobId: {}", jobId);

        // Feign exceptions propagate so @AfterThrowing in JobAuditAspect fires correctly.
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("jobId", jobId.toString());
        requestBody.put("topK", topK != null ? topK : 10);
        requestBody.put("excludeSameCompany", excludeSameCompany != null ? excludeSameCompany : false);

        Map<String, Object> response = recommendationFeignClient.getSimilarJobs(requestBody);

        if (response == null || !response.containsKey("data")) {
            log.warn("No data returned from engine for jobId: {}", jobId);
            return buildEmptyResponse();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");

        if (!data.containsKey("recommendations")) {
            log.warn("No similar jobs found for jobId: {}", jobId);
            return buildEmptyResponse();
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> recommendations = (List<Map<String, Object>>) data.get("recommendations");
        List<String> similarJobIds = new ArrayList<>();
        Map<String, Double> scoreMap = new HashMap<>();
        Map<String, Integer> rankMap = new HashMap<>();

        for (Map<String, Object> rec : recommendations) {
            String similarJobId = (String) rec.get("jobId");
            double score = rec.get("score") instanceof Number ? ((Number) rec.get("score")).doubleValue() : 0.0;
            int rank = rec.containsKey("rank") && rec.get("rank") instanceof Number
                    ? ((Number) rec.get("rank")).intValue()
                    : 1;

            similarJobIds.add(similarJobId);
            scoreMap.put(similarJobId, score);
            rankMap.put(similarJobId, rank);
        }

        log.info("Received {} similar job IDs from recommendation engine: {}", similarJobIds.size(), similarJobIds);

        List<Job> jobs = jobRepository.findActiveJobsByIds(
                similarJobIds.stream().map(UUID::fromString).collect(Collectors.toList()));

        log.info("Query returned {} jobs from database", jobs.size());

        if (jobs.isEmpty()) {
            log.warn("No active similar jobs found in database for job ID: {}", jobId);
            return buildEmptyResponse();
        }

        Map<String, Job> jobMap = jobs.stream()
                .collect(Collectors.toMap(job -> job.getJobId().toString(), job -> job));

        log.info("Found {}/{} active similar jobs in database", jobs.size(), similarJobIds.size());

        List<ResJobRecommendationDTO.JobRecommendation> jobRecommendations = new ArrayList<>();

        for (String similarJobId : similarJobIds) {
            Job job = jobMap.get(similarJobId);
            if (job != null) {
                jobRecommendations.add(
                        ResJobRecommendationDTO.JobRecommendation.builder()
                                .job(jobMapper.toResJobDTO(job))
                                .score(scoreMap.getOrDefault(similarJobId, 0.0))
                                .rank(rankMap.getOrDefault(similarJobId, 1))
                                .build());
            } else {
                log.warn("Similar job {} not found in active jobs (may be expired/deleted)", similarJobId);
            }
        }

        String processingTime = response.containsKey("processingTime")
                ? response.get("processingTime").toString()
                : "N/A";

        return ResJobRecommendationDTO.builder()
                .recommendations(jobRecommendations)
                .totalResults(jobRecommendations.size())
                .processingTime(processingTime)
                .build();
    }

    /**
     * Fetch CV profile text via the internal batch endpoint (no JWT required, Docker-network only).
     * Returns null when the user has no CV (legitimate empty state, not an error).
     * Feign exceptions propagate so audit @AfterThrowing fires on service failure.
     */
    private Map<String, Object> fetchCVData(String username) {
        List<Map<String, Object>> cvDataList = cvFeignClient.getCvDataBatch(List.of(username));

        if (cvDataList == null || cvDataList.isEmpty()) {
            log.warn("No CV found for user: {}", username);
            return null;
        }

        log.info("Found CV data for user {}", username);
        return cvDataList.get(0);
    }

    private static String asText(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    /** Flatten the structured CV sections into labeled profile text (cross-encoder query). */
    private String buildProfileText(String summary, String skills, String experience, String education) {
        StringBuilder sb = new StringBuilder();
        appendField(sb, "Summary", summary);
        appendField(sb, "Skills", skills);
        appendField(sb, "Experience", experience);
        appendField(sb, "Education", education);
        return sb.toString().trim();
    }

    private void appendField(StringBuilder sb, String label, String value) {
        if (value != null && !value.isEmpty()) {
            sb.append(label).append(": ").append(value).append("\n\n");
        }
    }

    private ResJobRecommendationDTO buildEmptyResponse() {
        return ResJobRecommendationDTO.builder()
                .recommendations(Collections.emptyList())
                .totalResults(0)
                .processingTime("0ms")
                .build();
    }
}
