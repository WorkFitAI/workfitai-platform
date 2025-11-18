package org.workfitai.apigateway.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AuthWebClient {

  private final WebClient.Builder webClientBuilder;
  @Value("${service.auth.url}")
  private String authServiceUrl;

  public Map<String, String> getPublicKey() {
    return webClientBuilder.build()
        .get()
        .uri(authServiceUrl + "/api/v1/keys/public")
        .retrieve()
        .bodyToMono(Map.class)
        .block(); // lấy 1 lần thôi, chấp nhận blocking ở startup
  }
}
