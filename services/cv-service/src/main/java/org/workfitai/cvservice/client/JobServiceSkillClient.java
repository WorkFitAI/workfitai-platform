package org.workfitai.cvservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.workfitai.cvservice.client.dto.JobServiceApiResponseDTO;
import org.workfitai.cvservice.client.dto.SkillPageResultDTO;

@FeignClient(name = "job-service", url = "${service.job.url}")
public interface JobServiceSkillClient {

    @GetMapping("/public/skills")
    JobServiceApiResponseDTO<SkillPageResultDTO> getAllSkills(
            @RequestParam("page") int page,
            @RequestParam("size") int size);
}
