package org.workfitai.notificationservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.workfitai.notificationservice.model.NotificationDocument;
import org.workfitai.notificationservice.service.NotificationPersistenceService;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationPersistenceService persistenceService;

    @GetMapping("/notifications")
    public ResponseEntity<List<NotificationDocument>> listNotifications(
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return ResponseEntity.ok(persistenceService.latest(limit));
    }
}
