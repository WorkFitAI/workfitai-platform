package org.workfitai.jobservice.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
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
        String cvProfileText = fetchCVProfileText(userId);

        if (cvProfileText == null || cvProfileText.isEmpty()) {
            log.warn("No CV found for user: {}", userId);
            return ResJobRecommendationDTO.builder()
                    .recommendations(Collections.emptyList())
                    .totalResults(0)
                    .processingTime("0ms")
                    .build();
        }

        // 2. Call Recommendation Engine
        ReqJobRecommendationDTO request = ReqJobRecommendationDTO.builder()
                .profileText(cvProfileText)
                .topK(topK != null ? topK : 20)
                .filters(filters)
                .build();

        return getRecommendationsByProfile(request);
    }

    @Override
    public ResJobRecommendationDTO getRecommendationsByProfile(ReqJobRecommendationDTO request) {
        log.info("Getting recommendations by profile, topK: {}", request.getTopK());

        try {
            // Call Recommendation Engine via Feign
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
                String jobId = (String) rec.get("jobId"); // Changed from "id" to "jobId"
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
                // Log status of found jobs
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

            // Create job map for quick lookup
            Map<String, Job> jobMap = jobs.stream()
                    .collect(Collectors.toMap(job -> job.getJobId().toString(), job -> job));

            log.info("Found {}/{} active jobs in database", jobs.size(), jobIds.size());

            // Map to response DTOs with scores, preserving recommendation order
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

        } catch (Exception e) {
            log.error("Error getting recommendations: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get recommendations from engine", e);
        }
    }

    @Override
    public ResJobRecommendationDTO getSimilarJobs(UUID jobId, Integer topK, Boolean excludeSameCompany) {
        log.info("Getting similar jobs for jobId: {}", jobId);

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("jobId", jobId.toString());
            requestBody.put("topK", topK != null ? topK : 10);
            requestBody.put("excludeSameCompany", excludeSameCompany != null ? excludeSameCompany : false);

            Map<String, Object> response = recommendationFeignClient.getSimilarJobs(requestBody);

            if (response == null || !response.containsKey("data")) {
                log.warn("No data returned from engine for jobId: {}", jobId);
                return buildEmptyResponse();
            }

            // Extract data wrapper
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.get("data");

            if (!data.containsKey("recommendations")) {
                log.warn("No similar jobs found for jobId: {}", jobId);
                return buildEmptyResponse();
            }

            // Extract and process similar jobs
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> recommendations = (List<Map<String, Object>>) data.get("recommendations");
            List<String> similarJobIds = new ArrayList<>();
            Map<String, Double> scoreMap = new HashMap<>();
            Map<String, Integer> rankMap = new HashMap<>();

            for (Map<String, Object> rec : recommendations) {
                String similarJobId = (String) rec.get("jobId"); // Changed from "id" to "jobId"
                double score = rec.get("score") instanceof Number ? ((Number) rec.get("score")).doubleValue() : 0.0;
                int rank = rec.containsKey("rank") && rec.get("rank") instanceof Number
                        ? ((Number) rec.get("rank")).intValue()
                        : 1;

                similarJobIds.add(similarJobId);
                scoreMap.put(similarJobId, score);
                rankMap.put(similarJobId, rank);
            }

            log.info("Received {} similar job IDs from recommendation engine: {}", similarJobIds.size(), similarJobIds);

            // Fetch job details
            List<Job> jobs = jobRepository.findActiveJobsByIds(
                    similarJobIds.stream().map(UUID::fromString).collect(Collectors.toList()));

            log.info("Query returned {} jobs from database", jobs.size());

            if (jobs.isEmpty()) {
                log.warn("No active similar jobs found in database for job ID: {}", jobId);
                return buildEmptyResponse();
            }

            // Create job map for quick lookup
            Map<String, Job> jobMap = jobs.stream()
                    .collect(Collectors.toMap(job -> job.getJobId().toString(), job -> job));

            log.info("Found {}/{} active similar jobs in database", jobs.size(), similarJobIds.size());

            // Map to response DTOs, preserving recommendation order
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

        } catch (Exception e) {
            log.error("Error getting similar jobs: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get similar jobs from engine", e);
        }
    }

    /**
     * Fetch CV profile text from CV Service and format for recommendations
     * Gets all CVs for user and uses the most recent one (first in list)
     */
    private String fetchCVProfileText(String username) {
        try {
            // Get all CVs for user (page 0, size 10, sorted by newest first)
            Map<String, Object> cvResponse = cvFeignClient.getCVsByUsername(username, 0, 10);

            if (cvResponse == null || !cvResponse.containsKey("data")) {
                log.warn("No CV response data for user: {}", username);
                return null;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> dataWrapper = (Map<String, Object>) cvResponse.get("data");

            if (!dataWrapper.containsKey("result")) {
                log.warn("No result field in CV response for user: {}", username);
                return null;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cvList = (List<Map<String, Object>>) dataWrapper.get("result");

            if (cvList == null || cvList.isEmpty()) {
                log.warn("No CVs found for user: {}", username);
                return null;
            }

            // Get the most recent CV (first in list)
            Map<String, Object> latestCV = cvList.get(0);
            log.info("Found {} CVs for user {}, using the most recent one", cvList.size(), username);

            StringBuilder profileText = new StringBuilder();

            // Add headline
            if (latestCV.containsKey("headline") && latestCV.get("headline") != null) {
                profileText.append(latestCV.get("headline").toString()).append("\n\n");
            }

            // Add summary
            if (latestCV.containsKey("summary") && latestCV.get("summary") != null) {
                profileText.append("Summary: ").append(latestCV.get("summary").toString()).append("\n\n");
            }

            // Extract sections (skills, experience, education, projects, languages)
            if (latestCV.containsKey("sections") && latestCV.get("sections") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> sections = (Map<String, Object>) latestCV.get("sections");

                // Skills
                if (sections.containsKey("skills")) {
                    profileText.append("Skills: ").append(extractTextFromSection(sections.get("skills")))
                            .append("\n\n");
                }

                // Experience
                if (sections.containsKey("experience")) {
                    profileText.append("Experience: ").append(extractTextFromSection(sections.get("experience")))
                            .append("\n\n");
                }

                // Education
                if (sections.containsKey("education")) {
                    profileText.append("Education: ").append(extractTextFromSection(sections.get("education")))
                            .append("\n\n");
                }

                // Projects
                if (sections.containsKey("projects")) {
                    profileText.append("Projects: ").append(extractTextFromSection(sections.get("projects")))
                            .append("\n\n");
                }

                // Languages
                if (sections.containsKey("languages")) {
                    profileText.append("Languages: ").append(extractTextFromSection(sections.get("languages")))
                            .append("\n");
                }
            }

            return profileText.toString().trim();

        } catch (Exception e) {
            log.error("Error fetching CV for user {}: {}", username, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Extract text content from section (handles string, list, and map)
     */
    private String extractTextFromSection(Object sectionData) {
        if (sectionData == null) {
            return "";
        }

        if (sectionData instanceof String) {
            return (String) sectionData;
        } else if (sectionData instanceof List) {
            StringBuilder text = new StringBuilder();
            @SuppressWarnings("unchecked")
            List<Object> items = (List<Object>) sectionData;
            for (Object item : items) {
                if (item instanceof String) {
                    text.append(item).append(", ");
                } else if (item instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> itemMap = (Map<String, Object>) item;
                    itemMap.values().forEach(value -> {
                        if (value instanceof String) {
                            text.append(value).append(", ");
                        }
                    });
                }
            }
            return text.toString().replaceAll(", $", "");
        } else if (sectionData instanceof Map) {
            StringBuilder text = new StringBuilder();
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) sectionData;
            dataMap.values().forEach(value -> {
                if (value instanceof String) {
                    text.append(value).append(", ");
                }
            });
            return text.toString().replaceAll(", $", "");
        }
        return sectionData.toString();
    }

    private ResJobRecommendationDTO buildEmptyResponse() {
        return ResJobRecommendationDTO.builder()
                .recommendations(Collections.emptyList())
                .totalResults(0)
                .processingTime("0ms")
                .build();
    }
}
