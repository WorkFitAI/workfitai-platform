package org.workfitai.jobservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.workfitai.jobservice.config.FeignConfig;

import java.util.List;
import java.util.Map;

@FeignClient(name = "cv-service", url = "${service.cv.url:http://localhost:9083}", configuration = FeignConfig.class)
public interface CVFeignClient {

    // Internal service-to-service endpoint — no JWT required, Docker-network only.
    @PostMapping("/internal/cvs/batch")
    List<Map<String, Object>> getCvDataBatch(@RequestBody List<String> usernames);
}
