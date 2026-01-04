package org.workfitai.jobservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

@FeignClient(name = "auth-service", url = "${service.auth.url}")
public interface AuthFeignClient {

  @GetMapping("/api/v1/keys/public")
  Map<String, String> getPublicKey();
}
