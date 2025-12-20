package org.workfitai.jobservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.workfitai.jobservice.config.FeignConfig;

import java.util.Map;

@FeignClient(name = "cv-service", url = "${service.cv.url:http://localhost:9083}", configuration = FeignConfig.class)
public interface CVFeignClient {

    @GetMapping("/candidate/{username}")
    Map<String, Object> getCVsByUsername(
            @PathVariable("username") String username,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size);
}
