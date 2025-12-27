package org.workfitai.userservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.workfitai.userservice.dto.response.AvatarResponse;
import org.workfitai.userservice.service.AvatarService;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
public class AvatarController {

    private final AvatarService avatarService;

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('candidate:update') or hasAuthority('hr:update') or hasAuthority('admin:update')")
    public ResponseEntity<AvatarResponse> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        String username = authentication.getName();
        log.info("Avatar upload request from user: {}", username);

        AvatarResponse response = avatarService.uploadAvatar(username, file);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/avatar")
    @PreAuthorize("hasAuthority('candidate:update') or hasAuthority('hr:update') or hasAuthority('admin:update')")
    public ResponseEntity<Map<String, String>> deleteAvatar(Authentication authentication) {
        String username = authentication.getName();
        log.info("Avatar deletion request from user: {}", username);

        avatarService.deleteAvatar(username);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Avatar deleted successfully");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/avatar")
    public ResponseEntity<AvatarResponse> getAvatar(Authentication authentication) {
        String username = authentication.getName();

        AvatarResponse response = avatarService.getAvatar(username);

        return ResponseEntity.ok(response);
    }
}
