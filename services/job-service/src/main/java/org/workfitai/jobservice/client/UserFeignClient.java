package org.workfitai.jobservice.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.workfitai.jobservice.model.dto.kafka.UserInfoServeForJobResponse;

@FeignClient(name = "user")
public interface UserFeignClient {

  @GetMapping("/api/v1/internal/users")
  ResponseEntity<List<UserInfoServeForJobResponse>> getUsersByUsernames(
      @RequestParam("usernames") List<String> usernames);
}