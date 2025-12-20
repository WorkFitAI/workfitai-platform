package org.workfitai.jobservice.service;

import org.workfitai.jobservice.model.dto.request.Recommendation.ReqJobRecommendationDTO;
import org.workfitai.jobservice.model.dto.response.Recommendation.ResJobRecommendationDTO;

import java.util.UUID;

public interface iRecommendationService {
    /**
     * Get job recommendations based on user's CV
     *
     * @param userId  The user ID to get CV from
     * @param topK    Number of recommendations to return
     * @param filters Optional filters for recommendations
     * @return Job recommendations with scores
     */
    ResJobRecommendationDTO getRecommendationsByCV(String userId, Integer topK,
            ReqJobRecommendationDTO.RecommendationFilters filters);

    /**
     * Get job recommendations based on profile text
     *
     * @param request Recommendation request with profile text and filters
     * @return Job recommendations with scores
     */
    ResJobRecommendationDTO getRecommendationsByProfile(ReqJobRecommendationDTO request);

    /**
     * Get similar jobs based on a job ID
     *
     * @param jobId              Reference job ID
     * @param topK               Number of similar jobs to return
     * @param excludeSameCompany Whether to exclude jobs from the same company
     * @return Similar job recommendations
     */
    ResJobRecommendationDTO getSimilarJobs(UUID jobId, Integer topK, Boolean excludeSameCompany);
}
