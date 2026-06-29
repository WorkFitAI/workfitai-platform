package org.workfitai.jobservice.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.jobservice.client.CVFeignClient;
import org.workfitai.jobservice.client.RecommendationFeignClient;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.dto.request.Recommendation.ReqJobRecommendationDTO;
import org.workfitai.jobservice.model.dto.response.Job.ResJobDTO;
import org.workfitai.jobservice.model.dto.response.Recommendation.ResJobRecommendationDTO;
import org.workfitai.jobservice.model.mapper.JobMapper;
import org.workfitai.jobservice.repository.JobRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceImplTest {

    @Mock
    private RecommendationFeignClient recommendationFeignClient;
    @Mock
    private CVFeignClient cvFeignClient;
    @Mock
    private JobRepository jobRepository;
    @Mock
    private JobMapper jobMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks
    private RecommendationServiceImpl recommendationService;

    @Test
    void getRecommendationsByCV_returnsEmpty_whenBatchReturnsEmptyList() {
        when(cvFeignClient.getCvDataBatch(List.of("alice"))).thenReturn(List.of());

        ResJobRecommendationDTO result = recommendationService.getRecommendationsByCV("alice", 5, null);

        assertThat(result.getTotalResults()).isEqualTo(0);
        assertThat(result.getRecommendations()).isEmpty();
    }

    @Test
    void getRecommendationsByCV_returnsEmpty_whenBatchReturnsNull() {
        when(cvFeignClient.getCvDataBatch(List.of("alice"))).thenReturn(null);

        ResJobRecommendationDTO result = recommendationService.getRecommendationsByCV("alice", null, null);

        assertThat(result.getRecommendations()).isEmpty();
    }

    @Test
    void getRecommendationsByCV_buildsProfileTextAndDelegatesToProfileSearch() {
        Map<String, Object> cvData = Map.of(
                "resumeSummary", "10 years experience",
                "resumeSkills", "Java, Spring");
        when(cvFeignClient.getCvDataBatch(List.of("alice"))).thenReturn(List.of(cvData));
        when(recommendationFeignClient.getRecommendationsByProfile(any())).thenReturn(Map.of());

        ResJobRecommendationDTO result = recommendationService.getRecommendationsByCV("alice", 5, null);

        assertThat(result.getRecommendations()).isEmpty();
    }

    @Test
    void getRecommendationsByCV_returnsEmpty_whenAllCvFieldsEmpty() {
        Map<String, Object> cvData = Map.of(
                "resumeSummary", "",
                "resumeSkills", "",
                "resumeExperience", "",
                "resumeEducation", "");
        when(cvFeignClient.getCvDataBatch(List.of("alice"))).thenReturn(List.of(cvData));

        ResJobRecommendationDTO result = recommendationService.getRecommendationsByCV("alice", 5, null);

        assertThat(result.getRecommendations()).isEmpty();
    }

    @Test
    void getRecommendationsByCV_returnsEmpty_whenCvDataHasNoResumeFields() {
        when(cvFeignClient.getCvDataBatch(List.of("alice"))).thenReturn(List.of(Map.of("username", "alice")));

        ResJobRecommendationDTO result = recommendationService.getRecommendationsByCV("alice", 5, null);

        assertThat(result.getRecommendations()).isEmpty();
    }

    @Test
    void getRecommendationsByCV_buildsFullProfileText_whenAllFieldsPresent() {
        Map<String, Object> cvData = new java.util.HashMap<>();
        cvData.put("resumeSummary", "10 years experience");
        cvData.put("resumeSkills", "Java\nSpring");
        cvData.put("resumeExperience", "FPT Software");
        cvData.put("resumeEducation", "BSc Computer Science");
        when(cvFeignClient.getCvDataBatch(List.of("alice"))).thenReturn(List.of(cvData));
        when(recommendationFeignClient.getRecommendationsByProfile(any())).thenReturn(Map.of());

        ResJobRecommendationDTO result = recommendationService.getRecommendationsByCV("alice", 5, null);

        assertThat(result.getRecommendations()).isEmpty();
    }

    @Test
    void getRecommendationsByCV_buildsProfileText_whenOnlySomeFieldsPresent() {
        Map<String, Object> cvData = Map.of("resumeSummary", "Backend developer");
        when(cvFeignClient.getCvDataBatch(List.of("alice"))).thenReturn(List.of(cvData));
        when(recommendationFeignClient.getRecommendationsByProfile(any())).thenReturn(Map.of());

        ResJobRecommendationDTO result = recommendationService.getRecommendationsByCV("alice", 5, null);

        assertThat(result.getRecommendations()).isEmpty();
    }

    @Test
    void getRecommendationsByProfile_returnsEmpty_whenResponseIsNull() {
        when(recommendationFeignClient.getRecommendationsByProfile(any())).thenReturn(null);

        ResJobRecommendationDTO result = recommendationService
                .getRecommendationsByProfile(ReqJobRecommendationDTO.builder().profileText("x").topK(5).build());

        assertThat(result.getTotalResults()).isEqualTo(0);
    }

    @Test
    void getRecommendationsByProfile_returnsEmpty_whenResponseHasNoDataKey() {
        when(recommendationFeignClient.getRecommendationsByProfile(any())).thenReturn(Map.of("status", "ok"));

        ResJobRecommendationDTO result = recommendationService
                .getRecommendationsByProfile(ReqJobRecommendationDTO.builder().profileText("x").topK(5).build());

        assertThat(result.getTotalResults()).isEqualTo(0);
    }

    @Test
    void getRecommendationsByProfile_returnsEmpty_whenDataHasNoRecommendationsKey() {
        when(recommendationFeignClient.getRecommendationsByProfile(any())).thenReturn(Map.of("data", Map.of()));

        ResJobRecommendationDTO result = recommendationService
                .getRecommendationsByProfile(ReqJobRecommendationDTO.builder().profileText("x").topK(5).build());

        assertThat(result.getTotalResults()).isEqualTo(0);
    }

    @Test
    void getRecommendationsByProfile_returnsEmpty_whenRecommendationsListEmpty() {
        when(recommendationFeignClient.getRecommendationsByProfile(any()))
                .thenReturn(Map.of("data", Map.of("recommendations", List.of())));

        ResJobRecommendationDTO result = recommendationService
                .getRecommendationsByProfile(ReqJobRecommendationDTO.builder().profileText("x").topK(5).build());

        assertThat(result.getTotalResults()).isEqualTo(0);
    }

    @Test
    void getRecommendationsByProfile_usesDefaults_whenScoreAndRankMissing() {
        UUID jobId = UUID.randomUUID();
        Map<String, Object> response = Map.of(
                "data", Map.of("recommendations", List.of(Map.of("jobId", jobId.toString()))));
        when(recommendationFeignClient.getRecommendationsByProfile(any())).thenReturn(response);
        Job job = new Job();
        job.setJobId(jobId);
        job.setStatus(org.workfitai.jobservice.model.enums.JobStatus.PUBLISHED);
        job.setExpiresAt(java.time.Instant.now().plusSeconds(3600));
        when(jobRepository.findAllById(any())).thenReturn(List.of(job));
        when(jobRepository.findActiveJobsByIds(any())).thenReturn(List.of(job));
        when(jobMapper.toResJobDTO(job)).thenReturn(ResJobDTO.builder().build());

        ResJobRecommendationDTO result = recommendationService
                .getRecommendationsByProfile(ReqJobRecommendationDTO.builder().profileText("x").topK(5).build());

        assertThat(result.getRecommendations().get(0).getScore()).isEqualTo(0.0);
        assertThat(result.getRecommendations().get(0).getRank()).isEqualTo(1);
        assertThat(result.getProcessingTime()).isEqualTo("N/A");
    }

    @Test
    void getRecommendationsByProfile_skipsJob_whenNotInActiveJobMap() {
        UUID matchedJobId = UUID.randomUUID();
        UUID missingJobId = UUID.randomUUID();
        Map<String, Object> response = Map.of(
                "data", Map.of("recommendations", List.of(
                        Map.of("jobId", matchedJobId.toString(), "score", 0.9, "rank", 1),
                        Map.of("jobId", missingJobId.toString(), "score", 0.5, "rank", 2))));
        when(recommendationFeignClient.getRecommendationsByProfile(any())).thenReturn(response);
        Job matchedJob = new Job();
        matchedJob.setJobId(matchedJobId);
        matchedJob.setStatus(org.workfitai.jobservice.model.enums.JobStatus.PUBLISHED);
        matchedJob.setExpiresAt(java.time.Instant.now().plusSeconds(3600));
        when(jobRepository.findAllById(any())).thenReturn(List.of(matchedJob));
        when(jobRepository.findActiveJobsByIds(any())).thenReturn(List.of(matchedJob));
        when(jobMapper.toResJobDTO(matchedJob)).thenReturn(ResJobDTO.builder().build());

        ResJobRecommendationDTO result = recommendationService
                .getRecommendationsByProfile(ReqJobRecommendationDTO.builder().profileText("x").topK(5).build());

        assertThat(result.getRecommendations()).hasSize(1);
    }

    @Test
    void getRecommendationsByProfile_returnsEmpty_whenNoActiveJobsMatchInDatabase() {
        UUID jobId = UUID.randomUUID();
        Map<String, Object> response = Map.of(
                "data", Map.of("recommendations", List.of(Map.of("jobId", jobId.toString(), "score", 0.9, "rank", 1))));
        when(recommendationFeignClient.getRecommendationsByProfile(any())).thenReturn(response);
        when(jobRepository.findAllById(any())).thenReturn(List.of());
        when(jobRepository.findActiveJobsByIds(any())).thenReturn(List.of());

        ResJobRecommendationDTO result = recommendationService
                .getRecommendationsByProfile(ReqJobRecommendationDTO.builder().profileText("x").topK(5).build());

        assertThat(result.getRecommendations()).isEmpty();
    }

    @Test
    void getRecommendationsByProfile_returnsMappedJobs_whenActiveJobsFound() {
        UUID jobId = UUID.randomUUID();
        Map<String, Object> response = Map.of(
                "data", Map.of("recommendations", List.of(Map.of("jobId", jobId.toString(), "score", 0.9, "rank", 1))),
                "processingTime", "120ms");
        when(recommendationFeignClient.getRecommendationsByProfile(any())).thenReturn(response);
        Job job = new Job();
        job.setJobId(jobId);
        job.setStatus(org.workfitai.jobservice.model.enums.JobStatus.PUBLISHED);
        job.setExpiresAt(java.time.Instant.now().plusSeconds(3600));
        when(jobRepository.findAllById(any())).thenReturn(List.of(job));
        when(jobRepository.findActiveJobsByIds(any())).thenReturn(List.of(job));
        ResJobDTO jobDto = ResJobDTO.builder().build();
        when(jobMapper.toResJobDTO(job)).thenReturn(jobDto);

        ResJobRecommendationDTO result = recommendationService
                .getRecommendationsByProfile(ReqJobRecommendationDTO.builder().profileText("x").topK(5).build());

        assertThat(result.getRecommendations()).hasSize(1);
        assertThat(result.getRecommendations().get(0).getScore()).isEqualTo(0.9);
        assertThat(result.getRecommendations().get(0).getRank()).isEqualTo(1);
        assertThat(result.getProcessingTime()).isEqualTo("120ms");
        assertThat(result.getTotalResults()).isEqualTo(1);
    }

    @Test
    void getSimilarJobs_returnsEmpty_whenNoDataInResponse() {
        UUID jobId = UUID.randomUUID();
        when(recommendationFeignClient.getSimilarJobs(any())).thenReturn(Map.of());

        ResJobRecommendationDTO result = recommendationService.getSimilarJobs(jobId, 10, false);

        assertThat(result.getRecommendations()).isEmpty();
    }

    @Test
    void getSimilarJobs_returnsEmpty_whenResponseIsNull() {
        UUID jobId = UUID.randomUUID();
        when(recommendationFeignClient.getSimilarJobs(any())).thenReturn(null);

        ResJobRecommendationDTO result = recommendationService.getSimilarJobs(jobId, null, null);

        assertThat(result.getRecommendations()).isEmpty();
    }

    @Test
    void getSimilarJobs_returnsEmpty_whenNoRecommendationsKey() {
        UUID jobId = UUID.randomUUID();
        when(recommendationFeignClient.getSimilarJobs(any())).thenReturn(Map.of("data", Map.of()));

        ResJobRecommendationDTO result = recommendationService.getSimilarJobs(jobId, 10, false);

        assertThat(result.getRecommendations()).isEmpty();
    }

    @Test
    void getSimilarJobs_usesDefaultsAndProcessingTime_whenScoreRankMissingAndJobNotFound() {
        UUID jobId = UUID.randomUUID();
        UUID matchedJobId = UUID.randomUUID();
        UUID unmatchedJobId = UUID.randomUUID();
        Map<String, Object> response = Map.of(
                "data", Map.of("recommendations", List.of(
                        Map.of("jobId", matchedJobId.toString()),
                        Map.of("jobId", unmatchedJobId.toString()))),
                "processingTime", "85ms");
        when(recommendationFeignClient.getSimilarJobs(any())).thenReturn(response);
        Job matchedJob = new Job();
        matchedJob.setJobId(matchedJobId);
        when(jobRepository.findActiveJobsByIds(any())).thenReturn(List.of(matchedJob));
        when(jobMapper.toResJobDTO(matchedJob)).thenReturn(ResJobDTO.builder().build());

        ResJobRecommendationDTO result = recommendationService.getSimilarJobs(jobId, null, null);

        assertThat(result.getRecommendations()).hasSize(1);
        assertThat(result.getRecommendations().get(0).getScore()).isEqualTo(0.0);
        assertThat(result.getRecommendations().get(0).getRank()).isEqualTo(1);
        assertThat(result.getProcessingTime()).isEqualTo("85ms");
    }

    @Test
    void getSimilarJobs_returnsMappedJobs_whenActiveJobsFound() {
        UUID jobId = UUID.randomUUID();
        UUID similarJobId = UUID.randomUUID();
        Map<String, Object> response = Map.of(
                "data", Map.of("recommendations",
                        List.of(Map.of("jobId", similarJobId.toString(), "score", 0.75, "rank", 2))));
        when(recommendationFeignClient.getSimilarJobs(any())).thenReturn(response);
        Job similarJob = new Job();
        similarJob.setJobId(similarJobId);
        when(jobRepository.findActiveJobsByIds(any())).thenReturn(List.of(similarJob));
        when(jobMapper.toResJobDTO(similarJob)).thenReturn(ResJobDTO.builder().build());

        ResJobRecommendationDTO result = recommendationService.getSimilarJobs(jobId, 10, true);

        assertThat(result.getRecommendations()).hasSize(1);
        assertThat(result.getRecommendations().get(0).getScore()).isEqualTo(0.75);
    }
}
