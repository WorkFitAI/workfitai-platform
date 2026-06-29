package org.workfitai.jobservice.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.workfitai.jobservice.model.dto.request.Recommendation.ReqJobRecommendationDTO;
import org.workfitai.jobservice.model.dto.response.RestResponse;
import org.workfitai.jobservice.model.dto.response.Recommendation.ResJobRecommendationDTO;
import org.workfitai.jobservice.service.iRecommendationService;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationControllerTest {

    @Mock
    private iRecommendationService recommendationService;
    @InjectMocks
    private RecommendationController controller;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList()));
    }

    @Test
    void getRecommendationsForMe_buildsFiltersAndDelegatesToService() {
        authenticateAs("alice");
        ResJobRecommendationDTO result = ResJobRecommendationDTO.builder().totalResults(2).build();
        when(recommendationService.getRecommendationsByCV(eq("alice"), eq(20), any())).thenReturn(result);

        RestResponse<ResJobRecommendationDTO> response = controller.getRecommendationsForMe(
                20, "Hanoi,Saigon", "SENIOR", "FULL_TIME", 1000, 2000);

        assertThat(response.getData()).isSameAs(result);
        ArgumentCaptor<ReqJobRecommendationDTO.RecommendationFilters> captor =
                ArgumentCaptor.forClass(ReqJobRecommendationDTO.RecommendationFilters.class);
        verify(recommendationService).getRecommendationsByCV(eq("alice"), eq(20), captor.capture());
        assertThat(captor.getValue().getLocations()).containsExactly("Hanoi", "Saigon");
        assertThat(captor.getValue().getMinSalary()).isEqualTo(1000);
        assertThat(captor.getValue().getMaxSalary()).isEqualTo(2000);
    }

    @Test
    void getRecommendationsForMe_buildsEmptyFilters_whenNoOptionalParamsProvided() {
        authenticateAs("alice");
        ResJobRecommendationDTO result = ResJobRecommendationDTO.builder().totalResults(0).build();
        when(recommendationService.getRecommendationsByCV(eq("alice"), eq(20), any())).thenReturn(result);

        controller.getRecommendationsForMe(20, null, null, null, null, null);

        ArgumentCaptor<ReqJobRecommendationDTO.RecommendationFilters> captor =
                ArgumentCaptor.forClass(ReqJobRecommendationDTO.RecommendationFilters.class);
        verify(recommendationService).getRecommendationsByCV(eq("alice"), eq(20), captor.capture());
        assertThat(captor.getValue().getLocations()).isNull();
        assertThat(captor.getValue().getMinSalary()).isNull();
    }

    @Test
    void getRecommendationsByProfile_defaultsTopKTo20_whenNotProvided() {
        ReqJobRecommendationDTO request = ReqJobRecommendationDTO.builder().profileText("x").build();
        ResJobRecommendationDTO result = ResJobRecommendationDTO.builder().totalResults(1).build();
        when(recommendationService.getRecommendationsByProfile(request)).thenReturn(result);

        RestResponse<ResJobRecommendationDTO> response = controller.getRecommendationsByProfile(request);

        assertThat(request.getTopK()).isEqualTo(20);
        assertThat(response.getData()).isSameAs(result);
    }

    @Test
    void getRecommendationsByProfile_preservesProvidedTopK() {
        ReqJobRecommendationDTO request = ReqJobRecommendationDTO.builder().profileText("x").topK(5).build();
        when(recommendationService.getRecommendationsByProfile(request))
                .thenReturn(ResJobRecommendationDTO.builder().build());

        controller.getRecommendationsByProfile(request);

        assertThat(request.getTopK()).isEqualTo(5);
    }

    @Test
    void getSimilarJobs_delegatesToService() {
        UUID jobId = UUID.randomUUID();
        ResJobRecommendationDTO result = ResJobRecommendationDTO.builder().totalResults(3).build();
        when(recommendationService.getSimilarJobs(jobId, 10, false)).thenReturn(result);

        RestResponse<ResJobRecommendationDTO> response = controller.getSimilarJobs(jobId, 10, false);

        assertThat(response.getData()).isSameAs(result);
    }
}
