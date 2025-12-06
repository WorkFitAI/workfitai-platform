package org.workfitai.cvservice.service.shared;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.workfitai.cvservice.dto.UserDto;

@Service
public class UserServiceClient {

    private final WebClient webClient;

    public UserServiceClient(WebClient webClient) {
        this.webClient = webClient;
    }

    public String getUserId(String username) {
        // Lấy token từ SecurityContext
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String token = jwt.getTokenValue();

        // Gọi UserService
        return webClient.get()
                .uri("http://user-service:9005/user/admins/{username}", username)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(UserDto.class)
                .map(UserDto::getUserId)
                .block();
    }
}