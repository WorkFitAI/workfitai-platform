package org.workfitai.notificationservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

@FeignClient(
    name = "auth-client",
    url = "${service.auth.url:http://auth-service:9005}",
    path = "/api/v1/keys")
public interface AuthFeignClient {

    @GetMapping("/public")
    Map<String, String> getPublicKey();
}
